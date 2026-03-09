package com.vbgone.service;

import com.anthropic.models.messages.Model;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vbgone.model.*;
import com.vbgone.session.SessionStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalysisService {

    static final String SYSTEM_PROMPT = """
            You are a VB.NET to C# migration expert. Analyse VB.NET source code and identify all \
            classes, their public methods, dependencies between classes, and complexity. Business \
            logic may be embedded in Windows Forms event handlers — extract the pure logic and \
            ignore all UI concerns. For each class, also assess code quality as POOR, FAIR, or GOOD. \
            Identify code smells such as God class, mixed concerns, deep nesting, magic numbers, \
            and poor naming. Suggest specific refactoring opportunities. Flag VB.NET-specific \
            anti-patterns including On Error Resume Next, GoTo statements, implicit type conversions, \
            Hungarian notation, and magic numbers. Return your analysis as JSON only, no preamble, \
            no markdown, matching this exact structure:
            {
              "classes": [{
                "name": "string",
                "methods": ["string"],
                "dependencies": ["string"],
                "complexity": "LOW | MEDIUM | HIGH",
                "codeQuality": "POOR | FAIR | GOOD",
                "codeSmells": ["string"],
                "refactoringSuggestions": ["string"],
                "vbAntiPatterns": ["string"]
              }],
              "suggestedMigrationOrder": ["ClassName"],
              "summary": "string"
            }
            IMPORTANT: suggestedMigrationOrder must contain ONLY class names — no descriptions, \
            no reasons, no dashes. Example: ["Calculator", "Form1"], NOT ["Calculator — simple class"].""";

    static final String PROJECT_SYSTEM_PROMPT = """
            You are a VB.NET to C# migration expert. You are given multiple VB.NET source files \
            from the same project. Analyse ALL files together and identify every class, their \
            public methods, dependencies between classes (including cross-file dependencies), and \
            complexity. Business logic may be embedded in Windows Forms event handlers — extract \
            the pure logic and ignore all UI concerns. For each class, also assess code quality \
            as POOR, FAIR, or GOOD. Identify code smells such as God class, mixed concerns, deep \
            nesting, magic numbers, and poor naming. Suggest specific refactoring opportunities. \
            Flag VB.NET-specific anti-patterns including On Error Resume Next, GoTo statements, \
            implicit type conversions, Hungarian notation, and magic numbers. Return your analysis \
            as JSON only, no preamble, no markdown, matching this exact structure:
            {
              "classes": [{
                "name": "string",
                "methods": ["string"],
                "dependencies": ["string"],
                "complexity": "LOW | MEDIUM | HIGH",
                "codeQuality": "POOR | FAIR | GOOD",
                "codeSmells": ["string"],
                "refactoringSuggestions": ["string"],
                "vbAntiPatterns": ["string"]
              }],
              "suggestedMigrationOrder": ["string"],
              "dependencyGraph": { "ClassName": ["DependencyName"] },
              "summary": "string"
            }
            The suggestedMigrationOrder should start with the simplest, most self-contained \
            classes — leaf nodes with no dependencies — and end with the most complex classes \
            that depend on others. The dependencyGraph maps each class name to the list of \
            classes it depends on.
            IMPORTANT: suggestedMigrationOrder must contain ONLY class names — no descriptions, \
            no reasons, no dashes. Example: ["Calculator", "Form1"], NOT ["Calculator — simple class"].""";

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
                    cleanMigrationOrder(analysis.suggestedMigrationOrder()),
                    analysis.summary()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Claude response: " + e.getMessage(), e);
        }
    }

    public ProjectAnalysis analyseProject(ZipManifest manifest) {
        MigrationSession session = sessionStore.get(manifest.sessionId())
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + manifest.sessionId()));

        String combinedContent = manifest.files().stream()
                .map(f -> "// === File: " + f.relativePath() + " ===\n" + f.content())
                .collect(Collectors.joining("\n\n"));

        session.setVbContent(combinedContent);

        ClaudeClient.ClaudeResponse response = claudeClient.sendWithCachedSystemPrompt(
                PROJECT_SYSTEM_PROMPT, combinedContent, Model.CLAUDE_SONNET_4_6, 8192L);
        String json = stripMarkdownFences(response.text());

        String modelId = Model.CLAUDE_SONNET_4_6.asString();
        double cost = CostService.calculateCost(modelId, response.inputTokens(), response.outputTokens());
        session.addTokenUsage(new TokenUsage("analyse-project", modelId, response.inputTokens(), response.outputTokens(), cost));

        ProjectAnalysis result = parseProjectAnalysis(manifest.sessionId(), json);
        session.setAnalysisResult(new AnalysisResult(
                manifest.sessionId(), result.classes(), result.suggestedMigrationOrder(), result.summary()));

        // Map each class name to its individual file source for per-class generation
        for (ClassInfo cls : result.classes()) {
            for (VbSourceFile file : manifest.files()) {
                if (file.content().contains("Class " + cls.name())) {
                    session.putClassSource(cls.name(), file.content());
                    break;
                }
            }
        }

        return result;
    }

    private ProjectAnalysis parseProjectAnalysis(String sessionId, String json) {
        try {
            ClaudeProjectAnalysis analysis = objectMapper.readValue(json, ClaudeProjectAnalysis.class);
            Map<String, List<String>> depGraph = analysis.dependencyGraph() != null
                    ? analysis.dependencyGraph() : Collections.emptyMap();
            return new ProjectAnalysis(
                    sessionId,
                    analysis.classes(),
                    cleanMigrationOrder(analysis.suggestedMigrationOrder()),
                    depGraph,
                    analysis.summary()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Claude response: " + e.getMessage(), e);
        }
    }

    private List<String> cleanMigrationOrder(List<String> order) {
        if (order == null) return List.of();
        return order.stream()
                .map(s -> s.split("\\s*[—–\\-]\\s", 2)[0].trim())
                .filter(s -> !s.isEmpty())
                .toList();
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

    private record ClaudeProjectAnalysis(
            List<ClassInfo> classes,
            List<String> suggestedMigrationOrder,
            Map<String, List<String>> dependencyGraph,
            String summary
    ) {}
}
