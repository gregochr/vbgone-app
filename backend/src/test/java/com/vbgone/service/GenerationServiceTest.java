package com.vbgone.service;

import com.anthropic.models.messages.Model;
import com.vbgone.model.*;
import com.vbgone.session.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenerationServiceTest {

    @Mock
    private ClaudeClient claudeClient;

    @Mock
    private SessionStore sessionStore;

    private GenerationService service;

    @BeforeEach
    void setUp() {
        service = new GenerationService(claudeClient, sessionStore);
    }

    private ClaudeClient.ClaudeResponse claudeResponse(String text) {
        return new ClaudeClient.ClaudeResponse(text, 100, 50);
    }

    private MigrationSession sessionWithVb(String sessionId) {
        MigrationSession session = new MigrationSession(sessionId);
        session.setVbContent("Public Class Form1...");
        return session;
    }

    private MigrationSession sessionWithInterface(String sessionId) {
        MigrationSession session = sessionWithVb(sessionId);
        session.setInterfaceResult(new InterfaceResult(
                sessionId, "Form1", "IForm1", "public interface IForm1 { int Add(int a, int b); }"));
        return session;
    }

    private MigrationSession sessionWithStub(String sessionId) {
        MigrationSession session = sessionWithInterface(sessionId);
        session.setStubResult(new StubResult(
                sessionId, "Form1", "public class Form1 : IForm1 { public int Add(int a, int b) => throw new NotImplementedException(); }"));
        return session;
    }

    // ── generateInterface ──

    @Test
    void generateInterface_callsHaikuAndReturnsResult() {
        MigrationSession session = sessionWithVb("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse("public interface IForm1 { int Add(int a, int b); }"));

        InterfaceResult result = service.generateInterface("s1", "Form1");

        assertThat(result.sessionId()).isEqualTo("s1");
        assertThat(result.className()).isEqualTo("Form1");
        assertThat(result.interfaceName()).isEqualTo("IForm1");
        assertThat(result.code()).contains("IForm1");
        assertThat(session.getInterfaceResult()).isEqualTo(result);

        verify(claudeClient).sendWithCachedSystemPrompt(
                eq(GenerationService.INTERFACE_SYSTEM_PROMPT),
                contains("IForm1"),
                eq(Model.CLAUDE_HAIKU_4_5),
                eq(4096L));
    }

    @Test
    void generateInterface_throwsOnMissingSession() {
        when(sessionStore.get("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateInterface("bad", "Form1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session not found");
    }

    @Test
    void generateInterface_usesClassSpecificSourceWhenAvailable() {
        MigrationSession session = new MigrationSession("s1");
        session.setVbContent("Public Class Foo...\nPublic Class Bar...");
        session.putClassSource("Foo", "Public Class Foo...");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse("public interface IFoo { }"));

        service.generateInterface("s1", "Foo");

        // Should send only Foo's source, not the combined content
        verify(claudeClient).sendWithCachedSystemPrompt(
                eq(GenerationService.INTERFACE_SYSTEM_PROMPT),
                argThat(msg -> msg.contains("Public Class Foo...") && !msg.contains("Public Class Bar...")),
                eq(Model.CLAUDE_HAIKU_4_5),
                eq(4096L));
    }

    @Test
    void generateInterface_fallsBackToFullContentWhenNoClassSource() {
        MigrationSession session = new MigrationSession("s1");
        session.setVbContent("Public Class Form1...");
        // No classSources set — single file mode
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse("public interface IForm1 { }"));

        service.generateInterface("s1", "Form1");

        verify(claudeClient).sendWithCachedSystemPrompt(
                eq(GenerationService.INTERFACE_SYSTEM_PROMPT),
                contains("Public Class Form1..."),
                eq(Model.CLAUDE_HAIKU_4_5),
                eq(4096L));
    }

    // ── generateTests ──

    @Test
    void generateTests_callsSonnetAndCountsTests() {
        MigrationSession session = sessionWithVb("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        String testCode = """
                [TestFixture]
                public class Form1Tests
                {
                    [Test]
                    public void Add_ReturnsSum() { }

                    [TestCase(1, 2, ExpectedResult = 3)]
                    [TestCase(0, 0, ExpectedResult = 0)]
                    public int Add_WithTestCases(int a, int b) { return 0; }

                    [Test]
                    public void Subtract_ReturnsDifference() { }
                }""";
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse(testCode));

        TestsResult result = service.generateTests("s1", "Form1");

        assertThat(result.sessionId()).isEqualTo("s1");
        assertThat(result.className()).isEqualTo("Form1");
        assertThat(result.testClassName()).isEqualTo("Form1Tests");
        assertThat(result.testCount()).isEqualTo(4);
        assertThat(session.getTestsResult()).isEqualTo(result);

        verify(claudeClient).sendWithCachedSystemPrompt(
                eq(GenerationService.TESTS_SYSTEM_PROMPT),
                contains("IForm1"),
                eq(Model.CLAUDE_SONNET_4_6),
                eq(16384L));
    }

    // ── generateStub ──

    @Test
    void generateStub_callsHaikuWithInterfaceCode() {
        MigrationSession session = sessionWithInterface("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse("public class Form1 : IForm1 { }"));

        StubResult result = service.generateStub("s1", "Form1");

        assertThat(result.sessionId()).isEqualTo("s1");
        assertThat(result.className()).isEqualTo("Form1");
        assertThat(result.code()).contains("Form1");
        assertThat(session.getStubResult()).isEqualTo(result);

        verify(claudeClient).sendWithCachedSystemPrompt(
                eq(GenerationService.STUB_SYSTEM_PROMPT),
                contains("IForm1"),
                eq(Model.CLAUDE_HAIKU_4_5),
                eq(4096L));
    }

    @Test
    void generateStub_throwsWhenNoInterface() {
        MigrationSession session = sessionWithVb("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.generateStub("s1", "Form1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Interface must be generated");
    }

    // ── implement (CLAUDE mode) ──

    @Test
    void implement_claudeMode_callsSonnetAndReturnsResult() {
        MigrationSession session = sessionWithInterface("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse("public class Form1 : IForm1 { public int Add(int a, int b) => a + b; }"));

        ImplementResult result = service.implement("s1", "Form1", ImplementMode.CLAUDE);

        assertThat(result.sessionId()).isEqualTo("s1");
        assertThat(result.className()).isEqualTo("Form1");
        assertThat(result.mode()).isEqualTo(ImplementMode.CLAUDE);
        assertThat(result.code()).contains("a + b");
        assertThat(session.getImplementResult()).isEqualTo(result);

        verify(claudeClient).sendWithCachedSystemPrompt(
                eq(GenerationService.IMPLEMENT_SYSTEM_PROMPT),
                contains("IForm1"),
                eq(Model.CLAUDE_SONNET_4_6),
                eq(16384L));
    }

    // ── implement (STUB mode) ──

    @Test
    void implement_stubMode_returnsExistingStubWithoutCallingClaude() {
        MigrationSession session = sessionWithStub("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));

        ImplementResult result = service.implement("s1", "Form1", ImplementMode.STUB);

        assertThat(result.sessionId()).isEqualTo("s1");
        assertThat(result.mode()).isEqualTo(ImplementMode.STUB);
        assertThat(result.code()).contains("NotImplementedException");
        assertThat(session.getImplementResult()).isEqualTo(result);

        verifyNoInteractions(claudeClient);
    }

    @Test
    void implement_stubMode_throwsWhenNoStub() {
        MigrationSession session = sessionWithInterface("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.implement("s1", "Form1", ImplementMode.STUB))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Stub must be generated");
    }

    // ── retryImplement ──

    @Test
    void retryImplement_callsSonnetWithFailingTestsAndPreviousCode() {
        MigrationSession session = sessionWithInterface("s1");
        session.setImplementResult(new ImplementResult(
                "s1", "Form1", "public class Form1 : IForm1 { /* broken */ }", ImplementMode.CLAUDE));
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse("public class Form1 : IForm1 { public int Add(int a, int b) => a + b; }"));

        ImplementResult result = service.retryImplement("s1", "Form1",
                java.util.List.of("Add_ReturnsSum", "Subtract_ReturnsDiff"));

        assertThat(result.sessionId()).isEqualTo("s1");
        assertThat(result.className()).isEqualTo("Form1");
        assertThat(result.mode()).isEqualTo(ImplementMode.CLAUDE);
        assertThat(result.code()).contains("a + b");
        assertThat(session.getImplementResult()).isEqualTo(result);

        verify(claudeClient).sendWithCachedSystemPrompt(
                eq(GenerationService.IMPLEMENT_SYSTEM_PROMPT),
                argThat(msg -> msg.contains("Add_ReturnsSum") && msg.contains("broken")),
                eq(Model.CLAUDE_SONNET_4_6),
                eq(16384L));
    }

    @Test
    void retryImplement_includesFailingTestSourceInPrompt() {
        MigrationSession session = sessionWithInterface("s1");
        session.setImplementResult(new ImplementResult(
                "s1", "Form1", "public class Form1 : IForm1 { }", ImplementMode.CLAUDE));
        session.setTestsResult(new TestsResult("s1", "Form1", "Form1Tests", """
                [TestFixture]
                public class Form1Tests
                {
                    [Test]
                    public void Add_ReturnsSum()
                    {
                        Assert.That(_sut.Add(2, 3), Is.EqualTo(5));
                    }
                }""", 1));
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse("public class Form1 : IForm1 { public int Add(int a, int b) => a + b; }"));

        service.retryImplement("s1", "Form1", java.util.List.of("Add_ReturnsSum"));

        verify(claudeClient).sendWithCachedSystemPrompt(
                eq(GenerationService.IMPLEMENT_SYSTEM_PROMPT),
                argThat(msg -> msg.contains("Failing test source:") && msg.contains("Is.EqualTo(5)")),
                eq(Model.CLAUDE_SONNET_4_6),
                eq(16384L));
    }

    @Test
    void retryImplement_throwsWhenNoInterface() {
        MigrationSession session = sessionWithVb("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.retryImplement("s1", "Form1", java.util.List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Interface must be generated");
    }

    @Test
    void retryImplement_throwsWhenNoPreviousImplementation() {
        MigrationSession session = sessionWithInterface("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.retryImplement("s1", "Form1", java.util.List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Previous implementation must exist");
    }

    // ── stripCodeFences ──

    @Test
    void generateInterface_stripsCodeFences() {
        MigrationSession session = sessionWithVb("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse("```csharp\npublic interface IForm1 { }\n```"));

        InterfaceResult result = service.generateInterface("s1", "Form1");

        assertThat(result.code()).doesNotContain("```");
        assertThat(result.code()).contains("IForm1");
    }

    // ── countTests ──

    @Test
    void countTests_countsTestAndTestCaseAttributes() {
        String code = """
                [Test]
                public void A() { }
                [TestCase(1)]
                [TestCase(2)]
                public int B(int x) { return x; }
                [Test]
                public void C() { }
                """;

        assertThat(service.countTests(code)).isEqualTo(4);
    }

    // ── extractFailingTests ──

    @Test
    void extractFailingTests_extractsMatchingTestMethod() {
        MigrationSession session = sessionWithInterface("s1");
        session.setTestsResult(new TestsResult("s1", "Form1", "Form1Tests", """
                [TestFixture]
                public class Form1Tests
                {
                    [Test]
                    public void Add_ReturnsSum()
                    {
                        Assert.That(_sut.Add(2, 3), Is.EqualTo(5));
                    }

                    [Test]
                    public void Subtract_ReturnsDiff()
                    {
                        Assert.That(_sut.Subtract(5, 3), Is.EqualTo(2));
                    }
                }""", 2));

        String result = service.extractFailingTests(session, java.util.List.of("Subtract_ReturnsDiff"));

        assertThat(result).contains("Subtract_ReturnsDiff");
        assertThat(result).contains("Is.EqualTo(2)");
        assertThat(result).doesNotContain("Add_ReturnsSum");
    }

    @Test
    void extractFailingTests_returnsEmptyWhenNoTests() {
        MigrationSession session = sessionWithInterface("s1");
        String result = service.extractFailingTests(session, java.util.List.of("Foo"));
        assertThat(result).isEmpty();
    }

    // ── repairTruncatedCSharp ──

    @Test
    void repairTruncatedCSharp_closesUnclosedBracesAndRemovesIncompleteMethod() {
        String truncated = """
                [TestFixture]
                public class FooTests
                {
                    [Test]
                    public void A() { Assert.Pass(); }

                    [Test]
                    public void B()
                    {
                        Assert.That(_s""";

        String repaired = service.repairTruncatedCSharp(truncated);

        // The incomplete method B should be trimmed and braces closed
        assertThat(repaired).doesNotContain("Assert.That(_s");
        assertThat(repaired).endsWith("}");
        // Count braces — should be balanced
        long opens = repaired.chars().filter(c -> c == '{').count();
        long closes = repaired.chars().filter(c -> c == '}').count();
        assertThat(opens).isEqualTo(closes);
    }

    @Test
    void repairTruncatedCSharp_handlesRealTruncatedTestFile() {
        // Actual truncated output from production — Claude hit 8192 token limit
        String truncated = """
                using NUnit.Framework;

                [TestFixture]
                public class OrderConfigurationTests
                {
                    private IOrderConfiguration _sut;

                    [SetUp]
                    public void SetUp()
                    {
                        _sut = new OrderConfiguration();
                    }

                    [Test]
                    public void CalculateTotal_SmallOrder_ReturnsCorrectTotal()
                    {
                        double result = _sut.CalculateTotal(10.0, 5);
                        Assert.That(result, Is.EqualTo(60.58).Within(0.01));
                    }

                    [Test]
                    public void GetDiscountTier_ReturnsStringNotNull_ForAllTiers()
                    {
                        Assert.That(_s""";

        String repaired = service.repairTruncatedCSharp(truncated);

        // Should compile — incomplete method removed, braces balanced
        assertThat(repaired).doesNotContain("Assert.That(_s");
        assertThat(repaired).contains("CalculateTotal_SmallOrder_ReturnsCorrectTotal");
        long opens = repaired.chars().filter(c -> c == '{').count();
        long closes = repaired.chars().filter(c -> c == '}').count();
        assertThat(opens).isEqualTo(closes);
    }

    @Test
    void repairTruncatedCSharp_leavesBalancedCodeUntouched() {
        String balanced = "[TestFixture]\npublic class FooTests\n{\n    [Test]\n    public void A() { Assert.Pass(); }\n}";
        assertThat(service.repairTruncatedCSharp(balanced)).isEqualTo(balanced);
    }

    // ── stripNamespaceWrapper ──

    @Test
    void stripNamespaceWrapper_removesBlockNamespace() {
        String code = "namespace Foo\n{\n    public interface IFoo { }\n}";
        assertThat(service.stripNamespaceWrapper(code)).isEqualTo("public interface IFoo { }");
    }

    @Test
    void stripNamespaceWrapper_removesFileScopedNamespace() {
        String code = "namespace Foo;\n\npublic interface IFoo { }";
        assertThat(service.stripNamespaceWrapper(code)).isEqualTo("public interface IFoo { }");
    }

    @Test
    void stripNamespaceWrapper_leavesCodeWithoutNamespaceUntouched() {
        String code = "public interface IFoo { }";
        assertThat(service.stripNamespaceWrapper(code)).isEqualTo("public interface IFoo { }");
    }

    @Test
    void generateInterface_stripsNamespaceFromResponse() {
        MigrationSession session = sessionWithVb("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse("namespace VBGone;\n\npublic interface IForm1 { int Add(int a, int b); }"));

        InterfaceResult result = service.generateInterface("s1", "Form1");

        assertThat(result.code()).doesNotContain("namespace");
        assertThat(result.code()).contains("IForm1");
    }

    // ── fixClassDeclaration ──

    @Test
    void fixClassDeclaration_correctsWrongClassAndInterface() {
        String code = "public class OrderCalculationService : IOrderCalculationService\n{\n    // body\n}";
        String fixed = service.fixClassDeclaration(code, "OrderConstants", "IOrderConstants");
        assertThat(fixed).startsWith("public class OrderConstants : IOrderConstants");
        assertThat(fixed).contains("// body");
    }

    @Test
    void fixClassDeclaration_leavesCorrectCodeUntouched() {
        String code = "public class OrderConstants : IOrderConstants\n{\n    // body\n}";
        String fixed = service.fixClassDeclaration(code, "OrderConstants", "IOrderConstants");
        assertThat(fixed).isEqualTo(code);
    }

    @Test
    void fixClassDeclaration_handlesExtraWhitespace() {
        String code = "public  class  Foo  :  IBar\n{\n}";
        String fixed = service.fixClassDeclaration(code, "MyClass", "IMyClass");
        assertThat(fixed).contains("public class MyClass : IMyClass");
    }

    @Test
    void implement_fixesWrongClassNameFromClaude() {
        MigrationSession session = sessionWithVb("s1");
        session.setInterfaceResult(new InterfaceResult("s1", "OrderConstants", "IOrderConstants",
                "public interface IOrderConstants { double CalculateTotal(double a, int q); }"));
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        // Claude returns the wrong class name
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse("public class OrderCalculationService : IOrderCalculationService\n{\n    public double CalculateTotal(double a, int q) => a * q;\n}"));

        ImplementResult result = service.implement("s1", "OrderConstants", ImplementMode.CLAUDE);

        assertThat(result.code()).contains("public class OrderConstants : IOrderConstants");
        assertThat(result.code()).doesNotContain("OrderCalculationService");
        assertThat(result.code()).doesNotContain("IOrderCalculationService");
    }

    // ── Token tracking ──

    @Test
    void generateInterface_tracksTokenUsage() {
        MigrationSession session = sessionWithVb("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(new ClaudeClient.ClaudeResponse("public interface IForm1 {}", 150, 75));

        service.generateInterface("s1", "Form1");

        assertThat(session.getTokenUsages()).hasSize(1);
        assertThat(session.getTokenUsages().get(0).step()).isEqualTo("interface");
        assertThat(session.getTokenUsages().get(0).inputTokens()).isEqualTo(150);
        assertThat(session.getTokenUsages().get(0).outputTokens()).isEqualTo(75);
    }
}
