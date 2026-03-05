package com.vbgone.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vbgone.model.*;
import com.vbgone.session.SessionStore;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubServiceTest {

    private MockWebServer mockWebServer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private SessionStore sessionStore;

    private GitHubService service;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        String baseUrl = mockWebServer.url("/").toString().replaceAll("/$", "");
        service = new GitHubService(sessionStore, new OkHttpClient(),
                objectMapper, "ghp_test123", baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    private MigrationSession fullSession(String sessionId) {
        MigrationSession session = new MigrationSession(sessionId);
        session.setVbContent("Public Class Form1...");
        session.setInterfaceResult(new InterfaceResult(
                sessionId, "Form1", "IForm1",
                "public interface IForm1 { int Add(int a, int b); }"));
        session.setTestsResult(new TestsResult(
                sessionId, "Form1", "Form1Tests",
                "[TestFixture] public class Form1Tests { [Test] public void Add_Works() {} }",
                1));
        session.setImplementResult(new ImplementResult(
                sessionId, "Form1",
                "public class Form1 : IForm1 { public int Add(int a, int b) => a + b; }",
                ImplementMode.CLAUDE));
        return session;
    }

    private void enqueueHappyPathResponses() {
        // 1. GET ref/heads/main
        enqueue(Map.of("object", Map.of("sha", "main-sha-123")));
        // 2. POST git/refs (create branch)
        enqueue(Map.of("ref", "refs/heads/migrate/form1"));
        // 3. GET git/commits/{sha} (get tree sha)
        enqueue(Map.of("tree", Map.of("sha", "base-tree-sha")));
        // 4. POST git/blobs × 3
        enqueue(Map.of("sha", "blob-iface"));
        enqueue(Map.of("sha", "blob-impl"));
        enqueue(Map.of("sha", "blob-tests"));
        // 5. POST git/trees
        enqueue(Map.of("sha", "new-tree-sha"));
        // 6. POST git/commits
        enqueue(Map.of("sha", "new-commit-sha"));
        // 7. PATCH git/refs/heads/...
        enqueue(Map.of("ref", "refs/heads/migrate/form1"));
        // 8. POST pulls
        enqueue(Map.of("html_url", "https://github.com/owner/repo/pull/42"));
    }

    private void enqueue(Object body) {
        try {
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(objectMapper.writeValueAsString(body)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── Happy path ──

    @Test
    void raisePR_createsbranchCommitsAndReturnsPR() throws Exception {
        MigrationSession session = fullSession("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        enqueueHappyPathResponses();

        PullRequestResult result = service.raisePR("s1", "owner", "repo", "migrate/form1");

        assertThat(result.sessionId()).isEqualTo("s1");
        assertThat(result.prUrl()).isEqualTo("https://github.com/owner/repo/pull/42");
        assertThat(result.branchName()).isEqualTo("migrate/form1");
        assertThat(result.filesCommitted()).containsExactly(
                "Form1/IForm1.cs",
                "Form1/Form1.cs",
                "Form1.Tests/Form1Tests.cs");
        assertThat(session.getPrResult()).isEqualTo(result);
    }

    // ── Request verification ──

    @Test
    void raisePR_callsCorrectEndpointsInOrder() throws Exception {
        MigrationSession session = fullSession("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        enqueueHappyPathResponses();

        service.raisePR("s1", "owner", "repo", "migrate/form1");

        assertThat(mockWebServer.takeRequest().getPath())
                .isEqualTo("/repos/owner/repo/git/ref/heads/main");
        assertThat(mockWebServer.takeRequest().getPath())
                .isEqualTo("/repos/owner/repo/git/refs");
        assertThat(mockWebServer.takeRequest().getPath())
                .isEqualTo("/repos/owner/repo/git/commits/main-sha-123");
        assertThat(mockWebServer.takeRequest().getPath())
                .isEqualTo("/repos/owner/repo/git/blobs");
        assertThat(mockWebServer.takeRequest().getPath())
                .isEqualTo("/repos/owner/repo/git/blobs");
        assertThat(mockWebServer.takeRequest().getPath())
                .isEqualTo("/repos/owner/repo/git/blobs");
        assertThat(mockWebServer.takeRequest().getPath())
                .isEqualTo("/repos/owner/repo/git/trees");
        assertThat(mockWebServer.takeRequest().getPath())
                .isEqualTo("/repos/owner/repo/git/commits");
        assertThat(mockWebServer.takeRequest().getPath())
                .isEqualTo("/repos/owner/repo/git/refs/heads/migrate/form1");
        assertThat(mockWebServer.takeRequest().getPath())
                .isEqualTo("/repos/owner/repo/pulls");
    }

    @Test
    void raisePR_sendsAuthHeaderOnAllRequests() throws Exception {
        MigrationSession session = fullSession("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        enqueueHappyPathResponses();

        service.raisePR("s1", "owner", "repo", "migrate/form1");

        for (int i = 0; i < 10; i++) {
            RecordedRequest request = mockWebServer.takeRequest();
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer ghp_test123");
            assertThat(request.getHeader("Accept")).isEqualTo("application/vnd.github+json");
        }
    }

    @Test
    void raisePR_createsBranchFromMainSha() throws Exception {
        MigrationSession session = fullSession("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        enqueueHappyPathResponses();

        service.raisePR("s1", "owner", "repo", "migrate/form1");

        mockWebServer.takeRequest(); // skip GET ref
        RecordedRequest createRef = mockWebServer.takeRequest();
        JsonNode body = objectMapper.readTree(createRef.getBody().readUtf8());
        assertThat(body.get("ref").asText()).isEqualTo("refs/heads/migrate/form1");
        assertThat(body.get("sha").asText()).isEqualTo("main-sha-123");
    }

    @Test
    void raisePR_prHasCorrectTitleAndBody() throws Exception {
        MigrationSession session = fullSession("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        enqueueHappyPathResponses();

        service.raisePR("s1", "owner", "repo", "migrate/form1");

        // Skip to the PR request (10th)
        for (int i = 0; i < 9; i++) {
            mockWebServer.takeRequest();
        }
        RecordedRequest prRequest = mockWebServer.takeRequest();
        JsonNode body = objectMapper.readTree(prRequest.getBody().readUtf8());

        assertThat(body.get("title").asText()).isEqualTo("Migrate Form1 from VB.NET to C#");
        assertThat(body.get("head").asText()).isEqualTo("migrate/form1");
        assertThat(body.get("base").asText()).isEqualTo("main");

        String prBody = body.get("body").asText();
        assertThat(prBody).contains("Form1/IForm1.cs");
        assertThat(prBody).contains("Form1/Form1.cs");
        assertThat(prBody).contains("Form1.Tests/Form1Tests.cs");
        assertThat(prBody).contains("CI pipeline");
        assertThat(prBody).contains("VBGone");
    }

    @Test
    void raisePR_sendsFileContentAsBlobs() throws Exception {
        MigrationSession session = fullSession("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        enqueueHappyPathResponses();

        service.raisePR("s1", "owner", "repo", "migrate/form1");

        mockWebServer.takeRequest(); // GET ref
        mockWebServer.takeRequest(); // POST refs
        mockWebServer.takeRequest(); // GET commits

        // Blob 1: interface
        RecordedRequest blob1 = mockWebServer.takeRequest();
        JsonNode blob1Body = objectMapper.readTree(blob1.getBody().readUtf8());
        assertThat(blob1Body.get("content").asText()).contains("IForm1");
        assertThat(blob1Body.get("encoding").asText()).isEqualTo("utf-8");

        // Blob 2: implementation
        RecordedRequest blob2 = mockWebServer.takeRequest();
        JsonNode blob2Body = objectMapper.readTree(blob2.getBody().readUtf8());
        assertThat(blob2Body.get("content").asText()).contains("a + b");

        // Blob 3: tests
        RecordedRequest blob3 = mockWebServer.takeRequest();
        JsonNode blob3Body = objectMapper.readTree(blob3.getBody().readUtf8());
        assertThat(blob3Body.get("content").asText()).contains("Form1Tests");
    }

    // ── Precondition tests ──

    @Test
    void raisePR_throwsWhenSessionNotFound() {
        when(sessionStore.get("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.raisePR("bad", "o", "r", "b"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session not found");
    }

    @Test
    void raisePR_throwsWhenNoInterface() {
        MigrationSession session = new MigrationSession("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.raisePR("s1", "o", "r", "b"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Interface");
    }

    @Test
    void raisePR_throwsWhenNoTests() {
        MigrationSession session = new MigrationSession("s1");
        session.setInterfaceResult(new InterfaceResult("s1", "Form1", "IForm1", "..."));
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.raisePR("s1", "o", "r", "b"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tests");
    }

    @Test
    void raisePR_throwsWhenNoImplementation() {
        MigrationSession session = new MigrationSession("s1");
        session.setInterfaceResult(new InterfaceResult("s1", "Form1", "IForm1", "..."));
        session.setTestsResult(new TestsResult("s1", "Form1", "Form1Tests", "...", 1));
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.raisePR("s1", "o", "r", "b"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Implementation");
    }

    // ── Error handling ──

    @Test
    void raisePR_throwsOnGitHubApiError() {
        MigrationSession session = fullSession("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("{\"message\":\"Not Found\"}"));

        assertThatThrownBy(() -> service.raisePR("s1", "owner", "repo", "branch"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("GitHub API");
    }

    // ── PR body builder ──

    @Test
    void buildPrBody_containsAllSections() {
        String body = service.buildPrBody("Form1", java.util.List.of(
                "Form1/IForm1.cs", "Form1/Form1.cs", "Form1.Tests/Form1Tests.cs"));

        assertThat(body).contains("## VB.NET to C# Migration: Form1");
        assertThat(body).contains("**Form1**");
        assertThat(body).contains("`Form1/IForm1.cs`");
        assertThat(body).contains("`Form1/Form1.cs`");
        assertThat(body).contains("`Form1.Tests/Form1Tests.cs`");
        assertThat(body).contains("CI pipeline");
        assertThat(body).contains("VBGone");
    }
}
