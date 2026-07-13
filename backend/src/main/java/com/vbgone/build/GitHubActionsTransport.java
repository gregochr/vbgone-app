package com.vbgone.build;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The production {@link WindowsRunnerTransport}: dispatches a characterisation project to the
 * {@code assure-windows-runner} GitHub Actions workflow on a {@code windows-latest} runner, then
 * fetches the {@code results.trx} it produces.
 *
 * <p>The dispatch reuses the same Git Data API dance {@link com.vbgone.service.GitHubService} uses to
 * raise PRs (blobs → tree → commit → ref): it pushes a short-lived {@code runner/<jobId>} branch off
 * {@code main} carrying the generated {@code runner-workspace/} files, which triggers the workflow's
 * {@code on: push}. It then polls {@code /actions/runs} for the run, downloads the artifact zip, and
 * extracts {@code results.trx} — deleting the branch when done.
 *
 * <p><b>Not yet exercised against a live runner.</b> The pieces are compile-verified and the logic is
 * unit-tested via the {@link WindowsRunnerTransport} seam, but an end-to-end run needs a token with
 * {@code contents:write} + {@code actions:read} and the workflow present on {@code main}.
 */
@Component
public class GitHubActionsTransport implements WindowsRunnerTransport {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http;
    private final ObjectMapper mapper;
    private final String token;
    private final String apiBaseUrl;
    private final String owner;
    private final String repo;
    private final String baseBranch;
    private final String branchPrefix;
    private final String artifactName;
    private final long pollIntervalMs;
    private final long timeoutMs;

    @Autowired
    public GitHubActionsTransport(
            ObjectMapper mapper,
            @Value("${github.token:not-set}") String token,
            @Value("${vbgone.assure.windows-runner.repo-owner:gregochr}") String owner,
            @Value("${vbgone.assure.windows-runner.repo-name:vbgone-app}") String repo,
            @Value("${vbgone.assure.windows-runner.base-branch:main}") String baseBranch,
            @Value("${vbgone.assure.windows-runner.branch-prefix:runner/}") String branchPrefix,
            @Value("${vbgone.assure.windows-runner.artifact-name:characterisation-results}") String artifactName,
            @Value("${vbgone.assure.windows-runner.poll-interval-ms:15000}") long pollIntervalMs,
            @Value("${vbgone.assure.windows-runner.timeout-ms:600000}") long timeoutMs) {
        this(new OkHttpClient(), mapper, token, "https://api.github.com", owner, repo,
                baseBranch, branchPrefix, artifactName, pollIntervalMs, timeoutMs);
    }

    GitHubActionsTransport(OkHttpClient http, ObjectMapper mapper, String token, String apiBaseUrl,
                           String owner, String repo, String baseBranch, String branchPrefix,
                           String artifactName, long pollIntervalMs, long timeoutMs) {
        this.http = http;
        this.mapper = mapper;
        this.token = token;
        this.apiBaseUrl = apiBaseUrl;
        this.owner = owner;
        this.repo = repo;
        this.baseBranch = baseBranch;
        this.branchPrefix = branchPrefix;
        this.artifactName = artifactName;
        this.pollIntervalMs = pollIntervalMs;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public String characterise(String jobId, Map<String, String> files)
            throws IOException, InterruptedException {
        String branch = branchPrefix + jobId;
        pushBranch(branch, files);
        try {
            long runId = waitForRun(branch);
            return extractTrx(downloadArtifactZip(runId));
        } finally {
            deleteBranchQuietly(branch);
        }
    }

    // ── dispatch: push runner/<jobId> off the base branch via the Git Data API ──

    private void pushBranch(String branch, Map<String, String> files) throws IOException {
        String baseSha = get("/repos/%s/%s/git/ref/heads/%s".formatted(owner, repo, baseBranch))
                .get("object").get("sha").asText();
        String baseTreeSha = get("/repos/%s/%s/git/commits/%s".formatted(owner, repo, baseSha))
                .get("tree").get("sha").asText();

        List<Map<String, String>> tree = new ArrayList<>();
        for (Map.Entry<String, String> f : files.entrySet()) {
            String blobSha = post("/repos/%s/%s/git/blobs".formatted(owner, repo),
                    Map.of("content", f.getValue(), "encoding", "utf-8")).get("sha").asText();
            tree.add(Map.of("path", f.getKey(), "mode", "100644", "type", "blob", "sha", blobSha));
        }
        String treeSha = post("/repos/%s/%s/git/trees".formatted(owner, repo),
                Map.of("base_tree", baseTreeSha, "tree", tree)).get("sha").asText();
        String commitSha = post("/repos/%s/%s/git/commits".formatted(owner, repo),
                Map.of("message", "assure: characterise " + branch, "tree", treeSha,
                        "parents", List.of(baseSha))).get("sha").asText();
        post("/repos/%s/%s/git/refs".formatted(owner, repo),
                Map.of("ref", "refs/heads/" + branch, "sha", commitSha));
    }

    // ── poll: wait for the assure workflow run on this branch to complete ──

    private long waitForRun(String branch) throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String path = "/repos/%s/%s/actions/runs?branch=%s&event=push&per_page=10"
                .formatted(owner, repo, branch);
        while (System.currentTimeMillis() < deadline) {
            for (JsonNode run : get(path).get("workflow_runs")) {
                if (!"assure-windows-runner".equals(run.path("name").asText())) continue;
                if ("completed".equals(run.path("status").asText())) {
                    return run.get("id").asLong();
                }
            }
            Thread.sleep(pollIntervalMs);
        }
        throw new IOException("Windows runner did not complete within " + timeoutMs + "ms for " + branch);
    }

    // ── download: fetch the artifact zip and pull results.trx out of it ──

    private byte[] downloadArtifactZip(long runId) throws IOException {
        JsonNode artifacts = get("/repos/%s/%s/actions/runs/%d/artifacts".formatted(owner, repo, runId));
        long artifactId = -1;
        for (JsonNode a : artifacts.get("artifacts")) {
            if (artifactName.equals(a.path("name").asText())) {
                artifactId = a.get("id").asLong();
                break;
            }
        }
        if (artifactId < 0) {
            throw new IOException("No '" + artifactName + "' artifact on run " + runId
                    + " (the VB likely failed to compile, so no test results were produced)");
        }
        // GitHub 302-redirects the /zip endpoint to a signed blob URL that must be fetched WITHOUT the
        // GitHub Authorization header — so resolve the redirect ourselves and fetch the location clean.
        String zipUrl = "%s/repos/%s/%s/actions/artifacts/%d/zip".formatted(apiBaseUrl, owner, repo, artifactId);
        OkHttpClient noRedirect = http.newBuilder().followRedirects(false).followSslRedirects(false).build();
        Request signed = new Request.Builder().url(zipUrl)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json").build();
        try (Response redirect = noRedirect.newCall(signed).execute()) {
            String location = redirect.header("Location");
            if (location == null) {
                throw new IOException("Artifact download did not redirect (HTTP " + redirect.code() + ")");
            }
            try (Response blob = http.newCall(new Request.Builder().url(location).build()).execute()) {
                if (!blob.isSuccessful() || blob.body() == null) {
                    throw new IOException("Artifact blob download failed: HTTP " + blob.code());
                }
                return blob.body().bytes();
            }
        }
    }

    private String extractTrx(byte[] zipBytes) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().endsWith(".trx")) {
                    return new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }
        throw new IOException("Artifact contained no .trx");
    }

    private void deleteBranchQuietly(String branch) {
        try {
            Request request = authed("/repos/%s/%s/git/refs/heads/%s".formatted(owner, repo, branch))
                    .delete().build();
            try (Response r = http.newCall(request).execute()) {
                // best-effort cleanup; a leftover branch is harmless and reaped by retention
                r.body();
            }
        } catch (IOException ignored) {
            // swallow — cleanup failure must not mask the characterisation result
        }
    }

    // ── HTTP helpers (mirroring GitHubService) ──

    private Request.Builder authed(String path) {
        return new Request.Builder().url(apiBaseUrl + path)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json");
    }

    private JsonNode get(String path) throws IOException {
        return execute(authed(path).build());
    }

    private JsonNode post(String path, Object body) throws IOException {
        String json = mapper.writeValueAsString(body);
        return execute(authed(path).post(RequestBody.create(json, JSON)).build());
    }

    private JsonNode execute(Request request) throws IOException {
        try (Response response = http.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("GitHub API error %d: %s".formatted(response.code(), body));
            }
            return mapper.readTree(body);
        }
    }
}
