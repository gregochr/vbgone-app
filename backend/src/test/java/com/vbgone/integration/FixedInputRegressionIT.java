package com.vbgone.integration;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
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

        String workspace = tempDir.toString();
        String containerName = "vbgone-app-dotnet-runner-1";
        ProcessRunner processRunner = new DockerProcessRunner(containerName, tempDir);

        generationService = new GenerationService(claudeClient, sessionStore);
        buildService = new BuildService(sessionStore, workspace, containerName, processRunner);
    }

    static String loadFixture(String path) {
        try (InputStream is = FixedInputRegressionIT.class.getResourceAsStream("/fixtures/" + path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
            MigrationSession session = sessionStore.get(sessionId).orElseThrow();
            BuildResult greenBuild = session.getGreenBuild();
            if (greenBuild != null && greenBuild.buildStatus() == BuildStatus.GREEN) {
                System.out.println("=== OrderValidator already GREEN — skip retry ===");
                assertThat(greenBuild.passed()).isEqualTo(57);
                return;
            }

            BuildResult redBuild = session.getRedBuild();
            assertThat(redBuild).isNotNull();
            List<String> failingTests = redBuild.failedTests();
            System.out.println("=== OrderValidator retrying " + failingTests.size() + " failing tests ===");

            ImplementResult retry = generationService.retryImplement(
                    sessionId, "OrderValidator", failingTests, 2);
            assertThat(retry.code()).contains("class OrderValidator");
            assertThat(retry.code()).contains("IOrderValidator");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== OrderValidator Retry: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");
            if (!result.failedTests().isEmpty()) System.out.println("  Failed: " + result.failedTests());

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
            MigrationSession session = sessionStore.get(sessionId).orElseThrow();
            BuildResult greenBuild = session.getGreenBuild();
            if (greenBuild != null && greenBuild.buildStatus() == BuildStatus.GREEN) {
                System.out.println("=== OrderCalculatorService already GREEN — skip retry ===");
                assertThat(greenBuild.passed()).isEqualTo(66);
                return;
            }

            BuildResult redBuild = session.getRedBuild();
            assertThat(redBuild).isNotNull();
            List<String> failingTests = redBuild.failedTests();
            System.out.println("=== OrderCalculatorService retrying " + failingTests.size() + " failing tests ===");

            ImplementResult retry = generationService.retryImplement(
                    sessionId, "OrderCalculatorService", failingTests, 2);
            assertThat(retry.code()).contains("class OrderCalculatorService");
            assertThat(retry.code()).contains("IOrderCalculatorService");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== OrderCalculatorService Retry: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");
            if (!result.failedTests().isEmpty()) System.out.println("  Failed: " + result.failedTests());

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
            MigrationSession session = sessionStore.get(sessionId).orElseThrow();
            BuildResult greenBuild = session.getGreenBuild();
            if (greenBuild != null && greenBuild.buildStatus() == BuildStatus.GREEN) {
                System.out.println("=== OrderCalculator already GREEN — skip retry ===");
                assertThat(greenBuild.passed()).isEqualTo(78);
                return;
            }

            BuildResult redBuild = session.getRedBuild();
            assertThat(redBuild).isNotNull();
            List<String> failingTests = redBuild.failedTests();
            System.out.println("=== OrderCalculator retrying " + failingTests.size() + " failing tests ===");

            ImplementResult retry = generationService.retryImplement(
                    sessionId, "OrderCalculator", failingTests, 2);
            assertThat(retry.code()).contains("class OrderCalculator");
            assertThat(retry.code()).contains("IOrderCalculator");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== OrderCalculator Retry: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");
            if (!result.failedTests().isEmpty()) System.out.println("  Failed: " + result.failedTests());

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
            MigrationSession session = sessionStore.get(sessionId).orElseThrow();
            BuildResult greenBuild = session.getGreenBuild();
            if (greenBuild != null && greenBuild.buildStatus() == BuildStatus.GREEN) {
                System.out.println("=== OrderCalculatorService v2 already GREEN — skip retry ===");
                assertThat(greenBuild.passed()).isEqualTo(50);
                return;
            }

            BuildResult redBuild = session.getRedBuild();
            assertThat(redBuild).isNotNull();
            List<String> failingTests = redBuild.failedTests();
            System.out.println("=== OrderCalculatorService v2 retrying " + failingTests.size() + " failing tests ===");

            ImplementResult retry = generationService.retryImplement(
                    sessionId, "OrderCalculatorService", failingTests, 2);
            assertThat(retry.code()).contains("class OrderCalculatorService");
            assertThat(retry.code()).contains("IOrderCalculatorService");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== OrderCalculatorService v2 Retry: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");
            if (!result.failedTests().isEmpty()) System.out.println("  Failed: " + result.failedTests());

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
            MigrationSession session = sessionStore.get(sessionId).orElseThrow();
            BuildResult greenBuild = session.getGreenBuild();
            if (greenBuild != null && greenBuild.buildStatus() == BuildStatus.GREEN) {
                System.out.println("=== OrderValidator v2 already GREEN — skip retry ===");
                assertThat(greenBuild.passed()).isEqualTo(62);
                return;
            }

            BuildResult redBuild = session.getRedBuild();
            assertThat(redBuild).isNotNull();
            List<String> failingTests = redBuild.failedTests();
            System.out.println("=== OrderValidator v2 retrying " + failingTests.size() + " failing tests ===");

            ImplementResult retry = generationService.retryImplement(
                    sessionId, "OrderValidator", failingTests, 2);
            assertThat(retry.code()).contains("class OrderValidator");
            assertThat(retry.code()).contains("IOrderValidator");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== OrderValidator v2 Retry: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");
            if (!result.failedTests().isEmpty()) System.out.println("  Failed: " + result.failedTests());

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
            MigrationSession session = sessionStore.get(sessionId).orElseThrow();
            BuildResult greenBuild = session.getGreenBuild();
            if (greenBuild != null && greenBuild.buildStatus() == BuildStatus.GREEN) {
                System.out.println("=== OrderNotificationService already GREEN — skip retry ===");
                assertThat(greenBuild.passed()).isEqualTo(74);
                return;
            }

            BuildResult redBuild = session.getRedBuild();
            assertThat(redBuild).isNotNull();
            List<String> failingTests = redBuild.failedTests();
            System.out.println("=== OrderNotificationService retrying " + failingTests.size() + " failing tests ===");

            ImplementResult retry = generationService.retryImplement(
                    sessionId, "OrderNotificationService", failingTests, 2);
            assertThat(retry.code()).contains("class OrderNotificationService");
            assertThat(retry.code()).contains("IOrderNotificationService");

            BuildResult result = buildService.build(sessionId);
            System.out.println("=== OrderNotificationService Retry: " + result.buildStatus()
                    + " " + result.passed() + "/" + result.total() + " ===");
            if (!result.failedTests().isEmpty()) System.out.println("  Failed: " + result.failedTests());

            assertThat(result.buildStatus()).isEqualTo(BuildStatus.GREEN);
            assertThat(result.passed()).isEqualTo(74);
        }
    }
}
