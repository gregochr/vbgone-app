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
                .thenReturn("public interface IForm1 { int Add(int a, int b); }");

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
                .thenReturn(testCode);

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
                eq(8192L));
    }

    // ── generateStub ──

    @Test
    void generateStub_callsHaikuWithInterfaceCode() {
        MigrationSession session = sessionWithInterface("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn("public class Form1 : IForm1 { }");

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
                .thenReturn("public class Form1 : IForm1 { public int Add(int a, int b) => a + b; }");

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
                eq(8192L));
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

    // ── stripCodeFences ──

    @Test
    void generateInterface_stripsCodeFences() {
        MigrationSession session = sessionWithVb("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn("```csharp\npublic interface IForm1 { }\n```");

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
}
