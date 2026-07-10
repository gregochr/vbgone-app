package com.vbgone.service;

import com.vbgone.ai.ProviderUnavailableException;
import com.vbgone.model.IngestRepoRequest;
import com.vbgone.model.VbSourceFile;
import com.vbgone.model.ZipManifest;
import com.vbgone.session.SessionStore;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepoIngestServiceTest {

    private MockWebServer server;
    private SessionStore sessionStore;
    private RepoIngestService service;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        sessionStore = new SessionStore();
        String base = server.url("/").toString().replaceAll("/$", "");
        service = new RepoIngestService(
                new ZipExtractorService(sessionStore), sessionStore, new OkHttpClient(), base);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    // ── URL parsing / validation ──

    @Test
    void parseSlug_acceptsShorthandFullUrlWwwGitAndTrailingPath() {
        assertThat(service.parseSlug("octocat/hello")).isEqualTo("octocat/hello");
        assertThat(service.parseSlug("https://github.com/org/legacy-app")).isEqualTo("org/legacy-app");
        assertThat(service.parseSlug("https://www.github.com/org/legacy-app.git")).isEqualTo("org/legacy-app");
        assertThat(service.parseSlug("git@github.com:org/legacy-app.git")).isEqualTo("org/legacy-app");
        assertThat(service.parseSlug("https://github.com/org/legacy-app/tree/main/src")).isEqualTo("org/legacy-app");
        assertThat(service.parseSlug("https://github.com/org/legacy-app#readme")).isEqualTo("org/legacy-app");
    }

    @Test
    void parseSlug_empty_throwsWithEmptyMessage() {
        assertThatThrownBy(() -> service.parseSlug("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(RepoIngestService.EMPTY_URL);
    }

    @Test
    void parseSlug_nonGithubHost_throwsWithNonGithubMessage() {
        assertThatThrownBy(() -> service.parseSlug("https://gitlab.com/org/repo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(RepoIngestService.NON_GITHUB);
    }

    @Test
    void parseSlug_githubHostNoRepoPath_throwsWithMalformedMessage() {
        assertThatThrownBy(() -> service.parseSlug("https://github.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(RepoIngestService.MALFORMED);
    }

    // ── Ingest flow ──

    @Test
    void ingest_success_stripsZipballPrefix_excludesBuildOutput_skipsNonVb() throws Exception {
        byte[] zip = zip(
                "org-legacy-app-abc123/Services/OrderService.vb", "Public Class OrderService\nEnd Class",
                "org-legacy-app-abc123/bin/Debug/Generated.vb", "Public Class Generated\nEnd Class",
                "org-legacy-app-abc123/obj/Temp.vb", "Public Class Temp\nEnd Class",
                "org-legacy-app-abc123/README.md", "# not source");
        server.enqueue(new MockResponse().setResponseCode(200).setBody(new Buffer().write(zip)));

        ZipManifest manifest = service.ingest(new IngestRepoRequest("https://github.com/org/legacy-app"));

        assertThat(manifest.sessionId()).isNotBlank();
        assertThat(sessionStore.get(manifest.sessionId())).isPresent();
        // Only the real source survives: bin/ and obj/ are build output, README is not .vb,
        // and the "<owner>-<repo>-<sha>/" prefix is stripped from the path.
        assertThat(manifest.files()).extracting(VbSourceFile::relativePath)
                .containsExactly("Services/OrderService.vb");
        assertThat(manifest.files().get(0).filename()).isEqualTo("OrderService.vb");
    }

    @Test
    void ingest_hitsTheZipballEndpointForTheParsedSlug() throws Exception {
        byte[] zip = zip("acme-app-sha/Calc.vb", "Public Class Calc\nEnd Class");
        server.enqueue(new MockResponse().setResponseCode(200).setBody(new Buffer().write(zip)));

        service.ingest(new IngestRepoRequest("https://github.com/acme/app"));

        assertThat(server.takeRequest().getPath()).isEqualTo("/repos/acme/app/zipball");
    }

    @Test
    void ingest_sendsNoAuthorizationHeader_soOnlyPublicReposResolve() throws Exception {
        byte[] zip = zip("acme-app-sha/Calc.vb", "Public Class Calc\nEnd Class");
        server.enqueue(new MockResponse().setResponseCode(200).setBody(new Buffer().write(zip)));

        service.ingest(new IngestRepoRequest("https://github.com/acme/app"));

        // Public-only: the server must never present a credential that could read a private repo.
        assertThat(server.takeRequest().getHeader("Authorization")).isNull();
    }

    @Test
    void ingest_404_reportsPrivateOrMissing() {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("Not Found"));

        assertThatThrownBy(() -> service.ingest(new IngestRepoRequest("org/ghost")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("org/ghost")
                .hasMessageContaining("private or doesn");
    }

    @Test
    void ingest_noVbAfterFiltering_reportsNoSource() throws Exception {
        byte[] zip = zip(
                "org-docs-sha/README.md", "# docs",
                "org-docs-sha/bin/Only.vb", "Public Class Only\nEnd Class"); // excluded → nothing left
        server.enqueue(new MockResponse().setResponseCode(200).setBody(new Buffer().write(zip)));

        assertThatThrownBy(() -> service.ingest(new IngestRepoRequest("org/docs")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no .vb source files found");
    }

    @Test
    void ingest_serverError_reportsUnavailable() {
        server.enqueue(new MockResponse().setResponseCode(503));

        assertThatThrownBy(() -> service.ingest(new IngestRepoRequest("org/repo")))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    @Test
    void ingest_moreThanMaxRepoFiles_throwsFileCountCap() throws Exception {
        String[] pairs = new String[(RepoIngestService.MAX_REPO_FILES + 1) * 2];
        for (int i = 0; i <= RepoIngestService.MAX_REPO_FILES; i++) {
            pairs[2 * i] = "repo-sha/Class" + i + ".vb";
            pairs[2 * i + 1] = "Public Class C" + i + "\nEnd Class";
        }
        server.enqueue(new MockResponse().setResponseCode(200).setBody(new Buffer().write(zip(pairs))));

        assertThatThrownBy(() -> service.ingest(new IngestRepoRequest("org/big")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than " + RepoIngestService.MAX_REPO_FILES + " .vb files");
    }

    @Test
    void ingest_corruptArchive_reportsProviderUnavailable() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 400; i++) sb.append("Public Class Form").append(i).append(" : End Class\n");
        byte[] full = zip("repo-sha/Form.vb", sb.toString());
        // Truncate mid deflate-stream so reading the entry throws IOException, which ingest maps to 422.
        byte[] truncated = java.util.Arrays.copyOf(full, 60);
        server.enqueue(new MockResponse().setResponseCode(200).setBody(new Buffer().write(truncated)));

        assertThatThrownBy(() -> service.ingest(new IngestRepoRequest("org/corrupt")))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("may be corrupt");
    }

    // ── helpers ──

    private static byte[] zip(String... pathThenContent) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i < pathThenContent.length; i += 2) {
                zos.putNextEntry(new ZipEntry(pathThenContent[i]));
                zos.write(pathThenContent[i + 1].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
