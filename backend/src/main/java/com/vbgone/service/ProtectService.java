package com.vbgone.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vbgone.ai.AiProvider;
import com.vbgone.ai.AiProviderRegistry;
import com.vbgone.ai.AiRequestOptions;
import com.vbgone.ai.AiResponse;
import com.vbgone.ai.ModelRole;
import com.vbgone.build.VbCharacterisationRunner;
import com.vbgone.model.*;
import com.vbgone.prompt.CSharpPrompts;
import com.vbgone.session.SessionStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Protect-mode generation + the real characterisation run. Mirrors {@code GenerationService}'s
 * model-call + token-accounting shape, but produces a pinned baseline surface (step 3) and an
 * MSTest characterisation suite that is executed against the original VB.NET (step 4).
 *
 * <p>Protect is C#-only (TARGET is locked), so the prompts come straight from
 * {@link CSharpPrompts} rather than the per-language registry.
 */
@Service
public class ProtectService {

    private final AiProviderRegistry registry;
    private final SessionStore sessionStore;
    private final ObjectMapper objectMapper;
    private final VbCharacterisationRunner runner;
    private final CSharpPrompts prompts = new CSharpPrompts();

    public ProtectService(AiProviderRegistry registry, SessionStore sessionStore,
                          ObjectMapper objectMapper, VbCharacterisationRunner runner) {
        this.registry = registry;
        this.sessionStore = sessionStore;
        this.objectMapper = objectMapper;
        this.runner = runner;
    }

    /** Step 3 — pin the concrete class's actual public surface (mechanical model). */
    public BaselineResult generateBaseline(String sessionId, String className,
                                           String provider, String targetLanguage,
                                           Map<String, String> modelOverrides) {
        AiRequestOptions options = AiRequestOptions.of(provider, targetLanguage, modelOverrides);
        MigrationSession session = getSession(sessionId);

        String userMessage = prompts.baselineSurfaceUserMessage(
                className, session.getVbContentForClass(className));
        AiResponse response = call(options, ModelRole.MECHANICAL,
                CSharpPrompts.BASELINE_SURFACE_SYSTEM_PROMPT, userMessage, 4096L, "baseline", session);

        List<BaselineMember> members = parseMembers(stripJsonFences(response.text()));
        BaselineResult result = new BaselineResult(
                sessionId, className, className + ".dll · public surface", members);
        session.setBaselineResult(result);
        return result;
    }

    /**
     * Step 4 — generate the MSTest characterisation suite (reasoning model), then compile the
     * original VB and run the suite against it. {@code netFaithful} is true only when every
     * assertion holds against the untouched original.
     */
    public BaselineTestsResult runBaselineTests(String sessionId, String className,
                                                String provider, String targetLanguage,
                                                Map<String, String> modelOverrides) {
        AiRequestOptions options = AiRequestOptions.of(provider, targetLanguage, modelOverrides);
        MigrationSession session = getSession(sessionId);

        String userMessage = prompts.baselineTestsUserMessage(
                className, session.getVbContentForClass(className));
        AiResponse response = call(options, ModelRole.REASONING,
                CSharpPrompts.BASELINE_TESTS_SYSTEM_PROMPT, userMessage, 16384L, "baseline-tests", session);
        String code = prompts.repairTruncated(prompts.stripWrappers(prompts.stripCodeFences(response.text())));

        return executeSuite(session, className, code);
    }

    /**
     * Re-run a (corrected) net against the original VB without regenerating it. When the net
     * isn't faithful, the fix is editing the assertions — not re-running the same suite — so the
     * UI sends the edited code here. No AI call, so no token usage is recorded.
     */
    public BaselineTestsResult rerunBaselineTests(String sessionId, String className, String code) {
        MigrationSession session = getSession(sessionId);
        return executeSuite(session, className, code);
    }

    /** Wraps the suite, runs it against the original VB on the CLR sidecar, builds the result. */
    private BaselineTestsResult executeSuite(MigrationSession session, String className, String code) {
        String testClassName = className + "Baseline";
        int generatedCount = prompts.countMsTests(code);
        TestsResult suite = new TestsResult(session.getSessionId(), className, testClassName, code, generatedCount);
        session.setBaselineSuite(suite);

        // The crux: compile the ORIGINAL VB and run the suite against it on the CLR sidecar.
        BuildResult build = runner.run(session, className, suite);
        session.setNetBuild(build);
        boolean netFaithful = build.buildStatus() == BuildStatus.GREEN && build.total() > 0;

        // Pair each failing test with its assertion message so the red state is actionable.
        Map<String, String> messages = session.getFailureMessages();
        List<TestFailure> failures = build.failedTests().stream()
                .map(name -> new TestFailure(name, messages.getOrDefault(name, "")))
                .toList();

        int testCount = build.total() > 0 ? build.total() : generatedCount;
        return new BaselineTestsResult(session.getSessionId(), className, testClassName, code,
                testCount, netFaithful, build, failures);
    }

    private List<BaselineMember> parseMembers(String json) {
        try {
            SurfaceJson surface = objectMapper.readValue(json, SurfaceJson.class);
            return surface.members() != null ? surface.members() : List.of();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse baseline surface: " + e.getMessage(), e);
        }
    }

    private String stripJsonFences(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }
        return trimmed;
    }

    private AiResponse call(AiRequestOptions options, ModelRole role, String systemPrompt,
                            String userMessage, long maxTokens, String step, MigrationSession session) {
        String modelId = registry.modelFor(options.provider(), role, options.modelOverrides());
        AiProvider aiProvider = registry.provider(options.provider());
        AiResponse response = aiProvider.generate(modelId, systemPrompt, userMessage, maxTokens);
        double cost = CostService.calculateCost(modelId, response.inputTokens(), response.outputTokens());
        session.addTokenUsage(new TokenUsage(step, modelId, response.inputTokens(),
                response.outputTokens(), cost, response.provider()));
        return response;
    }

    private MigrationSession getSession(String sessionId) {
        return sessionStore.get(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
    }

    private record SurfaceJson(List<BaselineMember> members) {}
}
