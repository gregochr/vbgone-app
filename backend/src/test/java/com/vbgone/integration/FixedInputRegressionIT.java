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
 * Deterministic regression tests with FIXED interface and test code.
 *
 * <p>The interface and NUnit tests are hardcoded — the only variable is Claude's implementation.
 * This catches regressions where Claude generates wrong class names, wrong interfaces,
 * or broken implementations.
 *
 * <p>Run with: {@code ./mvnw test -Dgroups=integration}
 */
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FixedInputRegressionIT {

    // ── Known-good VB.NET source ──

    static final String CALCULATOR_VB = """
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

    // ── Fixed interface — this is the contract ──

    static final String FIXED_INTERFACE = """
            public interface IForm1
            {
                int Add(int a, int b);
                int Subtract(int a, int b);
                int Multiply(int a, int b);
                double Divide(int a, int b);
            }""";

    // ── Fixed NUnit tests — these define correctness ──

    static final String FIXED_TESTS = """
            using NUnit.Framework;

            [TestFixture]
            public class Form1Tests
            {
                private IForm1 _sut;

                [SetUp]
                public void SetUp()
                {
                    _sut = new Form1();
                }

                [Test]
                public void Add_PositiveNumbers_ReturnsSum()
                {
                    Assert.That(_sut.Add(2, 3), Is.EqualTo(5));
                }

                [Test]
                public void Add_NegativeNumbers_ReturnsSum()
                {
                    Assert.That(_sut.Add(-2, -3), Is.EqualTo(-5));
                }

                [Test]
                public void Add_Zero_ReturnsOther()
                {
                    Assert.That(_sut.Add(0, 7), Is.EqualTo(7));
                }

                [Test]
                public void Subtract_PositiveNumbers_ReturnsDifference()
                {
                    Assert.That(_sut.Subtract(10, 3), Is.EqualTo(7));
                }

                [Test]
                public void Subtract_ResultNegative_ReturnsNegative()
                {
                    Assert.That(_sut.Subtract(3, 10), Is.EqualTo(-7));
                }

                [Test]
                public void Multiply_PositiveNumbers_ReturnsProduct()
                {
                    Assert.That(_sut.Multiply(4, 5), Is.EqualTo(20));
                }

                [Test]
                public void Multiply_ByZero_ReturnsZero()
                {
                    Assert.That(_sut.Multiply(100, 0), Is.EqualTo(0));
                }

                [Test]
                public void Divide_EvenDivision_ReturnsQuotient()
                {
                    Assert.That(_sut.Divide(10, 2), Is.EqualTo(5.0));
                }

                [Test]
                public void Divide_UnevenDivision_ReturnsDecimal()
                {
                    Assert.That(_sut.Divide(7, 2), Is.EqualTo(3.5));
                }

                [Test]
                public void Divide_ByZero_ThrowsDivideByZeroException()
                {
                    Assert.Throws<DivideByZeroException>(() => _sut.Divide(1, 0));
                }
            }""";

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

        sessionStore = new SessionStore();
        var anthropicClient = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
        ClaudeClient claudeClient = new ClaudeClient(anthropicClient);

        String workspace = tempDir.toString();
        String containerName = "vbgone-app-dotnet-runner-1";
        ProcessRunner processRunner = new DockerProcessRunner(containerName, tempDir);

        generationService = new GenerationService(claudeClient, sessionStore);
        buildService = new BuildService(sessionStore, workspace, containerName, processRunner);

        // Create session with fixed interface and tests already set
        MigrationSession session = sessionStore.create();
        sessionId = session.getSessionId();
        session.setVbContent(CALCULATOR_VB);
        session.setInterfaceResult(new InterfaceResult(
                sessionId, "Form1", "IForm1", FIXED_INTERFACE));
        session.setTestsResult(new TestsResult(
                sessionId, "Form1", "Form1Tests", FIXED_TESTS, 10));
    }

    @Test
    @Order(1)
    @DisplayName("Fixed tests: stub build → RED, all 10 tests fail")
    void stubBuild_allTestsFail() {
        // Generate stub from the fixed interface
        StubResult stub = generationService.generateStub(sessionId, "Form1");

        assertThat(stub.code()).contains("NotImplementedException");
        assertThat(stub.code()).contains("Form1");
        assertThat(stub.code()).contains("IForm1");

        System.out.println("=== Stub ===\n" + stub.code());

        BuildResult result = buildService.build(sessionId);

        System.out.println("=== Stub Build: " + result.buildStatus()
                + " | Total: " + result.total()
                + " Passed: " + result.passed()
                + " Failed: " + result.failed() + " ===");

        assertThat(result.buildStatus()).isEqualTo(BuildStatus.RED);
        assertThat(result.total()).isEqualTo(10);
        assertThat(result.failed()).isEqualTo(10);
        assertThat(result.passed()).isZero();
    }

    @Test
    @Order(2)
    @DisplayName("Fixed tests: Claude implementation → GREEN, all 10 tests pass")
    void claudeImplementation_allTestsPass() {
        ImplementResult impl = generationService.implement(sessionId, "Form1", ImplementMode.CLAUDE);

        // Class name must be correct
        assertThat(impl.code()).contains("class Form1");
        assertThat(impl.code()).contains("IForm1");
        assertThat(impl.code()).doesNotContain("NotImplementedException");

        System.out.println("=== Claude Implementation ===\n" + impl.code());

        BuildResult result = buildService.build(sessionId);

        System.out.println("=== Build: " + result.buildStatus()
                + " | Total: " + result.total()
                + " Passed: " + result.passed()
                + " Failed: " + result.failed() + " ===");
        if (!result.failedTests().isEmpty()) {
            System.out.println("Failed: " + result.failedTests());
        }
        if (!result.errors().isEmpty()) {
            System.out.println("Errors: " + result.errors());
        }

        assertThat(result.buildStatus()).isEqualTo(BuildStatus.GREEN);
        assertThat(result.total()).isEqualTo(10);
        assertThat(result.passed()).isEqualTo(10);
        assertThat(result.failed()).isZero();
    }

    @Test
    @Order(3)
    @DisplayName("Fixed tests: retry after artificial RED improves results")
    void retryAfterRed_improvesOrMaintainsGreen() {
        MigrationSession session = sessionStore.get(sessionId).orElseThrow();
        BuildResult lastBuild = session.getGreenBuild();

        // If already GREEN, simulate a RED scenario for retry testing
        if (lastBuild != null && lastBuild.buildStatus() == BuildStatus.GREEN) {
            System.out.println("=== Already GREEN — verifying retry with known failing test ===");
            // Retry with a single test name to verify the retry path works end-to-end
            ImplementResult retry = generationService.retryImplement(
                    sessionId, "Form1", List.of("Divide_ByZero_ThrowsDivideByZeroException"));

            assertThat(retry.code()).contains("class Form1");
            assertThat(retry.code()).contains("IForm1");
            assertThat(retry.code()).contains("DivideByZeroException");

            System.out.println("=== Retry Implementation ===\n" + retry.code());

            BuildResult retryBuild = buildService.build(sessionId);

            System.out.println("=== Retry Build: " + retryBuild.buildStatus()
                    + " | Passed: " + retryBuild.passed()
                    + " Failed: " + retryBuild.failed() + " ===");

            assertThat(retryBuild.buildStatus()).isEqualTo(BuildStatus.GREEN);
            assertThat(retryBuild.passed()).isEqualTo(10);
        }
    }
}
