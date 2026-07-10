package com.vbgone.service;

import com.anthropic.models.messages.Model;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vbgone.ai.AiProviderRegistry;
import com.vbgone.ai.anthropic.AnthropicProvider;
import com.vbgone.ai.github.GitHubModelsProvider;
import com.vbgone.lang.CSharpConventions;
import com.vbgone.lang.JavaConventions;
import com.vbgone.lang.LanguageConventionsRegistry;
import com.vbgone.model.*;
import com.vbgone.prompt.CSharpPrompts;
import com.vbgone.prompt.JavaPrompts;
import com.vbgone.prompt.PromptLanguageRegistry;
import com.vbgone.session.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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
        AnthropicProvider anthropicProvider = new AnthropicProvider(claudeClient);
        GitHubModelsProvider copilotProvider = new GitHubModelsProvider("", new ObjectMapper());
        AiProviderRegistry registry = new AiProviderRegistry(List.of(anthropicProvider, copilotProvider));
        AiCallSupport aiCallSupport = new AiCallSupport(registry);
        PromptLanguageRegistry promptRegistry =
                new PromptLanguageRegistry(List.of(new CSharpPrompts(), new JavaPrompts()));
        LanguageConventionsRegistry conventionsRegistry =
                new LanguageConventionsRegistry(List.of(new CSharpConventions(), new JavaConventions()));
        service = new GenerationService(aiCallSupport, sessionStore, promptRegistry, conventionsRegistry);
        // getOrThrow delegates to the (stubbed) get(); let the real method run over the mock.
        lenient().when(sessionStore.getOrThrow(anyString())).thenCallRealMethod();
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

    @Test
    void generateInterface_javaTarget_usesJavaNamingAndImplName() {
        MigrationSession session = sessionWithVb("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse("package com.vbgone.generated;\n\npublic interface Form1 { int add(int a, int b); }"));

        InterfaceResult result = service.generateInterface("s1", "Form1", null, "java", null);

        // Java interface drops the I prefix; impl gains Impl.
        assertThat(result.interfaceName()).isEqualTo("Form1");
        assertThat(result.implName()).isEqualTo("Form1Impl");
        assertThat(result.code()).contains("package com.vbgone.generated;");
        assertThat(session.getTargetLanguage()).isEqualTo("java");

        verify(claudeClient).sendWithCachedSystemPrompt(
                contains("VB.NET to Java migration expert"),
                contains("no 'I' prefix"),
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

    @Test
    void implement_claudeMode_throwsWhenResponseIsNotCode() {
        MigrationSession session = sessionWithInterface("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        // Model returns prose/analysis instead of a class — must not be persisted.
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse("I analysed Form1 and here is my reasoning, but no implementation."));

        assertThatThrownBy(() -> service.implement("s1", "Form1", ImplementMode.CLAUDE))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("did not return valid");
        assertThat(session.getImplementResult()).isNull();
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
                java.util.List.of("Add_ReturnsSum", "Subtract_ReturnsDiff"), 1);

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

        service.retryImplement("s1", "Form1", java.util.List.of("Add_ReturnsSum"), 1);

        verify(claudeClient).sendWithCachedSystemPrompt(
                eq(GenerationService.IMPLEMENT_SYSTEM_PROMPT),
                argThat(msg -> msg.contains("Failing test source:") && msg.contains("Is.EqualTo(5)")),
                eq(Model.CLAUDE_SONNET_4_6),
                eq(16384L));
    }

    @Test
    void retryImplement_escalatesToOpusOnFinalAttempt() {
        MigrationSession session = sessionWithInterface("s1");
        session.setImplementResult(new ImplementResult(
                "s1", "Form1", "public class Form1 : IForm1 { /* broken */ }", ImplementMode.CLAUDE));
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse("public class Form1 : IForm1 { public int Add(int a, int b) => a + b; }"));

        // attempt >= 3 escalates from Sonnet (IMPLEMENTATION) to Opus (ESCALATION).
        service.retryImplement("s1", "Form1", java.util.List.of("Add_ReturnsSum"), 3);

        verify(claudeClient).sendWithCachedSystemPrompt(
                eq(GenerationService.IMPLEMENT_SYSTEM_PROMPT),
                anyString(),
                eq(Model.CLAUDE_OPUS_4_6),
                eq(16384L));
    }

    @Test
    void retryImplement_staysOnSonnetBeforeFinalAttempt() {
        MigrationSession session = sessionWithInterface("s1");
        session.setImplementResult(new ImplementResult(
                "s1", "Form1", "public class Form1 : IForm1 { /* broken */ }", ImplementMode.CLAUDE));
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse("public class Form1 : IForm1 { public int Add(int a, int b) => a + b; }"));

        // Boundary: attempt 2 is still below the escalation threshold, so Sonnet.
        service.retryImplement("s1", "Form1", java.util.List.of("Add_ReturnsSum"), 2);

        verify(claudeClient).sendWithCachedSystemPrompt(
                eq(GenerationService.IMPLEMENT_SYSTEM_PROMPT),
                anyString(),
                eq(Model.CLAUDE_SONNET_4_6),
                eq(16384L));
    }

    @Test
    void retryImplement_fallsBackToPreviousCodeWhenResponseIsNotCode() {
        MigrationSession session = sessionWithInterface("s1");
        ImplementResult previous = new ImplementResult(
                "s1", "Form1", "public class Form1 : IForm1 { public int Add(int a, int b) => a + b; }",
                ImplementMode.CLAUDE);
        session.setImplementResult(previous);
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse("Sorry, I could not fix the tests — here is my analysis instead."));

        ImplementResult result = service.retryImplement("s1", "Form1", java.util.List.of("Add_ReturnsSum"), 1);

        // Unlike implement(), retry does NOT throw on non-code — it keeps the previous code.
        assertThat(result.code()).isEqualTo(previous.code());
        assertThat(result.mode()).isEqualTo(ImplementMode.CLAUDE);
        assertThat(session.getImplementResult().code()).isEqualTo(previous.code());
    }

    @Test
    void retryImplement_throwsWhenNoInterface() {
        MigrationSession session = sessionWithVb("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.retryImplement("s1", "Form1", java.util.List.of(), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Interface must be generated");
    }

    @Test
    void retryImplement_throwsWhenNoPreviousImplementation() {
        MigrationSession session = sessionWithInterface("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.retryImplement("s1", "Form1", java.util.List.of(), 1))
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

    // ── extractVbClass ──

    @Test
    void extractVbClass_extractsSingleClassFromMultiClassFile() {
        String vb = """
                Public Class OrderCalculatorService
                    Public Function CalculateTotal() As Double
                        Return 0
                    End Function
                End Class

                Public Class OrderValidator
                    Public Function ValidateOrder() As Boolean
                        Return True
                    End Function
                End Class""";

        String extracted = MigrationSession.extractVbClass(vb, "OrderValidator");

        assertThat(extracted).contains("Public Class OrderValidator");
        assertThat(extracted).contains("ValidateOrder");
        assertThat(extracted).contains("End Class");
        assertThat(extracted).doesNotContain("OrderCalculatorService");
        assertThat(extracted).doesNotContain("CalculateTotal");
    }

    @Test
    void extractVbClass_returnsNullWhenClassNotFound() {
        String vb = "Public Class Foo\nEnd Class";
        assertThat(MigrationSession.extractVbClass(vb, "Bar")).isNull();
    }

    @Test
    void extractVbClass_handlesClassWithNoAccessModifier() {
        String vb = "Class MyClass\n    Sub DoStuff()\n    End Sub\nEnd Class";
        String extracted = MigrationSession.extractVbClass(vb, "MyClass");
        assertThat(extracted).contains("Class MyClass");
        assertThat(extracted).contains("DoStuff");
    }

    @Test
    void getVbContentForClass_extractsClassFromFullContent() {
        MigrationSession session = new MigrationSession("s1");
        session.setVbContent("""
                Public Class Alpha
                    Sub A()
                    End Sub
                End Class

                Public Class Beta
                    Sub B()
                    End Sub
                End Class""");

        String content = session.getVbContentForClass("Beta");

        assertThat(content).contains("Public Class Beta");
        assertThat(content).contains("Sub B()");
        assertThat(content).doesNotContain("Alpha");
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
