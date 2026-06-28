package com.vbgone.integration;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vbgone.ai.AiProviderRegistry;
import com.vbgone.ai.anthropic.AnthropicProvider;
import com.vbgone.ai.github.GitHubModelsProvider;
import com.vbgone.build.BuildRuntimeRegistry;
import com.vbgone.build.DotNetRuntime;
import com.vbgone.model.*;
import com.vbgone.service.*;
import com.vbgone.session.SessionStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
 * <p>Run with: {@code ./mvnw test -Pintegration}
 */
@Tag("integration")
class FixedInputRegressionIT {

    private static SessionStore sessionStore;
    private static GenerationService generationService;
    private static BuildService buildService;

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
        AiProviderRegistry registry = new AiProviderRegistry(List.of(
                new AnthropicProvider(claudeClient),
                new GitHubModelsProvider("", new ObjectMapper())));

        String workspace = tempDir.toString();
        String containerName = "vbgone-app-dotnet-runner-1";
        ProcessRunner processRunner = new DockerProcessRunner(containerName, tempDir);

        var promptRegistry = new com.vbgone.prompt.PromptLanguageRegistry(List.of(
                new com.vbgone.prompt.CSharpPrompts(), new com.vbgone.prompt.JavaPrompts()));
        var conventionsRegistry = new com.vbgone.lang.LanguageConventionsRegistry(List.of(
                new com.vbgone.lang.CSharpConventions(), new com.vbgone.lang.JavaConventions()));
        generationService = new GenerationService(registry, sessionStore, promptRegistry, conventionsRegistry);
        DotNetRuntime dotnetRuntime = new DotNetRuntime(sessionStore, workspace, containerName, processRunner);
        BuildRuntimeRegistry runtimeRegistry = new BuildRuntimeRegistry(List.of(dotnetRuntime));
        buildService = new BuildService(sessionStore, runtimeRegistry);
    }

    static String loadFixture(String path) {
        try (InputStream is = FixedInputRegressionIT.class.getResourceAsStream("/fixtures/" + path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retries implementation up to 2 times (Sonnet then Opus), matching prod flow.
     * Returns the final BuildResult.
     */
    static BuildResult retryUntilGreen(String sessionId, String className, int expectedTotal) {
        for (int attempt = 2; attempt <= 3; attempt++) {
            MigrationSession session = sessionStore.get(sessionId).orElseThrow();
            BuildResult latest = session.getGreenBuild() != null ? session.getGreenBuild() : session.getRedBuild();
            if (latest != null && latest.buildStatus() == BuildStatus.GREEN) {
                return latest;
            }

            BuildResult redBuild = session.getRedBuild();
            assertThat(redBuild).isNotNull();
            List<String> failingTests = redBuild.failedTests();
            String model = attempt >= 3 ? "Opus" : "Sonnet";
            System.out.println("=== " + className + " retry attempt " + attempt + " (" + model + ") — "
                    + failingTests.size() + " failing tests ===");

            ImplementResult retry = generationService.retryImplement(
                    sessionId, className, failingTests, attempt);
            assertThat(retry.code()).contains("class " + className);

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== " + className + " Retry " + attempt + ": " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");
            if (!result.failedTests().isEmpty()) System.out.println("  Failed: " + result.failedTests());

            if (result.buildStatus() == BuildStatus.GREEN) {
                return result;
            }
        }
        // Return whatever we have after all attempts
        MigrationSession session = sessionStore.get(sessionId).orElseThrow();
        return session.getRedBuild();
    }

    // ════════════════════════════════════════════════════════════════════
    //  Calculator (simple — 4 arithmetic methods, 10 tests)
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class Calculator {

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

        static final String INTERFACE = """
                public interface IForm1
                {
                    int Add(int a, int b);
                    int Subtract(int a, int b);
                    int Multiply(int a, int b);
                    double Divide(int a, int b);
                }""";

        static final String TESTS = """
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

        static String sessionId;

        @BeforeAll
        static void createSession() {
            MigrationSession session = sessionStore.create();
            sessionId = session.getSessionId();
            session.setVbContent(VB);
            session.setInterfaceResult(new InterfaceResult(sessionId, "Form1", "IForm1", INTERFACE));
            session.setTestsResult(new TestsResult(sessionId, "Form1", "Form1Tests", TESTS, 10));
        }

        @Test
        @Order(1)
        @DisplayName("Calculator: stub → RED (10/10 fail)")
        void stubBuild_allTestsFail() {
            StubResult stub = generationService.generateStub(sessionId, "Form1");
            assertThat(stub.code()).contains("NotImplementedException");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== Calculator Stub: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");

            assertThat(result.buildStatus()).isEqualTo(BuildStatus.RED);
            assertThat(result.total()).isEqualTo(10);
            assertThat(result.failed()).isEqualTo(10);
        }

        @Test
        @Order(2)
        @DisplayName("Calculator: Claude implement → GREEN (10/10 pass)")
        void claudeImplementation_allTestsPass() {
            ImplementResult impl = generationService.implement(sessionId, "Form1", ImplementMode.CLAUDE);
            assertThat(impl.code()).contains("class Form1");
            assertThat(impl.code()).contains("IForm1");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== Calculator Impl: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");
            if (!result.failedTests().isEmpty()) System.out.println("  Failed: " + result.failedTests());

            assertThat(result.buildStatus()).isEqualTo(BuildStatus.GREEN);
            assertThat(result.passed()).isEqualTo(10);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  WinFormsCalculator (Calculator with framework dependencies in analysis)
    //  Regression: System.Windows.Forms.* deps must be filtered, not scaffolded
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class WinFormsCalculator {

        static final String VB = """
                Public Class Form1
                    Private lblResult As System.Windows.Forms.Label
                    Private btnAdd As System.Windows.Forms.Button
                    Private txtA As System.Windows.Forms.TextBox
                    Private txtB As System.Windows.Forms.TextBox
                    Private Sub btnAdd_Click(sender As Object, e As EventArgs) Handles btnAdd.Click
                        lblResult.Text = CStr(CInt(txtA.Text) + CInt(txtB.Text))
                    End Sub
                    Private Sub btnSubtract_Click(sender As Object, e As EventArgs) Handles btnSubtract.Click
                        lblResult.Text = CStr(CInt(txtA.Text) - CInt(txtB.Text))
                    End Sub
                    Private Sub btnMultiply_Click(sender As Object, e As EventArgs) Handles btnMultiply.Click
                        lblResult.Text = CStr(CInt(txtA.Text) * CInt(txtB.Text))
                    End Sub
                    Private Sub btnDivide_Click(sender As Object, e As EventArgs) Handles btnDivide.Click
                        lblResult.Text = CStr(CInt(txtA.Text) / CInt(txtB.Text))
                    End Sub
                End Class""";

        static final String INTERFACE = """
                public interface IForm1
                {
                    int Add(int a, int b);
                    int Subtract(int a, int b);
                    int Multiply(int a, int b);
                    double Divide(int a, int b);
                }""";

        static final String TESTS = """
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
                    public void Subtract_PositiveNumbers_ReturnsDifference()
                    {
                        Assert.That(_sut.Subtract(10, 3), Is.EqualTo(7));
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
                    public void Divide_ByZero_ThrowsDivideByZeroException()
                    {
                        Assert.Throws<DivideByZeroException>(() => _sut.Divide(1, 0));
                    }
                }""";

        static String sessionId;

        @BeforeAll
        static void createSession() {
            MigrationSession session = sessionStore.create();
            sessionId = session.getSessionId();
            session.setVbContent(VB);
            // Analysis with framework dependencies — the bug that caused build errors
            session.setAnalysisResult(new AnalysisResult(sessionId,
                    List.of(new ClassInfo("Form1", List.of("Add", "Subtract", "Multiply", "Divide"),
                            List.of("System.Windows.Forms.Label", "System.Windows.Forms.Button",
                                    "System.Windows.Forms.TextBox", "System.Windows.Forms.Form"),
                            Complexity.LOW, CodeQuality.FAIR, List.of(), List.of(), List.of())),
                    List.of("Form1"), "Simple calculator with WinForms UI"));
            session.setInterfaceResult(new InterfaceResult(sessionId, "Form1", "IForm1", INTERFACE));
            session.setTestsResult(new TestsResult(sessionId, "Form1", "Form1Tests", TESTS, 7));
        }

        @Test
        @Order(1)
        @DisplayName("WinFormsCalculator: stub → RED (7/7 fail, no compile errors from framework deps)")
        void stubBuild_allTestsFail_noCompileErrors() {
            StubResult stub = generationService.generateStub(sessionId, "Form1");
            assertThat(stub.code()).contains("NotImplementedException");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== WinFormsCalculator Stub: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");
            if (!result.errors().isEmpty()) System.out.println("  Errors: " + result.errors());

            // Must NOT be ERROR — framework deps must be filtered, not scaffolded
            assertThat(result.buildStatus())
                    .as("Framework dependencies like System.Windows.Forms.Label must not cause compile errors")
                    .isEqualTo(BuildStatus.RED);
            assertThat(result.total()).isEqualTo(7);
            assertThat(result.failed()).isEqualTo(7);
        }

        @Test
        @Order(2)
        @DisplayName("WinFormsCalculator: Claude implement → GREEN (7/7 pass)")
        void claudeImplementation_allTestsPass() {
            ImplementResult impl = generationService.implement(sessionId, "Form1", ImplementMode.CLAUDE);
            assertThat(impl.code()).contains("class Form1");
            assertThat(impl.code()).contains("IForm1");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== WinFormsCalculator Impl: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");
            if (!result.failedTests().isEmpty()) System.out.println("  Failed: " + result.failedTests());

            assertThat(result.buildStatus()).isEqualTo(BuildStatus.GREEN);
            assertThat(result.passed()).isEqualTo(7);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  OrderValidator (complex — validation, discounts, shipping, 57 tests)
    //  Captured from live run 2026-03-10
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class OrderValidator {

        static String sessionId;

        @BeforeAll
        static void createSession() {
            String vb = loadFixture("order-validator/OrderProcessor.vb");
            String iface = loadFixture("order-validator/IOrderValidator.cs");
            String tests = loadFixture("order-validator/OrderValidatorTests.cs");

            MigrationSession session = sessionStore.create();
            sessionId = session.getSessionId();
            session.setVbContent(vb);
            session.setInterfaceResult(new InterfaceResult(
                    sessionId, "OrderValidator", "IOrderValidator", iface));
            session.setTestsResult(new TestsResult(
                    sessionId, "OrderValidator", "OrderValidatorTests", tests, 57));
        }

        @Test
        @Order(1)
        @DisplayName("OrderValidator: stub → RED (57/57 fail)")
        void stubBuild_allTestsFail() {
            StubResult stub = generationService.generateStub(sessionId, "OrderValidator");
            assertThat(stub.code()).contains("NotImplementedException");
            assertThat(stub.code()).contains("class OrderValidator");
            assertThat(stub.code()).contains("IOrderValidator");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== OrderValidator Stub: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");

            assertThat(result.buildStatus()).isEqualTo(BuildStatus.RED);
            assertThat(result.total()).isEqualTo(57);
            assertThat(result.failed()).isEqualTo(57);
        }

        @Test
        @Order(2)
        @DisplayName("OrderValidator: Claude implement → at least 50/57 pass")
        void claudeImplementation_mostTestsPass() {
            ImplementResult impl = generationService.implement(
                    sessionId, "OrderValidator", ImplementMode.CLAUDE);
            assertThat(impl.code()).contains("class OrderValidator");
            assertThat(impl.code()).contains("IOrderValidator");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== OrderValidator Impl: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");
            if (!result.failedTests().isEmpty()) System.out.println("  Failed: " + result.failedTests());
            if (!result.errors().isEmpty()) System.out.println("  Errors: " + result.errors());

            // Claude should pass the vast majority — allow up to 7 failures for retry
            assertThat(result.buildStatus()).isNotEqualTo(BuildStatus.ERROR);
            assertThat(result.passed()).isGreaterThanOrEqualTo(50);
        }

        @Test
        @Order(3)
        @DisplayName("OrderValidator: retry → GREEN (57/57 pass)")
        void retryIfNeeded_allTestsPass() {
            BuildResult result = retryUntilGreen(sessionId, "OrderValidator", 57);
            assertThat(result.buildStatus()).isEqualTo(BuildStatus.GREEN);
            assertThat(result.passed()).isEqualTo(57);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  OrderCalculatorService (complex — totals, discounts, shipping, 66 tests)
    //  Captured from live run 2026-03-10
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class OrderCalculatorService {

        static String sessionId;

        @BeforeAll
        static void createSession() {
            String vb = loadFixture("order-calculator-service/OrderProcessor.vb");
            String iface = loadFixture("order-calculator-service/IOrderCalculatorService.cs");
            String tests = loadFixture("order-calculator-service/OrderCalculatorServiceTests.cs");

            MigrationSession session = sessionStore.create();
            sessionId = session.getSessionId();
            session.setVbContent(vb);
            session.setInterfaceResult(new InterfaceResult(
                    sessionId, "OrderCalculatorService", "IOrderCalculatorService", iface));
            session.setTestsResult(new TestsResult(
                    sessionId, "OrderCalculatorService", "OrderCalculatorServiceTests", tests, 66));
        }

        @Test
        @Order(1)
        @DisplayName("OrderCalculatorService: stub → RED (66/66 fail)")
        void stubBuild_allTestsFail() {
            StubResult stub = generationService.generateStub(sessionId, "OrderCalculatorService");
            assertThat(stub.code()).contains("NotImplementedException");
            assertThat(stub.code()).contains("class OrderCalculatorService");
            assertThat(stub.code()).contains("IOrderCalculatorService");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== OrderCalculatorService Stub: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");

            assertThat(result.buildStatus()).isEqualTo(BuildStatus.RED);
            assertThat(result.total()).isEqualTo(66);
            assertThat(result.failed()).isEqualTo(66);
        }

        @Test
        @Order(2)
        @DisplayName("OrderCalculatorService: Claude implement → at least 60/66 pass")
        void claudeImplementation_mostTestsPass() {
            ImplementResult impl = generationService.implement(
                    sessionId, "OrderCalculatorService", ImplementMode.CLAUDE);
            assertThat(impl.code()).contains("class OrderCalculatorService");
            assertThat(impl.code()).contains("IOrderCalculatorService");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== OrderCalculatorService Impl: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");
            if (!result.failedTests().isEmpty()) System.out.println("  Failed: " + result.failedTests());
            if (!result.errors().isEmpty()) System.out.println("  Errors: " + result.errors());

            assertThat(result.buildStatus()).isNotEqualTo(BuildStatus.ERROR);
            assertThat(result.passed()).isGreaterThanOrEqualTo(60);
        }

        @Test
        @Order(3)
        @DisplayName("OrderCalculatorService: retry → GREEN (66/66 pass)")
        void retryIfNeeded_allTestsPass() {
            BuildResult result = retryUntilGreen(sessionId, "OrderCalculatorService", 66);
            assertThat(result.buildStatus()).isEqualTo(BuildStatus.GREEN);
            assertThat(result.passed()).isEqualTo(66);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  OrderCalculator (complex — validation, totals, discounts, 78 tests)
    //  Captured from live run 2026-03-10
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class OrderCalculator {

        static String sessionId;

        @BeforeAll
        static void createSession() {
            String vb = loadFixture("order-calculator/OrderProcessor.vb");
            String iface = loadFixture("order-calculator/IOrderCalculator.cs");
            String tests = loadFixture("order-calculator/OrderCalculatorTests.cs");

            MigrationSession session = sessionStore.create();
            sessionId = session.getSessionId();
            session.setVbContent(vb);
            session.setInterfaceResult(new InterfaceResult(
                    sessionId, "OrderCalculator", "IOrderCalculator", iface));
            session.setTestsResult(new TestsResult(
                    sessionId, "OrderCalculator", "OrderCalculatorTests", tests, 78));
        }

        @Test
        @Order(1)
        @DisplayName("OrderCalculator: stub → RED (78/78 fail)")
        void stubBuild_allTestsFail() {
            StubResult stub = generationService.generateStub(sessionId, "OrderCalculator");
            assertThat(stub.code()).contains("NotImplementedException");
            assertThat(stub.code()).contains("class OrderCalculator");
            assertThat(stub.code()).contains("IOrderCalculator");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== OrderCalculator Stub: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");

            assertThat(result.buildStatus()).isEqualTo(BuildStatus.RED);
            assertThat(result.total()).isEqualTo(78);
            assertThat(result.failed()).isEqualTo(78);
        }

        @Test
        @Order(2)
        @DisplayName("OrderCalculator: Claude implement → at least 70/78 pass")
        void claudeImplementation_mostTestsPass() {
            ImplementResult impl = generationService.implement(
                    sessionId, "OrderCalculator", ImplementMode.CLAUDE);
            assertThat(impl.code()).contains("class OrderCalculator");
            assertThat(impl.code()).contains("IOrderCalculator");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== OrderCalculator Impl: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");
            if (!result.failedTests().isEmpty()) System.out.println("  Failed: " + result.failedTests());
            if (!result.errors().isEmpty()) System.out.println("  Errors: " + result.errors());

            assertThat(result.buildStatus()).isNotEqualTo(BuildStatus.ERROR);
            assertThat(result.passed()).isGreaterThanOrEqualTo(70);
        }

        @Test
        @Order(3)
        @DisplayName("OrderCalculator: retry → GREEN (78/78 pass)")
        void retryIfNeeded_allTestsPass() {
            BuildResult result = retryUntilGreen(sessionId, "OrderCalculator", 78);
            assertThat(result.buildStatus()).isEqualTo(BuildStatus.GREEN);
            assertThat(result.passed()).isEqualTo(78);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  OrderCalculatorService v2 (50 tests, stronger prompt)
    //  Captured from live run 2026-03-10 — went GREEN on first attempt
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class OrderCalculatorServiceV2 {

        static String sessionId;

        @BeforeAll
        static void createSession() {
            String vb = loadFixture("order-calculator-service-v2/OrderProcessor.vb");
            String iface = loadFixture("order-calculator-service-v2/IOrderCalculatorService.cs");
            String tests = loadFixture("order-calculator-service-v2/OrderCalculatorServiceTests.cs");

            MigrationSession session = sessionStore.create();
            sessionId = session.getSessionId();
            session.setVbContent(vb);
            session.setInterfaceResult(new InterfaceResult(
                    sessionId, "OrderCalculatorService", "IOrderCalculatorService", iface));
            session.setTestsResult(new TestsResult(
                    sessionId, "OrderCalculatorService", "OrderCalculatorServiceTests", tests, 50));
        }

        @Test
        @Order(1)
        @DisplayName("OrderCalculatorService v2: stub → RED (50/50 fail)")
        void stubBuild_allTestsFail() {
            StubResult stub = generationService.generateStub(sessionId, "OrderCalculatorService");
            assertThat(stub.code()).contains("NotImplementedException");
            assertThat(stub.code()).contains("class OrderCalculatorService");
            assertThat(stub.code()).contains("IOrderCalculatorService");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== OrderCalculatorService v2 Stub: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");

            assertThat(result.buildStatus()).isEqualTo(BuildStatus.RED);
            assertThat(result.total()).isEqualTo(50);
            assertThat(result.failed()).isEqualTo(50);
        }

        @Test
        @Order(2)
        @DisplayName("OrderCalculatorService v2: Claude implement → at least 45/50 pass")
        void claudeImplementation_mostTestsPass() {
            ImplementResult impl = generationService.implement(
                    sessionId, "OrderCalculatorService", ImplementMode.CLAUDE);
            assertThat(impl.code()).contains("class OrderCalculatorService");
            assertThat(impl.code()).contains("IOrderCalculatorService");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== OrderCalculatorService v2 Impl: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");
            if (!result.failedTests().isEmpty()) System.out.println("  Failed: " + result.failedTests());
            if (!result.errors().isEmpty()) System.out.println("  Errors: " + result.errors());

            assertThat(result.buildStatus()).isNotEqualTo(BuildStatus.ERROR);
            assertThat(result.passed()).isGreaterThanOrEqualTo(45);
        }

        @Test
        @Order(3)
        @DisplayName("OrderCalculatorService v2: retry → GREEN (50/50 pass)")
        void retryIfNeeded_allTestsPass() {
            BuildResult result = retryUntilGreen(sessionId, "OrderCalculatorService", 50);
            assertThat(result.buildStatus()).isEqualTo(BuildStatus.GREEN);
            assertThat(result.passed()).isEqualTo(50);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  OrderValidator v2 (62 tests, stronger prompt)
    //  Captured from live run 2026-03-10 — 57/62 first attempt, retry needed
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class OrderValidatorV2 {

        static String sessionId;

        @BeforeAll
        static void createSession() {
            String vb = loadFixture("order-validator-v2/OrderProcessor.vb");
            String iface = loadFixture("order-validator-v2/IOrderValidator.cs");
            String tests = loadFixture("order-validator-v2/OrderValidatorTests.cs");

            MigrationSession session = sessionStore.create();
            sessionId = session.getSessionId();
            session.setVbContent(vb);
            session.setInterfaceResult(new InterfaceResult(
                    sessionId, "OrderValidator", "IOrderValidator", iface));
            session.setTestsResult(new TestsResult(
                    sessionId, "OrderValidator", "OrderValidatorTests", tests, 62));
        }

        @Test
        @Order(1)
        @DisplayName("OrderValidator v2: stub → RED (62/62 fail)")
        void stubBuild_allTestsFail() {
            StubResult stub = generationService.generateStub(sessionId, "OrderValidator");
            assertThat(stub.code()).contains("NotImplementedException");
            assertThat(stub.code()).contains("class OrderValidator");
            assertThat(stub.code()).contains("IOrderValidator");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== OrderValidator v2 Stub: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");

            assertThat(result.buildStatus()).isEqualTo(BuildStatus.RED);
            assertThat(result.total()).isEqualTo(62);
            assertThat(result.failed()).isEqualTo(62);
        }

        @Test
        @Order(2)
        @DisplayName("OrderValidator v2: Claude implement → at least 55/62 pass")
        void claudeImplementation_mostTestsPass() {
            ImplementResult impl = generationService.implement(
                    sessionId, "OrderValidator", ImplementMode.CLAUDE);
            assertThat(impl.code()).contains("class OrderValidator");
            assertThat(impl.code()).contains("IOrderValidator");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== OrderValidator v2 Impl: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");
            if (!result.failedTests().isEmpty()) System.out.println("  Failed: " + result.failedTests());
            if (!result.errors().isEmpty()) System.out.println("  Errors: " + result.errors());

            assertThat(result.buildStatus()).isNotEqualTo(BuildStatus.ERROR);
            assertThat(result.passed()).isGreaterThanOrEqualTo(55);
        }

        @Test
        @Order(3)
        @DisplayName("OrderValidator v2: retry → GREEN (62/62 pass)")
        void retryIfNeeded_allTestsPass() {
            BuildResult result = retryUntilGreen(sessionId, "OrderValidator", 62);
            assertThat(result.buildStatus()).isEqualTo(BuildStatus.GREEN);
            assertThat(result.passed()).isEqualTo(62);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  OrderNotificationService (74 tests, boundary conditions)
    //  Captured from live run 2026-03-10 — 67/74 first attempt
    //  Shipping thresholds and discount boundaries consistently tricky
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class OrderNotificationService {

        static String sessionId;

        @BeforeAll
        static void createSession() {
            String vb = loadFixture("order-notification-service/OrderProcessor.vb");
            String iface = loadFixture("order-notification-service/IOrderNotificationService.cs");
            String tests = loadFixture("order-notification-service/OrderNotificationServiceTests.cs");

            MigrationSession session = sessionStore.create();
            sessionId = session.getSessionId();
            session.setVbContent(vb);
            session.setInterfaceResult(new InterfaceResult(
                    sessionId, "OrderNotificationService", "IOrderNotificationService", iface));
            session.setTestsResult(new TestsResult(
                    sessionId, "OrderNotificationService", "OrderNotificationServiceTests", tests, 74));
        }

        @Test
        @Order(1)
        @DisplayName("OrderNotificationService: stub → RED (74/74 fail)")
        void stubBuild_allTestsFail() {
            StubResult stub = generationService.generateStub(sessionId, "OrderNotificationService");
            assertThat(stub.code()).contains("NotImplementedException");
            assertThat(stub.code()).contains("class OrderNotificationService");
            assertThat(stub.code()).contains("IOrderNotificationService");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== OrderNotificationService Stub: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");

            assertThat(result.buildStatus()).isEqualTo(BuildStatus.RED);
            assertThat(result.total()).isEqualTo(74);
            assertThat(result.failed()).isEqualTo(74);
        }

        @Test
        @Order(2)
        @DisplayName("OrderNotificationService: Claude implement → at least 60/74 pass")
        void claudeImplementation_mostTestsPass() {
            ImplementResult impl = generationService.implement(
                    sessionId, "OrderNotificationService", ImplementMode.CLAUDE);
            assertThat(impl.code()).contains("class OrderNotificationService");
            assertThat(impl.code()).contains("IOrderNotificationService");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== OrderNotificationService Impl: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");
            if (!result.failedTests().isEmpty()) System.out.println("  Failed: " + result.failedTests());
            if (!result.errors().isEmpty()) System.out.println("  Errors: " + result.errors());

            assertThat(result.buildStatus()).isNotEqualTo(BuildStatus.ERROR);
            assertThat(result.passed()).isGreaterThanOrEqualTo(60);
        }

        @Test
        @Order(3)
        @DisplayName("OrderNotificationService: retry → GREEN (74/74 pass)")
        void retryIfNeeded_allTestsPass() {
            BuildResult result = retryUntilGreen(sessionId, "OrderNotificationService", 74);
            assertThat(result.buildStatus()).isEqualTo(BuildStatus.GREEN);
            assertThat(result.passed()).isEqualTo(74);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  OrderRepository (63 tests)
    //  Captured from live run 2026-03-10 — 62/63 first attempt, retry needed
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class OrderRepository {

        static String sessionId;

        @BeforeAll
        static void createSession() {
            String vb = loadFixture("order-repository/OrderProcessor.vb");
            String iface = loadFixture("order-repository/IOrderRepository.cs");
            String tests = loadFixture("order-repository/OrderRepositoryTests.cs");

            MigrationSession session = sessionStore.create();
            sessionId = session.getSessionId();
            session.setVbContent(vb);
            session.setInterfaceResult(new InterfaceResult(
                    sessionId, "OrderRepository", "IOrderRepository", iface));
            session.setTestsResult(new TestsResult(
                    sessionId, "OrderRepository", "OrderRepositoryTests", tests, 63));
        }

        @Test
        @Order(1)
        @DisplayName("OrderRepository: stub → RED (63/63 fail)")
        void stubBuild_allTestsFail() {
            StubResult stub = generationService.generateStub(sessionId, "OrderRepository");
            assertThat(stub.code()).contains("NotImplementedException");
            assertThat(stub.code()).contains("class OrderRepository");
            assertThat(stub.code()).contains("IOrderRepository");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== OrderRepository Stub: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");

            assertThat(result.buildStatus()).isEqualTo(BuildStatus.RED);
            assertThat(result.total()).isEqualTo(63);
            assertThat(result.failed()).isEqualTo(63);
        }

        @Test
        @Order(2)
        @DisplayName("OrderRepository: Claude implement → at least 55/63 pass")
        void claudeImplementation_mostTestsPass() {
            ImplementResult impl = generationService.implement(
                    sessionId, "OrderRepository", ImplementMode.CLAUDE);
            assertThat(impl.code()).contains("class OrderRepository");
            assertThat(impl.code()).contains("IOrderRepository");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== OrderRepository Impl: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");
            if (!result.failedTests().isEmpty()) System.out.println("  Failed: " + result.failedTests());
            if (!result.errors().isEmpty()) System.out.println("  Errors: " + result.errors());

            assertThat(result.buildStatus()).isNotEqualTo(BuildStatus.ERROR);
            assertThat(result.passed()).isGreaterThanOrEqualTo(55);
        }

        @Test
        @Order(3)
        @DisplayName("OrderRepository: retry → GREEN (63/63 pass)")
        void retryIfNeeded_allTestsPass() {
            BuildResult result = retryUntilGreen(sessionId, "OrderRepository", 63);
            assertThat(result.buildStatus()).isEqualTo(BuildStatus.GREEN);
            assertThat(result.passed()).isEqualTo(63);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  RefundRepository (72 tests)
    //  Captured from live run 2026-03-10 — 70/72 first attempt, retry needed
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class RefundRepository {

        static String sessionId;

        @BeforeAll
        static void createSession() {
            String vb = loadFixture("refund-repository/OrderProcessor.vb");
            String iface = loadFixture("refund-repository/IRefundRepository.cs");
            String tests = loadFixture("refund-repository/RefundRepositoryTests.cs");

            MigrationSession session = sessionStore.create();
            sessionId = session.getSessionId();
            session.setVbContent(vb);
            session.setInterfaceResult(new InterfaceResult(
                    sessionId, "RefundRepository", "IRefundRepository", iface));
            session.setTestsResult(new TestsResult(
                    sessionId, "RefundRepository", "RefundRepositoryTests", tests, 72));
        }

        @Test
        @Order(1)
        @DisplayName("RefundRepository: stub → RED (72/72 fail)")
        void stubBuild_allTestsFail() {
            StubResult stub = generationService.generateStub(sessionId, "RefundRepository");
            assertThat(stub.code()).contains("NotImplementedException");
            assertThat(stub.code()).contains("class RefundRepository");
            assertThat(stub.code()).contains("IRefundRepository");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== RefundRepository Stub: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");

            assertThat(result.buildStatus()).isEqualTo(BuildStatus.RED);
            assertThat(result.total()).isEqualTo(72);
            assertThat(result.failed()).isEqualTo(72);
        }

        @Test
        @Order(2)
        @DisplayName("RefundRepository: Claude implement → at least 65/72 pass")
        void claudeImplementation_mostTestsPass() {
            ImplementResult impl = generationService.implement(
                    sessionId, "RefundRepository", ImplementMode.CLAUDE);
            assertThat(impl.code()).contains("class RefundRepository");
            assertThat(impl.code()).contains("IRefundRepository");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== RefundRepository Impl: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");
            if (!result.failedTests().isEmpty()) System.out.println("  Failed: " + result.failedTests());
            if (!result.errors().isEmpty()) System.out.println("  Errors: " + result.errors());

            assertThat(result.buildStatus()).isNotEqualTo(BuildStatus.ERROR);
            assertThat(result.passed()).isGreaterThanOrEqualTo(65);
        }

        @Test
        @Order(3)
        @DisplayName("RefundRepository: retry → GREEN (72/72 pass)")
        void retryIfNeeded_allTestsPass() {
            BuildResult result = retryUntilGreen(sessionId, "RefundRepository", 72);
            assertThat(result.buildStatus()).isEqualTo(BuildStatus.GREEN);
            assertThat(result.passed()).isEqualTo(72);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  OrderLogService (76 tests)
    //  Captured from live run 2026-03-10 — shipping boundary fixture bug fixed
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class OrderLogService {

        static String sessionId;

        @BeforeAll
        static void createSession() {
            String vb = loadFixture("order-log-service/OrderProcessor.vb");
            String iface = loadFixture("order-log-service/IOrderLogService.cs");
            String tests = loadFixture("order-log-service/OrderLogServiceTests.cs");

            MigrationSession session = sessionStore.create();
            sessionId = session.getSessionId();
            session.setVbContent(vb);
            session.setInterfaceResult(new InterfaceResult(
                    sessionId, "OrderLogService", "IOrderLogService", iface));
            session.setTestsResult(new TestsResult(
                    sessionId, "OrderLogService", "OrderLogServiceTests", tests, 76));
        }

        @Test
        @Order(1)
        @DisplayName("OrderLogService: stub → RED (76/76 fail)")
        void stubBuild_allTestsFail() {
            StubResult stub = generationService.generateStub(sessionId, "OrderLogService");
            assertThat(stub.code()).contains("NotImplementedException");
            assertThat(stub.code()).contains("class OrderLogService");
            assertThat(stub.code()).contains("IOrderLogService");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== OrderLogService Stub: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");

            assertThat(result.buildStatus()).isEqualTo(BuildStatus.RED);
            assertThat(result.total()).isEqualTo(76);
            assertThat(result.failed()).isEqualTo(76);
        }

        @Test
        @Order(2)
        @DisplayName("OrderLogService: Claude implement → at least 68/76 pass")
        void claudeImplementation_mostTestsPass() {
            ImplementResult impl = generationService.implement(
                    sessionId, "OrderLogService", ImplementMode.CLAUDE);
            assertThat(impl.code()).contains("class OrderLogService");
            assertThat(impl.code()).contains("IOrderLogService");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== OrderLogService Impl: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");
            if (!result.failedTests().isEmpty()) System.out.println("  Failed: " + result.failedTests());
            if (!result.errors().isEmpty()) System.out.println("  Errors: " + result.errors());

            assertThat(result.buildStatus()).isNotEqualTo(BuildStatus.ERROR);
            assertThat(result.passed()).isGreaterThanOrEqualTo(68);
        }

        @Test
        @Order(3)
        @DisplayName("OrderLogService: retry → GREEN (76/76 pass)")
        void retryIfNeeded_allTestsPass() {
            BuildResult result = retryUntilGreen(sessionId, "OrderLogService", 76);
            assertThat(result.buildStatus()).isEqualTo(BuildStatus.GREEN);
            assertThat(result.passed()).isEqualTo(76);
        }
    }
}
