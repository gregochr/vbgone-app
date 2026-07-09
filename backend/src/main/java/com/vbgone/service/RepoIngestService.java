package com.vbgone.service;

import com.vbgone.ai.ProviderUnavailableException;
import com.vbgone.model.IngestRepoRequest;
import com.vbgone.model.VbSourceFile;
import com.vbgone.model.ZipManifest;
import com.vbgone.session.SessionStore;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ingests a <strong>public</strong> GitHub repository as a portfolio estate. It parses/validates
 * the repo URL, downloads the repo's zipball server-side, keeps only {@code .vb} sources (skipping
 * build output and non-source files), and returns a {@link ZipManifest} that feeds the same static
 * readiness pass as an uploaded {@code .zip}.
 *
 * <p>There is deliberately <strong>no user auth</strong> — only public repos are supported. The
 * server may present its own token to raise GitHub's rate limit, but a private/non-existent repo
 * (which returns 404 unauthenticated) is reported as such rather than prompting for credentials.
 */
@Service
public class RepoIngestService {

    static final long MAX_ARCHIVE_BYTES = 25L * 1024 * 1024; // 25 MB compressed
    static final int MAX_REPO_FILES = 500; // a legacy estate is larger than a single uploaded zip

    // Path segments that are build output / VCS / vendored deps — never business-logic source.
    private static final Set<String> EXCLUDED_DIRS =
            Set.of("bin", "obj", ".git", ".vs", "node_modules", "packages");

    // ── User-facing messages (must match the design copy verbatim, curly ' and em dash —) ──
    static final String EMPTY_URL = "Paste a GitHub repository URL to analyse.";
    static final String NON_GITHUB = "Only github.com repositories are supported right now.";
    static final String MALFORMED = "That doesn’t look like a repo URL. Try github.com/org/repo.";

    // Accepts, after stripping a leading https:// or git@: full github.com URLs (with/without .git,
    // trailing path, #/? fragments) and www.github.com. `org/repo` shorthand is handled separately.
    private static final Pattern SHORTHAND = Pattern.compile("^[\\w.-]+/[\\w.-]+$");
    private static final Pattern GITHUB_HOST = Pattern.compile("^(www\\.)?github\\.com$");
    private static final Pattern REPO_PATH = Pattern.compile(
            "github\\.com[/:]([\\w.-]+)/([\\w.-]+?)(?:\\.git)?(?:[/#?].*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOT_GIT = Pattern.compile("\\.git$", Pattern.CASE_INSENSITIVE);

    private final ZipExtractorService zipExtractorService;
    private final SessionStore sessionStore;
    private final OkHttpClient httpClient;
    private final String apiBaseUrl;

    @Autowired
    public RepoIngestService(ZipExtractorService zipExtractorService, SessionStore sessionStore) {
        this(zipExtractorService, sessionStore, new OkHttpClient(), "https://api.github.com");
    }

    RepoIngestService(ZipExtractorService zipExtractorService, SessionStore sessionStore,
                      OkHttpClient httpClient, String apiBaseUrl) {
        this.zipExtractorService = zipExtractorService;
        this.sessionStore = sessionStore;
        this.httpClient = httpClient;
        this.apiBaseUrl = apiBaseUrl;
    }

    /**
     * Clone-and-filter a public repo into a manifest of {@code .vb} sources. Throws
     * {@link IllegalArgumentException} (→ 400) for the five expected failures — empty/non-github/
     * malformed URL, private-or-missing repo, and no {@code .vb} source — each with a specific
     * message; {@link ProviderUnavailableException} (→ 422) for a transient GitHub failure.
     */
    public ZipManifest ingest(IngestRepoRequest request) {
        String slug = parseSlug(request == null ? null : request.url());

        byte[] archive = downloadZipball(slug);

        List<VbSourceFile> vbFiles;
        try {
            vbFiles = zipExtractorService.readVbEntries(
                    new ByteArrayInputStream(archive), this::isSource, MAX_REPO_FILES, "Repository");
        } catch (IOException e) {
            throw new ProviderUnavailableException(
                    "Couldn’t read the archive for " + slug + " — the download may be corrupt.", e);
        }

        // GitHub zipballs nest everything under a top-level "<owner>-<repo>-<sha>/" folder — drop it
        // so the report shows tidy in-repo paths.
        List<VbSourceFile> sources = new ArrayList<>(vbFiles.size());
        for (VbSourceFile f : vbFiles) {
            sources.add(new VbSourceFile(stripTopDir(f.relativePath()), f.filename(), f.content()));
        }

        if (sources.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cloned " + slug + " — no .vb source files found in this repository.");
        }

        String sessionId = sessionStore.create().getSessionId();
        return new ZipManifest(sessionId, sources, sources.size());
    }

    /** Port of the design's {@code parseRepo}: normalises the input to an {@code owner/repo} slug. */
    String parseSlug(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) {
            throw new IllegalArgumentException(EMPTY_URL);
        }

        String noScheme = v.replaceFirst("(?i)^https?://", "").replaceFirst("(?i)^git@", "");

        // A bare "org/repo" (no host) is accepted immediately.
        if (SHORTHAND.matcher(noScheme).matches()) {
            String[] parts = noScheme.split("/", 2);
            return parts[0] + "/" + DOT_GIT.matcher(parts[1]).replaceFirst("");
        }

        String host = noScheme.split("[/:]", 2)[0].toLowerCase();
        if (!GITHUB_HOST.matcher(host).matches()) {
            throw new IllegalArgumentException(NON_GITHUB);
        }

        Matcher m = REPO_PATH.matcher(noScheme);
        if (!m.find()) {
            throw new IllegalArgumentException(MALFORMED);
        }
        return m.group(1) + "/" + DOT_GIT.matcher(m.group(2)).replaceFirst("");
    }

    private byte[] downloadZipball(String slug) {
        // Public-only, no auth: we deliberately send NO Authorization header, so GitHub only serves
        // public repositories. A private or non-existent repo returns 404 and is reported as such —
        // the server never presents a credential that could read a private repo into the tenant.
        Request request = new Request.Builder()
                .url(apiBaseUrl + "/repos/" + slug + "/zipball")
                .header("Accept", "application/vnd.github+json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 404) {
                throw new IllegalArgumentException("Can’t reach " + slug
                        + " — it’s private or doesn’t exist. VBGone only reads public repositories (no sign-in).");
            }
            if (!response.isSuccessful()) {
                throw new ProviderUnavailableException("Couldn’t reach GitHub to read " + slug
                        + " (HTTP " + response.code() + "). Try again shortly.");
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new ProviderUnavailableException("GitHub returned an empty response for " + slug + ".");
            }
            return readBounded(body.byteStream());
        } catch (IOException e) {
            throw new ProviderUnavailableException(
                    "Couldn’t reach GitHub to read " + slug + ". Try again shortly.", e);
        }
    }

    /** Read a stream into memory, aborting if it exceeds {@link #MAX_ARCHIVE_BYTES}. */
    private byte[] readBounded(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > MAX_ARCHIVE_BYTES) {
                throw new IllegalArgumentException(
                        "Repository archive exceeds the " + (MAX_ARCHIVE_BYTES / 1024 / 1024) + " MB limit.");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** True unless the entry sits under a build-output / VCS / vendored-deps directory. */
    private boolean isSource(String entryName) {
        for (String segment : entryName.split("/")) {
            if (EXCLUDED_DIRS.contains(segment.toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    private static String stripTopDir(String relativePath) {
        int slash = relativePath.indexOf('/');
        if (slash < 0 || slash == relativePath.length() - 1) {
            return relativePath;
        }
        return relativePath.substring(slash + 1);
    }
}
