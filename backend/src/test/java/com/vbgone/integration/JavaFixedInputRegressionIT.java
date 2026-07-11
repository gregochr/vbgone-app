package com.vbgone.integration;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vbgone.ai.AiProviderRegistry;
import com.vbgone.ai.AiRequestOptions;
import com.vbgone.ai.anthropic.AnthropicProvider;
import com.vbgone.ai.github.GitHubModelsProvider;
import com.vbgone.build.BuildRuntimeRegistry;
import com.vbgone.build.JavaRuntime;
import com.vbgone.lang.LanguageConventionsRegistry;
import com.vbgone.model.*;
import com.vbgone.service.*;
import com.vbgone.session.SessionStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Java-target end-to-end regression test — the Java analog of {@link FixedInputRegressionIT}.
 *
 * <p>Unlike the C# IT (which seeds a fixed interface + tests), this drives the real
 * generation pipeline against Claude for the Calculator (Form1) fixture with
 * {@code targetLanguage="java"}: generateInterface → generateTests → generateStub →
 * build (RED) → implement (CLAUDE) → build (GREEN). It proves the whole Java path —
 * prompts, conventions (no {@code I}-prefix, {@code Impl} suffix,
 * {@code UnsupportedOperationException} stubs), Maven scaffolding, and Surefire parsing.
 *
 * <p>Run with: {@code ./mvnw test -Pintegration}
 *
 * <p>Skips cleanly when {@code ANTHROPIC_API_KEY} is absent or the Maven/JDK sidecar
 * container is unreachable.
 */
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JavaFixedInputRegressionIT {

    /** Same VB source the C# IT uses for its Calculator/Form1 case. */
    static final String VB = """
            Public Class Form1
                Private Sub Button1_Click(sender As Object, e As EventArgs) Handles Button1.Click
                    TextBox3.Text = Int(TextBox1.Text) + Int(TextBox2.Text)
                End Sub
                Private Sub Button2_Click(sender As Object, e As EventArgs) Handles Button2.Click
                    TextBox3.Text = Int(TextBox1.Text) - Int(TextBox2.Text)
                End Sub
                Private Sub Button3_Click(sender As Object, e As EventArgs) Handles Button3.Click
                    TextBox3.Text = Int(TextBox1.Text) * Int(TextBox2.Text)
                End Sub
                Private Sub Button4_Click(sender As Object, e As EventArgs) Handles Button4.Click
                    TextBox3.Text = Int(TextBox1.Text) / Int(TextBox2.Text)
                End Sub
            End Class""";

    private static final String JAVA = "java";
    private static final String CONTAINER_NAME = "vbgone-jdk-maven-runner";

    private static SessionStore sessionStore;
    private static GenerationService generationService;
    private static BuildService buildService;

    private static String sessionId;

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void setUp() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                "ANTHROPIC_API_KEY not set — skipping integration tests");

        Assumptions.assumeTrue(mavenSidecarReachable(),
                "Maven/JDK sidecar container '" + CONTAINER_NAME + "' not reachable — skipping");

        sessionStore = new SessionStore();
        var anthropicClient = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
        ClaudeClient claudeClient = new ClaudeClient(anthropicClient);
        AiProviderRegistry registry = new AiProviderRegistry(List.of(
                new AnthropicProvider(claudeClient),
                new GitHubModelsProvider("", new ObjectMapper())));

        String workspace = tempDir.toString();
        ProcessRunner processRunner = new DockerMavenProcessRunner(CONTAINER_NAME, tempDir);

        var promptRegistry = new com.vbgone.prompt.PromptLanguageRegistry(List.of(
                new com.vbgone.prompt.CSharpPrompts(), new com.vbgone.prompt.JavaPrompts()));
        var conventionsRegistry = new LanguageConventionsRegistry(List.of(
                new com.vbgone.lang.CSharpConventions(), new com.vbgone.lang.JavaConventions()));
        var aiCallSupport = new com.vbgone.service.AiCallSupport(registry);
        generationService = new GenerationService(aiCallSupport, sessionStore, promptRegistry, conventionsRegistry);

        JavaRuntime javaRuntime = new JavaRuntime(sessionStore, workspace, CONTAINER_NAME,
                processRunner, conventionsRegistry);
        BuildRuntimeRegistry runtimeRegistry = new BuildRuntimeRegistry(List.of(javaRuntime));
        buildService = new BuildService(sessionStore, runtimeRegistry);

        MigrationSession session = sessionStore.create();
        sessionId = session.getSessionId();
        session.setVbContent(VB);
        session.setTargetLanguage(JAVA);
    }

    /** {@code docker exec <container> mvn -version} returns 0 when the sidecar is up. */
    private static boolean mavenSidecarReachable() {
        try {
            Process p = new ProcessBuilder("docker", "exec", CONTAINER_NAME, "mvn", "-version")
                    .redirectErrorStream(true)
                    .start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @Order(1)
    @DisplayName("Calculator (java): generate interface → no I-prefix")
    void generateInterface_dropsIPrefix() {
        InterfaceResult iface = generationService.generateInterface(
                sessionId, "Form1", AiRequestOptions.of("anthropic", JAVA, Map.of()));

        // Java conventions: interface name == className (no I-prefix), impl == className + "Impl"
        assertThat(iface.interfaceName()).isEqualTo("Form1");
        assertThat(iface.implName()).isEqualTo("Form1Impl");
        assertThat(iface.code()).contains("interface Form1");
    }

    @Test
    @Order(2)
    @DisplayName("Calculator (java): generate tests → testCount > 0")
    void generateTests_producesTests() {
        TestsResult tests = generationService.generateTests(
                sessionId, "Form1", AiRequestOptions.of("anthropic", JAVA, Map.of()));

        assertThat(tests.testClassName()).isEqualTo("Form1Test");
        assertThat(tests.testCount()).isGreaterThan(0);
    }

    @Test
    @Order(3)
    @DisplayName("Calculator (java): stub → RED (all tests fail)")
    void stubBuild_allTestsFail() {
        StubResult stub = generationService.generateStub(
                sessionId, "Form1", AiRequestOptions.of("anthropic", JAVA, Map.of()));
        assertThat(stub.code()).contains("UnsupportedOperationException");

        BuildResult result = buildService.build(sessionId);
        System.out.println("=== Calculator (java) Stub: " + result.buildStatus()
                + " " + result.passed() + "/" + result.total() + " ===");
        if (!result.errors().isEmpty()) System.out.println("  Errors: " + result.errors());

        assertThat(result.buildStatus()).isEqualTo(BuildStatus.RED);
        assertThat(result.total()).isGreaterThan(0);
        assertThat(result.failed()).isEqualTo(result.total());
    }

    @Test
    @Order(4)
    @DisplayName("Calculator (java): Claude implement → GREEN (all tests pass)")
    void claudeImplementation_allTestsPass() {
        ImplementResult impl = generationService.implement(
                sessionId, "Form1", ImplementMode.CLAUDE, AiRequestOptions.of("anthropic", JAVA, Map.of()));
        assertThat(impl.code()).contains("class Form1Impl");
        assertThat(impl.code()).contains("implements Form1");

        BuildResult result = buildService.build(sessionId);
        System.out.println("=== Calculator (java) Impl: " + result.buildStatus()
                + " " + result.passed() + "/" + result.total() + " ===");
        if (!result.failedTests().isEmpty()) System.out.println("  Failed: " + result.failedTests());
        if (!result.errors().isEmpty()) System.out.println("  Errors: " + result.errors());

        assertThat(result.buildStatus()).isEqualTo(BuildStatus.GREEN);
        assertThat(result.total()).isGreaterThan(0);
        assertThat(result.passed()).isEqualTo(result.total());
        assertThat(result.failed()).isEqualTo(0);
    }
}
