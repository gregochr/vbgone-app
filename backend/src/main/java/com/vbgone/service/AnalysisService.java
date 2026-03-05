package com.vbgone.service;

import com.anthropic.models.messages.Model;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vbgone.model.*;
import com.vbgone.session.SessionStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnalysisService {

    static final String SYSTEM_PROMPT = """
            You are a VB.NET to C# migration expert. Analyse VB.NET source code and identify all \
            classes, their public methods, dependencies between classes, and complexity. Business \
            logic may be embedded in Windows Forms event handlers — extract the pure logic and \
            ignore all UI concerns. Return your analysis as JSON only, no preamble, no markdown, \
            matching this exact structure:
            {
              "classes": [{
                "name": "string",
                "methods": ["string"],
                "dependencies": ["string"],
                "complexity": "LOW | MEDIUM | HIGH"
              }],
              "suggestedMigrationOrder": ["string"],
              "summary": "string"
            }""";

    private final ClaudeClient claudeClient;
    private final SessionStore sessionStore;
    private final ObjectMapper objectMapper;

    public AnalysisService(ClaudeClient claudeClient, SessionStore sessionStore, ObjectMapper objectMapper) {
        this.claudeClient = claudeClient;
        this.sessionStore = sessionStore;
        this.objectMapper = objectMapper;
    }

    public AnalysisResult analyse(String filename, String content) {
        MigrationSession session = sessionStore.create();
        session.setFilename(filename);
        session.setVbContent(content);

        ClaudeClient.ClaudeResponse response = claudeClient.sendWithCachedSystemPrompt(
                SYSTEM_PROMPT, content, Model.CLAUDE_SONNET_4_6, 4096L);
        String json = stripMarkdownFences(response.text());

        String modelId = Model.CLAUDE_SONNET_4_6.asString();
        double cost = CostService.calculateCost(modelId, response.inputTokens(), response.outputTokens());
        session.addTokenUsage(new TokenUsage("analyse", modelId, response.inputTokens(), response.outputTokens(), cost));

        AnalysisResult result = parseAnalysis(session.getSessionId(), json);
        session.setAnalysisResult(result);
        return result;
    }

    private AnalysisResult parseAnalysis(String sessionId, String json) {
        try {
            ClaudeAnalysis analysis = objectMapper.readValue(json, ClaudeAnalysis.class);
            return new AnalysisResult(
                    sessionId,
                    analysis.classes(),
                    analysis.suggestedMigrationOrder(),
                    analysis.summary()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Claude response: " + e.getMessage(), e);
        }
    }

    private String stripMarkdownFences(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }
        return trimmed;
    }

    private record ClaudeAnalysis(
            List<ClassInfo> classes,
            List<String> suggestedMigrationOrder,
            String summary
    ) {}
}
