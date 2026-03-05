package com.vbgone.service;

import com.anthropic.models.messages.Model;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vbgone.model.AnalysisResult;
import com.vbgone.model.Complexity;
import com.vbgone.model.MigrationSession;
import com.vbgone.session.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
                "complexity": "LOW"
              }],
              "suggestedMigrationOrder": ["Form1"],
              "summary": "One class found with 3 arithmetic methods."
            }""";

    @BeforeEach
    void setUp() {
        analysisService = new AnalysisService(claudeClient, sessionStore, objectMapper);
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
}
