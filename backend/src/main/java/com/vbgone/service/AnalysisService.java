package com.vbgone.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vbgone.ai.AiRequestOptions;
import com.vbgone.ai.AiResponse;
import com.vbgone.ai.ModelRole;
import com.vbgone.common.JsonResponses;
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

    /**
     * Assure mode's analysis persona: forensic, not prescriptive. It records what each
     * path does today — return values and the exact exceptions thrown on edge inputs —
     * and explicitly does NOT suggest fixes. The extra observedBehaviour array drives the
     * UI's dominant "Observed Behaviour" block.
     */
    static final String ASSURE_SYSTEM_PROMPT = """
            You are a VB.NET behaviour archaeologist. Analyse VB.NET source code and record \
            EXACTLY what it does today — defects included. Business logic may be embedded in \
            Windows Forms event handlers — characterise the pure logic and ignore UI wiring. \
            You are forensic, NOT prescriptive: describe the faults precisely; do NOT suggest \
            how to fix them, refactor them, or migrate them. For each method, record the real \
            return value and the EXACT exception type thrown on edge inputs (null, empty, zero, \
            negative, non-numeric). Identify code smells. Do NOT include refactoring suggestions. \
            Return your analysis as JSON only, no preamble, no markdown, matching this exact \
            structure:
            {
              "classes": [{
                "name": "string",
                "methods": ["string"],
                "dependencies": ["string"],
                "complexity": "LOW | MEDIUM | HIGH",
                "codeQuality": "POOR | FAIR | GOOD",
                "codeSmells": ["string"],
                "observedBehaviour": [{
                  "method": "string",
                  "cls": "string",
                  "rows": [{
                    "cond": "edge condition, e.g. headcount = 0",
                    "outcome": "what it does today, e.g. throws DivideByZeroException",
                    "kind": "throws | fault | returns"
                  }]
                }]
              }],
              "suggestedMigrationOrder": ["ClassName"],
              "summary": "string"
            }
            For each row, "kind" is "throws" for an exception, "fault" for a silent wrong result \
            (no error raised), or "returns" for normal/benign output. \
            IMPORTANT: suggestedMigrationOrder must contain ONLY class names — no descriptions, \
            no reasons, no dashes.""";

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

    private final AiCallSupport aiCallSupport;
    private final SessionStore sessionStore;
    private final ObjectMapper objectMapper;

    public AnalysisService(AiCallSupport aiCallSupport, SessionStore sessionStore, ObjectMapper objectMapper) {
        this.aiCallSupport = aiCallSupport;
        this.sessionStore = sessionStore;
        this.objectMapper = objectMapper;
    }

    public AnalysisResult analyse(String filename, String content) {
        return analyse(filename, content, null, null, null);
    }

    public AnalysisResult analyse(String filename, String content,
                                  String provider, String targetLanguage,
                                  Map<String, String> modelOverrides) {
        return analyse(filename, content, provider, targetLanguage, modelOverrides, null);
    }

    public AnalysisResult analyse(String filename, String content,
                                  String provider, String targetLanguage,
                                  Map<String, String> modelOverrides, String mode) {
        AiRequestOptions options = AiRequestOptions.of(provider, targetLanguage, modelOverrides);
        boolean assure = "assure".equalsIgnoreCase(mode);

        MigrationSession session = sessionStore.create();
        session.setFilename(filename);
        session.setVbContent(content);
        session.setTargetLanguage(options.targetLanguage());

        // Assure uses a forensic persona and emits the richer observedBehaviour array.
        String systemPrompt = assure ? ASSURE_SYSTEM_PROMPT : SYSTEM_PROMPT;
        long maxTokens = assure ? 8192L : 4096L;
        AiResponse response = aiCallSupport.call(
                options, ModelRole.REASONING, systemPrompt, content, maxTokens, "analyse", session);
        String json = JsonResponses.stripFences(response.text());

        AnalysisResult result = parseAnalysis(session.getSessionId(), json);
        session.setAnalysisResult(result);

        // Pre-populate per-class VB source for multi-class single-file scenarios
        for (ClassInfo cls : result.classes()) {
            String extracted = MigrationSession.extractVbClass(content, cls.name());
            if (extracted != null) {
                session.putClassSource(cls.name(), extracted);
            }
        }

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
        return analyseProject(manifest, null, null, null);
    }

    public ProjectAnalysis analyseProject(ZipManifest manifest,
                                          String provider, String targetLanguage,
                                          Map<String, String> modelOverrides) {
        AiRequestOptions options = AiRequestOptions.of(provider, targetLanguage, modelOverrides);

        MigrationSession session = sessionStore.get(manifest.sessionId())
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + manifest.sessionId()));
        session.setTargetLanguage(options.targetLanguage());

        String combinedContent = manifest.files().stream()
                .map(f -> "// === File: " + f.relativePath() + " ===\n" + f.content())
                .collect(Collectors.joining("\n\n"));

        session.setVbContent(combinedContent);

        AiResponse response = aiCallSupport.call(options, ModelRole.REASONING,
                PROJECT_SYSTEM_PROMPT, combinedContent, 8192L, "analyse-project", session);
        String json = JsonResponses.stripFences(response.text());

        ProjectAnalysis result = parseProjectAnalysis(manifest.sessionId(), json);
        session.setAnalysisResult(new AnalysisResult(
                manifest.sessionId(), result.classes(), result.suggestedMigrationOrder(), result.summary()));

        // Map each class name to its extracted VB source for per-class generation
        for (ClassInfo cls : result.classes()) {
            for (VbSourceFile file : manifest.files()) {
                if (file.content().contains("Class " + cls.name())) {
                    // Extract just this class section, not the entire file
                    String extracted = MigrationSession.extractVbClass(file.content(), cls.name());
                    session.putClassSource(cls.name(), extracted != null ? extracted : file.content());
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
