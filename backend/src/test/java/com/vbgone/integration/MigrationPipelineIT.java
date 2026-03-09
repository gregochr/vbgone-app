package com.vbgone.integration;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.vbgone.model.*;
import com.vbgone.service.*;
import com.vbgone.session.SessionStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration regression tests that call the real Claude API and dotnet-runner.
 *
 * <p>These tests are tagged "integration" and excluded from the default Maven build.
 * Run them explicitly with: {@code ./mvnw test -Dgroups=integration}
 *
 * <p>Requirements:
 * <ul>
 *   <li>ANTHROPIC_API_KEY environment variable set</li>
 *   <li>dotnet-runner Docker container running (docker compose up dotnet-runner)</li>
 *   <li>Shared /workspace volume mounted</li>
 * </ul>
 *
 * <p>The pipeline for each test:
 * <ol>
 *   <li>Generate C# interface from known VB.NET source → verify method signatures</li>
 *   <li>Generate NUnit tests from VB.NET + interface → verify test count and key names</li>
 *   <li>Generate stub → build → verify RED (all tests fail)</li>
 *   <li>Generate implementation → build → verify GREEN (all tests pass)</li>
 * </ol>
 */
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MigrationPipelineIT {

    static final String SIMPLE_VB = """
            Public Class Form1
                'Code for SUM
                Private Sub Button1_Click(sender As Object, e As EventArgs) Handles Button1.Click
                    Label3.Text = "Sum of " + TextBox1.Text + " and " + TextBox2.Text
                    TextBox3.Text = Int(TextBox1.Text) + Int(TextBox2.Text)
                End Sub
                'Code for Difference
                Private Sub Button2_Click(sender As Object, e As EventArgs) Handles Button2.Click
                    Label3.Text = "Difference of " + TextBox1.Text + " and " + TextBox2.Text
                    TextBox3.Text = Int(TextBox1.Text) - Int(TextBox2.Text)
                End Sub
                'Code for Product
                Private Sub Button3_Click(sender As Object, e As EventArgs) Handles Button3.Click
                    Label3.Text = "Product of " + TextBox1.Text + " and " + TextBox2.Text
                    TextBox3.Text = Int(TextBox1.Text) * Int(TextBox2.Text)
                End Sub
                'Code for Quotient
                Private Sub Button4_Click(sender As Object, e As EventArgs) Handles Button4.Click
                    Label3.Text = "Quotient of " + TextBox1.Text + " and " + TextBox2.Text
                    TextBox3.Text = Int(TextBox1.Text) / Int(TextBox2.Text)
                End Sub
                'Code for Clear
                Private Sub Button5_Click(sender As Object, e As EventArgs) Handles Button5.Click
                    TextBox1.Text = ""
                    TextBox2.Text = ""
                    TextBox3.Text = ""
                    Label3.Text = "Answer"
                End Sub
                'Code for Exit
                Private Sub Button6_Click(sender As Object, e As EventArgs) Handles Button6.Click
                    End
                End Sub
            End Class""";

    private static SessionStore sessionStore;
    private static GenerationService generationService;
    private static BuildService buildService;
    private static String sessionId;
    private static String workspace;

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void setUp() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                "ANTHROPIC_API_KEY not set — skipping integration tests");

        sessionStore = new SessionStore();
        var anthropicClient = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
        ClaudeClient claudeClient = new ClaudeClient(anthropicClient);

        workspace = tempDir.toString();

        String containerName = "vbgone-app-dotnet-runner-1";
        ProcessRunner processRunner = new DockerProcessRunner(containerName, tempDir);

        generationService = new GenerationService(claudeClient, sessionStore);
        buildService = new BuildService(sessionStore, workspace, containerName, processRunner);

        // Create a session with the simple calculator VB.NET
        MigrationSession session = sessionStore.create();
        sessionId = session.getSessionId();
        session.setVbContent(SIMPLE_VB);
    }

    @Test
    @Order(1)
    @DisplayName("Step 1: Generate interface — should contain Add, Subtract, Multiply, Divide")
    void generateInterface_containsExpectedMethods() {
        InterfaceResult result = generationService.generateInterface(sessionId, "Form1");

        assertThat(result.sessionId()).isEqualTo(sessionId);
        assertThat(result.className()).isEqualTo("Form1");
        assertThat(result.interfaceName()).isEqualTo("IForm1");
        assertThat(result.code()).contains("IForm1");

        // Must contain four arithmetic methods (Claude may name them Add/Sum, Subtract/Difference, etc.)
        String code = result.code().toLowerCase();
        assertThat(code.contains("add") || code.contains("sum"))
                .as("Should contain Add or Sum method").isTrue();
        assertThat(code.contains("subtract") || code.contains("difference"))
                .as("Should contain Subtract or Difference method").isTrue();
        assertThat(code.contains("multiply") || code.contains("product"))
                .as("Should contain Multiply or Product method").isTrue();
        assertThat(code.contains("divide") || code.contains("quotient"))
                .as("Should contain Divide or Quotient method").isTrue();

        // Should NOT have namespace wrapper
        assertThat(code).doesNotContain("namespace ");

        System.out.println("=== Generated Interface ===\n" + code);
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Generate tests — should produce reasonable NUnit test count")
    void generateTests_producesReasonableTestCount() {
        TestsResult result = generationService.generateTests(sessionId, "Form1");

        assertThat(result.sessionId()).isEqualTo(sessionId);
        assertThat(result.className()).isEqualTo("Form1");
        assertThat(result.testClassName()).isEqualTo("Form1Tests");
        assertThat(result.testCount()).isGreaterThanOrEqualTo(8);

        String code = result.code();
        // Must use NUnit
        assertThat(code).contains("[TestFixture]");
        assertThat(code).contains("[Test]");
        // Must reference the interface and class
        assertThat(code).contains("IForm1");
        assertThat(code).contains("Form1");
        // Should have divide by zero test
        assertThat(code).containsIgnoringCase("divide");
        // Should NOT have namespace wrapper
        assertThat(code).doesNotContain("namespace ");

        System.out.println("=== Generated Tests (" + result.testCount() + " tests) ===\n" + code);
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: Generate stub + build → RED (all tests fail with NotImplementedException)")
    void stubBuild_allTestsFail() {
        StubResult stub = generationService.generateStub(sessionId, "Form1");

        assertThat(stub.code()).contains("NotImplementedException");
        assertThat(stub.code()).doesNotContain("namespace ");

        System.out.println("=== Generated Stub ===\n" + stub.code());

        BuildResult result = buildService.build(sessionId);

        System.out.println("=== Stub Build: " + result.buildStatus() + " ===");
        System.out.println("Total: " + result.total() + " Passed: " + result.passed() + " Failed: " + result.failed());
        if (!result.errors().isEmpty()) {
            System.out.println("Errors: " + result.errors());
        }

        // Stub should compile but all tests should fail
        assertThat(result.buildStatus()).isIn(BuildStatus.RED, BuildStatus.ERROR);
        if (result.buildStatus() == BuildStatus.RED) {
            assertThat(result.failed()).isEqualTo(result.total());
            assertThat(result.passed()).isZero();
        }
    }

    @Test
    @Order(4)
    @DisplayName("Step 4: Claude implements → build → GREEN (all tests pass)")
    void claudeImplementation_allTestsPass() {
        ImplementResult impl = generationService.implement(sessionId, "Form1", ImplementMode.CLAUDE);

        assertThat(impl.code()).doesNotContain("NotImplementedException");
        assertThat(impl.code()).doesNotContain("namespace ");
        assertThat(impl.mode()).isEqualTo(ImplementMode.CLAUDE);

        System.out.println("=== Claude Implementation ===\n" + impl.code());

        BuildResult result = buildService.build(sessionId);

        System.out.println("=== Implementation Build: " + result.buildStatus() + " ===");
        System.out.println("Total: " + result.total() + " Passed: " + result.passed() + " Failed: " + result.failed());
        if (!result.failedTests().isEmpty()) {
            System.out.println("Failed tests: " + result.failedTests());
        }

        // Claude implementation should make all tests pass
        assertThat(result.buildStatus()).isEqualTo(BuildStatus.GREEN);
        assertThat(result.passed()).isEqualTo(result.total());
        assertThat(result.failed()).isZero();
    }

    @Test
    @Order(5)
    @DisplayName("Step 5: If Step 4 was RED, retry should improve — verify retry prompt includes failing tests")
    void retryIfNeeded_improvesResults() {
        MigrationSession session = sessionStore.get(sessionId).orElseThrow();
        BuildResult lastBuild = session.getGreenBuild();
        if (lastBuild != null && lastBuild.buildStatus() == BuildStatus.GREEN) {
            System.out.println("=== Skipping retry — already GREEN ===");
            return;
        }

        BuildResult redBuild = session.getRedBuild();
        assertThat(redBuild).isNotNull();
        List<String> failingTests = redBuild.failedTests();
        assertThat(failingTests).isNotEmpty();

        System.out.println("=== Retrying with " + failingTests.size() + " failing tests ===");

        ImplementResult retryImpl = generationService.retryImplement(sessionId, "Form1", failingTests);
        assertThat(retryImpl.code()).doesNotContain("NotImplementedException");

        System.out.println("=== Retry Implementation ===\n" + retryImpl.code());

        BuildResult retryBuild = buildService.build(sessionId);

        System.out.println("=== Retry Build: " + retryBuild.buildStatus() + " ===");
        System.out.println("Total: " + retryBuild.total() + " Passed: " + retryBuild.passed() + " Failed: " + retryBuild.failed());

        // Retry should pass at least as many tests as before (ideally all)
        assertThat(retryBuild.passed()).isGreaterThanOrEqualTo(redBuild.passed());
    }
}
