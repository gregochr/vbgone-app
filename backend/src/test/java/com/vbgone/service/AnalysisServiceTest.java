package com.vbgone.service;

import com.anthropic.models.messages.Model;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vbgone.ai.AiProviderRegistry;
import com.vbgone.ai.anthropic.AnthropicProvider;
import com.vbgone.ai.github.GitHubModelsProvider;
import com.vbgone.model.*;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    @Mock
    private ClaudeClient claudeClient;

    @Mock
    private SessionStore sessionStore;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AnalysisService analysisService;

    private static final String CLAUDE_JSON_RESPONSE = """
            {
              "classes": [{
                "name": "Form1",
                "methods": ["Add", "Subtract", "Multiply"],
                "dependencies": [],
                "complexity": "LOW",
                "codeQuality": "FAIR",
                "codeSmells": ["Mixed concerns \u2014 UI logic mixed with business logic"],
                "refactoringSuggestions": ["Extract arithmetic operations into a separate service class"],
                "vbAntiPatterns": ["Implicit type conversions via Int()"]
              }],
              "suggestedMigrationOrder": ["Form1"],
              "summary": "One class found with 3 arithmetic methods."
            }""";

    @BeforeEach
    void setUp() {
        AnthropicProvider anthropicProvider = new AnthropicProvider(claudeClient);
        GitHubModelsProvider copilotProvider = new GitHubModelsProvider("", objectMapper);
        AiProviderRegistry registry = new AiProviderRegistry(List.of(anthropicProvider, copilotProvider));
        AiCallSupport aiCallSupport = new AiCallSupport(registry);
        analysisService = new AnalysisService(aiCallSupport, sessionStore, objectMapper);
    }

    private ClaudeClient.ClaudeResponse claudeResponse(String text) {
        return new ClaudeClient.ClaudeResponse(text, 100, 50);
    }

    @Test
    void analyse_callsClaudeAndReturnsAnalysisResult() {
        MigrationSession session = new MigrationSession("test-session-123");
        when(sessionStore.create()).thenReturn(session);
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse(CLAUDE_JSON_RESPONSE));

        AnalysisResult result = analysisService.analyse("Form1.vb", "Public Class Form1...");

        assertThat(result.sessionId()).isEqualTo("test-session-123");
        assertThat(result.classes()).hasSize(1);
        assertThat(result.classes().get(0).name()).isEqualTo("Form1");
        assertThat(result.classes().get(0).methods()).containsExactly("Add", "Subtract", "Multiply");
        assertThat(result.classes().get(0).dependencies()).isEmpty();
        assertThat(result.classes().get(0).complexity()).isEqualTo(Complexity.LOW);
        assertThat(result.suggestedMigrationOrder()).containsExactly("Form1");
        assertThat(result.summary()).isEqualTo("One class found with 3 arithmetic methods.");
    }

    @Test
    void analyse_assureMode_usesForensicPromptAndParsesObservedBehaviour() {
        MigrationSession session = new MigrationSession("s-assure");
        when(sessionStore.create()).thenReturn(session);
        String assureJson = """
                {
                  "classes": [{
                    "name": "OrderProcessor",
                    "methods": ["SplitPerHead"],
                    "dependencies": [],
                    "complexity": "MEDIUM",
                    "codeQuality": "FAIR",
                    "codeSmells": ["Headcount read straight off a TextBox"],
                    "observedBehaviour": [{
                      "method": "SplitPerHead",
                      "cls": "OrderProcessor",
                      "rows": [
                        { "cond": "headcount = 0", "outcome": "throws DivideByZeroException", "kind": "throws" }
                      ]
                    }]
                  }],
                  "suggestedMigrationOrder": ["OrderProcessor"],
                  "summary": "Characterised against the live assemblies."
                }""";
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse(assureJson));

        AnalysisResult result = analysisService.analyse(
                "OrderProcessor.vb", "Public Class OrderProcessor...", null, null, null, "assure");

        // The forensic persona is used, not the migrate one.
        verify(claudeClient).sendWithCachedSystemPrompt(
                eq(AnalysisService.ASSURE_SYSTEM_PROMPT), anyString(), any(), anyLong());

        var observed = result.classes().get(0).observedBehaviour();
        assertThat(observed).hasSize(1);
        assertThat(observed.get(0).method()).isEqualTo("SplitPerHead");
        assertThat(observed.get(0).rows().get(0).kind()).isEqualTo("throws");
        assertThat(observed.get(0).rows().get(0).outcome()).contains("DivideByZeroException");
    }

    @Test
    void analyse_storesFilenameAndContentInSession() {
        MigrationSession session = new MigrationSession("test-session-123");
        when(sessionStore.create()).thenReturn(session);
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse(CLAUDE_JSON_RESPONSE));

        analysisService.analyse("Form1.vb", "Public Class Form1...");

        assertThat(session.getFilename()).isEqualTo("Form1.vb");
        assertThat(session.getVbContent()).isEqualTo("Public Class Form1...");
        assertThat(session.getAnalysisResult()).isNotNull();
        assertThat(session.getAnalysisResult().sessionId()).isEqualTo("test-session-123");
    }

    @Test
    void analyse_sendsVbContentAndSystemPromptToClaude() {
        MigrationSession session = new MigrationSession("test-session-123");
        when(sessionStore.create()).thenReturn(session);
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse(CLAUDE_JSON_RESPONSE));

        analysisService.analyse("Form1.vb", "Public Class Form1...");

        verify(claudeClient).sendWithCachedSystemPrompt(
                eq(AnalysisService.SYSTEM_PROMPT),
                eq("Public Class Form1..."),
                eq(Model.CLAUDE_SONNET_4_6),
                eq(4096L)
        );
    }

    @Test
    void analyse_stripsMarkdownFencesFromResponse() {
        MigrationSession session = new MigrationSession("test-session-123");
        when(sessionStore.create()).thenReturn(session);

        String wrappedResponse = "```json\n" + CLAUDE_JSON_RESPONSE + "\n```";
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse(wrappedResponse));

        AnalysisResult result = analysisService.analyse("Form1.vb", "Public Class Form1...");

        assertThat(result.classes()).hasSize(1);
        assertThat(result.classes().get(0).name()).isEqualTo("Form1");
    }

    @Test
    void analyse_throwsOnInvalidJson() {
        MigrationSession session = new MigrationSession("test-session-123");
        when(sessionStore.create()).thenReturn(session);
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse("not valid json"));

        assertThatThrownBy(() -> analysisService.analyse("Form1.vb", "..."))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse Claude response");
    }

    @Test
    void analyse_tracksTokenUsageInSession() {
        MigrationSession session = new MigrationSession("test-session-123");
        when(sessionStore.create()).thenReturn(session);
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(new ClaudeClient.ClaudeResponse(CLAUDE_JSON_RESPONSE, 200, 100));

        analysisService.analyse("Form1.vb", "Public Class Form1...");

        assertThat(session.getTokenUsages()).hasSize(1);
        assertThat(session.getTokenUsages().get(0).step()).isEqualTo("analyse");
        assertThat(session.getTokenUsages().get(0).inputTokens()).isEqualTo(200);
        assertThat(session.getTokenUsages().get(0).outputTokens()).isEqualTo(100);
        assertThat(session.getTokenUsages().get(0).cost()).isGreaterThan(0);
    }

    // ── analyseProject tests ──

    private static final String PROJECT_JSON_RESPONSE = """
            {
              "classes": [
                {
                  "name": "Calculator",
                  "methods": ["Add", "Subtract"],
                  "dependencies": [],
                  "complexity": "LOW",
                  "codeQuality": "GOOD",
                  "codeSmells": [],
                  "refactoringSuggestions": [],
                  "vbAntiPatterns": []
                },
                {
                  "name": "Report",
                  "methods": ["Generate"],
                  "dependencies": ["Calculator"],
                  "complexity": "MEDIUM",
                  "codeQuality": "FAIR",
                  "codeSmells": ["Mixed concerns"],
                  "refactoringSuggestions": ["Separate report formatting from data access"],
                  "vbAntiPatterns": ["On Error Resume Next"]
                }
              ],
              "suggestedMigrationOrder": ["Calculator", "Report"],
              "dependencyGraph": {
                "Calculator": [],
                "Report": ["Calculator"]
              },
              "summary": "Two classes found. Report depends on Calculator."
            }""";

    private ZipManifest createManifest(String sessionId) {
        return new ZipManifest(sessionId, List.of(
                new VbSourceFile("Calculator.vb", "Calculator.vb",
                        "Public Class Calculator\n    Public Function Add() As Integer\n    End Function\nEnd Class"),
                new VbSourceFile("Report.vb", "Report.vb",
                        "Public Class Report\n    Public Function Generate() As String\n    End Function\nEnd Class")
        ), 2);
    }

    @Test
    void analyseProject_callsClaudeAndReturnsProjectAnalysis() {
        MigrationSession session = new MigrationSession("test-session-123");
        when(sessionStore.get("test-session-123")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse(PROJECT_JSON_RESPONSE));

        ProjectAnalysis result = analysisService.analyseProject(createManifest("test-session-123"));

        assertThat(result.sessionId()).isEqualTo("test-session-123");
        assertThat(result.classes()).hasSize(2);
        assertThat(result.classes().get(0).name()).isEqualTo("Calculator");
        assertThat(result.classes().get(1).name()).isEqualTo("Report");
        assertThat(result.suggestedMigrationOrder()).containsExactly("Calculator", "Report");
        assertThat(result.dependencyGraph()).containsKey("Report");
        assertThat(result.dependencyGraph().get("Report")).containsExactly("Calculator");
        assertThat(result.summary()).isEqualTo("Two classes found. Report depends on Calculator.");
    }

    @Test
    void analyseProject_combinesFileContentsWithHeaders() {
        MigrationSession session = new MigrationSession("test-session-123");
        when(sessionStore.get("test-session-123")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse(PROJECT_JSON_RESPONSE));

        analysisService.analyseProject(createManifest("test-session-123"));

        verify(claudeClient).sendWithCachedSystemPrompt(
                eq(AnalysisService.PROJECT_SYSTEM_PROMPT),
                contains("// === File: Calculator.vb ==="),
                eq(Model.CLAUDE_SONNET_4_6),
                eq(8192L)
        );
    }

    @Test
    void analyseProject_storesCombinedContentInSession() {
        MigrationSession session = new MigrationSession("test-session-123");
        when(sessionStore.get("test-session-123")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse(PROJECT_JSON_RESPONSE));

        analysisService.analyseProject(createManifest("test-session-123"));

        assertThat(session.getVbContent()).contains("// === File: Calculator.vb ===");
        assertThat(session.getVbContent()).contains("// === File: Report.vb ===");
        assertThat(session.getAnalysisResult()).isNotNull();
    }

    @Test
    void analyseProject_populatesClassSourcesFromFiles() {
        MigrationSession session = new MigrationSession("test-session-123");
        when(sessionStore.get("test-session-123")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse(PROJECT_JSON_RESPONSE));

        analysisService.analyseProject(createManifest("test-session-123"));

        assertThat(session.getClassSources()).containsKey("Calculator");
        assertThat(session.getClassSources().get("Calculator")).contains("Public Class Calculator");
        assertThat(session.getClassSources().get("Calculator")).doesNotContain("Public Class Report");
        assertThat(session.getClassSources()).containsKey("Report");
        assertThat(session.getClassSources().get("Report")).contains("Public Class Report");
    }

    @Test
    void analyseProject_getVbContentForClass_returnsClassSpecificSource() {
        MigrationSession session = new MigrationSession("test-session-123");
        when(sessionStore.get("test-session-123")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse(PROJECT_JSON_RESPONSE));

        analysisService.analyseProject(createManifest("test-session-123"));

        // Class-specific source should be returned, not the combined content
        String calcSource = session.getVbContentForClass("Calculator");
        assertThat(calcSource).contains("Public Class Calculator");
        assertThat(calcSource).doesNotContain("Public Class Report");
        assertThat(calcSource).doesNotContain("// === File:");
    }

    @Test
    void analyseProject_getVbContentForClass_fallsBackToFullContent() {
        MigrationSession session = new MigrationSession("test-session-123");
        when(sessionStore.get("test-session-123")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse(PROJECT_JSON_RESPONSE));

        analysisService.analyseProject(createManifest("test-session-123"));

        // Unknown class falls back to combined vbContent
        String unknown = session.getVbContentForClass("UnknownClass");
        assertThat(unknown).contains("// === File: Calculator.vb ===");
        assertThat(unknown).contains("// === File: Report.vb ===");
    }

    @Test
    void analyseProject_throwsOnMissingSession() {
        when(sessionStore.get("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> analysisService.analyseProject(
                new ZipManifest("missing", List.of(), 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session not found");
    }

    @Test
    void analyseProject_throwsOnInvalidJson() {
        MigrationSession session = new MigrationSession("test-session-123");
        when(sessionStore.get("test-session-123")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse("not valid json"));

        assertThatThrownBy(() -> analysisService.analyseProject(createManifest("test-session-123")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse Claude response");
    }

    @Test
    void analyseProject_tracksTokenUsage() {
        MigrationSession session = new MigrationSession("test-session-123");
        when(sessionStore.get("test-session-123")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(new ClaudeClient.ClaudeResponse(PROJECT_JSON_RESPONSE, 500, 200));

        analysisService.analyseProject(createManifest("test-session-123"));

        assertThat(session.getTokenUsages()).hasSize(1);
        assertThat(session.getTokenUsages().get(0).step()).isEqualTo("analyse-project");
        assertThat(session.getTokenUsages().get(0).inputTokens()).isEqualTo(500);
        assertThat(session.getTokenUsages().get(0).outputTokens()).isEqualTo(200);
    }

    @Test
    void analyseProject_handlesNullDependencyGraph() {
        String jsonNoDeps = """
                {
                  "classes": [{
                    "name": "Simple",
                    "methods": ["DoWork"],
                    "dependencies": [],
                    "complexity": "LOW"
                  }],
                  "suggestedMigrationOrder": ["Simple"],
                  "summary": "One simple class."
                }""";
        MigrationSession session = new MigrationSession("test-session-123");
        when(sessionStore.get("test-session-123")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse(jsonNoDeps));

        ProjectAnalysis result = analysisService.analyseProject(createManifest("test-session-123"));

        assertThat(result.dependencyGraph()).isEmpty();
    }

    @Test
    void analyseProject_stripsMarkdownFences() {
        MigrationSession session = new MigrationSession("test-session-123");
        when(sessionStore.get("test-session-123")).thenReturn(Optional.of(session));

        String wrappedResponse = "```json\n" + PROJECT_JSON_RESPONSE + "\n```";
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse(wrappedResponse));

        ProjectAnalysis result = analysisService.analyseProject(createManifest("test-session-123"));

        assertThat(result.classes()).hasSize(2);
    }

    @Test
    void analyse_parsesCodeQualityFields() {
        MigrationSession session = new MigrationSession("test-session-123");
        when(sessionStore.create()).thenReturn(session);
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse(CLAUDE_JSON_RESPONSE));

        AnalysisResult result = analysisService.analyse("Form1.vb", "Public Class Form1...");

        assertThat(result.classes().get(0).codeQuality()).isEqualTo(CodeQuality.FAIR);
        assertThat(result.classes().get(0).codeSmells()).containsExactly("Mixed concerns \u2014 UI logic mixed with business logic");
        assertThat(result.classes().get(0).refactoringSuggestions()).containsExactly("Extract arithmetic operations into a separate service class");
        assertThat(result.classes().get(0).vbAntiPatterns()).containsExactly("Implicit type conversions via Int()");
    }

    @Test
    void analyse_handlesNullCodeQualityFields() {
        MigrationSession session = new MigrationSession("test-session-123");
        when(sessionStore.create()).thenReturn(session);
        String jsonWithoutCodeQuality = """
                {
                  "classes": [{
                    "name": "Simple",
                    "methods": ["DoWork"],
                    "dependencies": [],
                    "complexity": "LOW"
                  }],
                  "suggestedMigrationOrder": ["Simple"],
                  "summary": "Simple class."
                }""";
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse(jsonWithoutCodeQuality));

        AnalysisResult result = analysisService.analyse("Simple.vb", "...");

        assertThat(result.classes().get(0).codeQuality()).isNull();
        assertThat(result.classes().get(0).codeSmells()).isNull();
        assertThat(result.classes().get(0).refactoringSuggestions()).isNull();
        assertThat(result.classes().get(0).vbAntiPatterns()).isNull();
    }
}
