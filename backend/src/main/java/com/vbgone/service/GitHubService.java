package com.vbgone.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vbgone.build.JavaRuntime;
import com.vbgone.lang.LanguageConventions;
import com.vbgone.lang.LanguageConventionsRegistry;
import com.vbgone.model.*;
import com.vbgone.session.SessionStore;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class GitHubService {

    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");
    private static final String JAVA_PACKAGE_DIR = "com/vbgone/generated";

    private final SessionStore sessionStore;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String githubToken;
    private final String apiBaseUrl;
    private final LanguageConventionsRegistry conventionsRegistry;

    @Autowired
    public GitHubService(SessionStore sessionStore,
                         ObjectMapper objectMapper,
                         LanguageConventionsRegistry conventionsRegistry,
                         @Value("${github.token:not-set}") String githubToken) {
        this(sessionStore, new OkHttpClient(), objectMapper, conventionsRegistry,
                githubToken, "https://api.github.com");
    }

    GitHubService(SessionStore sessionStore, OkHttpClient httpClient,
                  ObjectMapper objectMapper, LanguageConventionsRegistry conventionsRegistry,
                  String githubToken, String apiBaseUrl) {
        this.sessionStore = sessionStore;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.conventionsRegistry = conventionsRegistry;
        this.githubToken = githubToken;
        this.apiBaseUrl = apiBaseUrl;
    }

    public PullRequestResult raisePR(String sessionId, String repoOwner, String repoName, String branchName) {
        MigrationSession session = sessionStore.getOrThrow(sessionId);

        InterfaceResult iface = session.getInterfaceResult();
        if (iface == null) {
            throw new IllegalStateException("Interface must be generated before raising PR");
        }
        TestsResult tests = session.getTestsResult();
        if (tests == null) {
            throw new IllegalStateException("Tests must be generated before raising PR");
        }
        ImplementResult impl = session.getImplementResult();
        if (impl == null) {
            throw new IllegalStateException("Implementation must exist before raising PR");
        }

        String className = iface.className();
        String targetLanguage = session.getTargetLanguage();
        boolean java = "java".equalsIgnoreCase(targetLanguage);
        LanguageConventions conv = conventionsRegistry.forLanguage(targetLanguage);

        List<String> filePaths;
        List<String> fileContents;
        if (java) {
            String mainDir = "src/main/java/" + JAVA_PACKAGE_DIR + "/";
            String testDir = "src/test/java/" + JAVA_PACKAGE_DIR + "/";
            filePaths = List.of(
                    mainDir + iface.interfaceName() + ".java",
                    mainDir + iface.implName() + ".java",
                    testDir + tests.testClassName() + ".java",
                    "pom.xml"
            );
            fileContents = List.of(
                    iface.code(),
                    impl.code(),
                    tests.code(),
                    JavaRuntime.POM
            );
        } else {
            filePaths = List.of(
                    className + "/" + conv.interfaceName(className) + ".cs",
                    className + "/" + conv.implName(className) + ".cs",
                    className + ".Tests/" + tests.testClassName() + ".cs"
            );
            fileContents = List.of(
                    iface.code(),
                    impl.code(),
                    tests.code()
            );
        }

        try {
            String mainSha = getRefSha(repoOwner, repoName, "heads/main");

            // A class can be migrated more than once (a re-run, or C# then Java),
            // so the requested branch may already exist. Create it, falling back to
            // <branch>-2, -3, … on a "Reference already exists" collision.
            branchName = createBranchOnFreeName(repoOwner, repoName, branchName, mainSha);

            String baseTreeSha = getCommitTreeSha(repoOwner, repoName, mainSha);

            List<String> blobShas = new ArrayList<>();
            for (String content : fileContents) {
                blobShas.add(createBlob(repoOwner, repoName, content));
            }

            String treeSha = createTree(repoOwner, repoName, baseTreeSha, filePaths, blobShas);

            String targetName = java ? "Java" : "C#";
            String commitMessage = "Migrate " + className + " from VB.NET to " + targetName;
            String commitSha = createCommit(repoOwner, repoName, commitMessage, treeSha, mainSha);

            updateRef(repoOwner, repoName, "heads/" + branchName, commitSha);

            String prUrl = createPullRequest(repoOwner, repoName, branchName, className, filePaths, targetName);

            PullRequestResult result = new PullRequestResult(sessionId, prUrl, branchName, filePaths);
            session.setPrResult(result);
            return result;

        } catch (IOException e) {
            throw new RuntimeException("GitHub API call failed: " + e.getMessage(), e);
        }
    }

    // ── Git Data API calls ──

    private String getRefSha(String owner, String repo, String ref) throws IOException {
        JsonNode response = get("/repos/%s/%s/git/ref/%s".formatted(owner, repo, ref));
        return response.get("object").get("sha").asText();
    }

    private void createRef(String owner, String repo, String ref, String sha) throws IOException {
        post("/repos/%s/%s/git/refs".formatted(owner, repo),
                Map.of("ref", ref, "sha", sha));
    }

    /**
     * Creates the requested branch, or on a "Reference already exists" collision
     * the first free {@code <branch>-2}, {@code <branch>-3}, … Returns the branch
     * name that was actually created. The happy path is a single createRef call.
     */
    String createBranchOnFreeName(String owner, String repo, String desired, String sha) throws IOException {
        try {
            createRef(owner, repo, "refs/heads/" + desired, sha);
            return desired;
        } catch (IOException e) {
            if (!isAlreadyExists(e)) {
                throw e;
            }
        }
        for (int i = 2; i <= 50; i++) {
            String candidate = desired + "-" + i;
            try {
                createRef(owner, repo, "refs/heads/" + candidate, sha);
                return candidate;
            } catch (IOException e) {
                if (!isAlreadyExists(e)) {
                    throw e;
                }
            }
        }
        throw new IOException("Could not find a free branch name based on '" + desired + "'");
    }

    private boolean isAlreadyExists(IOException e) {
        return e.getMessage() != null && e.getMessage().contains("Reference already exists");
    }

    private String getCommitTreeSha(String owner, String repo, String sha) throws IOException {
        JsonNode response = get("/repos/%s/%s/git/commits/%s".formatted(owner, repo, sha));
        return response.get("tree").get("sha").asText();
    }

    private String createBlob(String owner, String repo, String content) throws IOException {
        JsonNode response = post("/repos/%s/%s/git/blobs".formatted(owner, repo),
                Map.of("content", content, "encoding", "utf-8"));
        return response.get("sha").asText();
    }

    private String createTree(String owner, String repo, String baseTreeSha,
                              List<String> paths, List<String> blobShas) throws IOException {
        List<Map<String, String>> treeEntries = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            treeEntries.add(Map.of(
                    "path", paths.get(i),
                    "mode", "100644",
                    "type", "blob",
                    "sha", blobShas.get(i)
            ));
        }
        JsonNode response = post("/repos/%s/%s/git/trees".formatted(owner, repo),
                Map.of("base_tree", baseTreeSha, "tree", treeEntries));
        return response.get("sha").asText();
    }

    private String createCommit(String owner, String repo, String message,
                                String treeSha, String parentSha) throws IOException {
        JsonNode response = post("/repos/%s/%s/git/commits".formatted(owner, repo),
                Map.of("message", message, "tree", treeSha, "parents", List.of(parentSha)));
        return response.get("sha").asText();
    }

    private void updateRef(String owner, String repo, String ref, String sha) throws IOException {
        patch("/repos/%s/%s/git/refs/%s".formatted(owner, repo, ref),
                Map.of("sha", sha));
    }

    private String createPullRequest(String owner, String repo, String branch,
                                     String className, List<String> filePaths,
                                     String targetName) throws IOException {
        String title = "Migrate " + className + " from VB.NET to " + targetName;
        String body = buildPrBody(className, filePaths, targetName);

        JsonNode response = post("/repos/%s/%s/pulls".formatted(owner, repo),
                Map.of("title", title, "body", body, "head", branch, "base", "main"));
        return response.get("html_url").asText();
    }

    /** Back-compat: defaults to the C# wording. */
    String buildPrBody(String className, List<String> filePaths) {
        return buildPrBody(className, filePaths, "C#");
    }

    String buildPrBody(String className, List<String> filePaths, String targetName) {
        StringBuilder sb = new StringBuilder();
        sb.append("## VB.NET to ").append(targetName).append(" Migration: ").append(className).append("\n\n");
        sb.append("This PR migrates **").append(className).append("** from VB.NET to modern ")
                .append(targetName).append(".\n\n");
        sb.append("### Files\n");
        for (String path : filePaths) {
            sb.append("- `").append(path).append("`\n");
        }
        sb.append("\n### Next steps\n");
        sb.append("The CI pipeline will run automatically — check the status checks below.\n\n");
        sb.append("---\n");
        sb.append("Generated by [VBGone](https://github.com/chrisgregory/vbgone-app)\n");
        return sb.toString();
    }

    // ── HTTP helpers ──

    private JsonNode get(String path) throws IOException {
        Request request = new Request.Builder()
                .url(apiBaseUrl + path)
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github+json")
                .build();
        return execute(request);
    }

    private JsonNode post(String path, Object body) throws IOException {
        String json = objectMapper.writeValueAsString(body);
        Request request = new Request.Builder()
                .url(apiBaseUrl + path)
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github+json")
                .post(RequestBody.create(json, JSON_MEDIA))
                .build();
        return execute(request);
    }

    private void patch(String path, Object body) throws IOException {
        String json = objectMapper.writeValueAsString(body);
        Request request = new Request.Builder()
                .url(apiBaseUrl + path)
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github+json")
                .patch(RequestBody.create(json, JSON_MEDIA))
                .build();
        execute(request);
    }

    private JsonNode execute(Request request) throws IOException {
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("GitHub API error %d: %s".formatted(response.code(), responseBody));
            }
            return objectMapper.readTree(responseBody);
        }
    }
}
