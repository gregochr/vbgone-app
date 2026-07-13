package com.vbgone.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vbgone.ai.AiProviderRegistry;
import com.vbgone.ai.AiRequestOptions;
import com.vbgone.ai.AiResponse;
import com.vbgone.ai.ModelRole;
import com.vbgone.build.CharacterisationRunner;
import com.vbgone.common.JsonResponses;
import com.vbgone.model.*;
import com.vbgone.prompt.CSharpPrompts;
import com.vbgone.session.SessionStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static com.vbgone.service.CharacterisationSuiteEditor.buildDiff;
import static com.vbgone.service.CharacterisationSuiteEditor.ensureTestClassAttribute;
import static com.vbgone.service.CharacterisationSuiteEditor.extractTestMethod;
import static com.vbgone.service.CharacterisationSuiteEditor.isFlaky;
import static com.vbgone.service.CharacterisationSuiteEditor.markTestIgnored;
import static com.vbgone.service.CharacterisationSuiteEditor.spliceMethod;
import static com.vbgone.service.CharacterisationSuiteEditor.validityGate;

/**
 * Assure-mode generation + the real characterisation run. Mirrors {@code GenerationService}'s
 * model-call + token-accounting shape, but produces a pinned baseline surface (step 3) and an
 * MSTest characterisation suite that is executed against the original VB.NET (step 4).
 *
 * <p>Assure is C#-only (TARGET is locked), so the prompts come straight from
 * {@link CSharpPrompts} rather than the per-language registry.
 */
@Service
public class AssureService {

    private final AiProviderRegistry registry;
    private final SessionStore sessionStore;
    private final ObjectMapper objectMapper;
    private final CharacterisationRunner runner;
    private final AiCallSupport aiCallSupport;
    private final CSharpPrompts prompts = new CSharpPrompts();

    public AssureService(AiProviderRegistry registry, SessionStore sessionStore,
                          ObjectMapper objectMapper, CharacterisationRunner runner,
                          AiCallSupport aiCallSupport) {
        this.registry = registry;
        this.sessionStore = sessionStore;
        this.objectMapper = objectMapper;
        this.runner = runner;
        this.aiCallSupport = aiCallSupport;
    }

    /** Step 3 — pin the concrete class's actual public surface (mechanical model). */
    public BaselineResult generateBaseline(String sessionId, String className, AiRequestOptions options) {
        MigrationSession session = sessionStore.getOrThrow(sessionId);

        String userMessage = prompts.baselineSurfaceUserMessage(
                className, session.getVbContentForClass(className));
        AiResponse response = aiCallSupport.call(options, ModelRole.MECHANICAL,
                CSharpPrompts.BASELINE_SURFACE_SYSTEM_PROMPT, userMessage, 4096L, "baseline", session);

        List<BaselineMember> members = parseMembers(JsonResponses.stripFences(response.text()));
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
    public BaselineTestsResult runBaselineTests(String sessionId, String className, AiRequestOptions options) {
        return runBaselineTests(sessionId, className, options, "linux");
    }

    /**
     * As above, but pins the characterisation runner chosen in the UI ({@code "linux"} or
     * {@code "windows"}) onto the session so the {@code CharacterisationRouter} sends framework-gated
     * classes to the Windows runner. Set here, at the first net execution, and reused by every later
     * run (rerun/augment/quarantine/repair) for the class.
     */
    public BaselineTestsResult runBaselineTests(String sessionId, String className,
                                                AiRequestOptions options, String runnerMode) {
        MigrationSession session = sessionStore.getOrThrow(sessionId);
        session.setRunnerMode(runnerMode);

        String userMessage = prompts.baselineTestsUserMessage(
                className, session.getVbContentForClass(className));
        AiResponse response = aiCallSupport.call(options, ModelRole.REASONING,
                CSharpPrompts.BASELINE_TESTS_SYSTEM_PROMPT, userMessage, 16384L, "baseline-tests", session);
        String code = prompts.cleanTestSuite(response.text());

        return executeSuite(session, className, code);
    }

    /**
     * Re-run a (corrected) net against the original VB without regenerating it. When the net
     * isn't faithful, the fix is editing the assertions — not re-running the same suite — so the
     * UI sends the edited code here. No AI call, so no token usage is recorded.
     */
    public BaselineTestsResult rerunBaselineTests(String sessionId, String className, String code) {
        MigrationSession session = sessionStore.getOrThrow(sessionId);
        return executeSuite(session, className, code);
    }

    /**
     * "Add more tests" — extend a green characterisation suite to pin more of the class. Sends the
     * current suite, the original VB and the current coverage to the model, asks it to KEEP every
     * existing test and add new ones for the untested behaviour, then re-runs the augmented suite
     * (which stays green if the new tests are faithful, or drops to the repair loop if one drifts).
     */
    public BaselineTestsResult augmentBaselineTests(String sessionId, String className, String currentCode,
                                                    Double coveragePercent, AiRequestOptions options) {
        MigrationSession session = sessionStore.getOrThrow(sessionId);

        String userMessage = prompts.augmentBaselineTestsUserMessage(
                className, session.getVbContentForClass(className), currentCode, coveragePercent);
        AiResponse response = aiCallSupport.call(options, ModelRole.REASONING,
                CSharpPrompts.AUGMENT_BASELINE_TESTS_SYSTEM_PROMPT, userMessage, 16384L, "augment-baseline-tests", session);
        String code = prompts.cleanTestSuite(response.text());

        return executeSuite(session, className, code);
    }

    /**
     * Accept a red baseline by quarantining the unrepairable test(s): mark each {@code [Ignore(...)]}
     * (kept in the suite and flagged for a human, but skipped by MSTest) and re-run the rest against
     * the original VB. When the remainder is green this records a downloadable per-class suite, so a
     * class with a set-aside test is still "assured" with the passing tests. No AI call.
     */
    public BaselineTestsResult quarantineBaseline(String sessionId, String className,
                                                  String code, List<String> tests) {
        MigrationSession session = sessionStore.getOrThrow(sessionId);
        String ignored = code;
        if (tests != null) {
            for (String test : tests) {
                ignored = markTestIgnored(ignored, test, QUARANTINE_REASON);
            }
        }
        BaselineTestsResult result = executeSuite(session, className, ignored);

        // The supplied names may not match what actually failed (e.g. a repair tier renamed the
        // test, or there were other failures). If the remainder is still red, set aside whatever
        // the run reported as failing and try once more, so the passing tests can still be pinned.
        if (!result.netFaithful() && session.getNetBuild() != null) {
            String retry = ignored;
            for (String failed : session.getNetBuild().failedTests()) {
                retry = markTestIgnored(retry, failed, QUARANTINE_REASON);
            }
            if (!retry.equals(ignored)) {
                result = executeSuite(session, className, retry);
            }
        }
        return result;
    }

    private static final String QUARANTINE_REASON =
            "quarantined: could not be repaired to match the original behaviour";

    /**
     * Step 4 auto-repair — one escalating attempt. Because the suite runs against the untouched
     * original, a red test can only mean the test is wrong: this asks the tier's model to rewrite
     * just the failing test to match the real observed output, gates the rewrite (same method, real
     * assertion, no always-pass cheat), then re-runs it against the original. A green re-run that
     * clears the gate is a fix; a value that differs across re-runs is flaky (route to quarantine).
     */
    public RepairAttempt repairAttempt(RepairRequest request) {
        AiRequestOptions options = request.aiOptions();
        MigrationSession session = sessionStore.getOrThrow(request.sessionId());

        RepairTier tier = RepairTier.of(request.tier());
        String modelId = registry.modelFor(options.provider(), tier.role(), options.modelOverrides());

        String failingTest = request.failingTest();
        String suiteCode = request.code();
        String currentTest = extractTestMethod(suiteCode, failingTest);
        String vbSource = session.getVbContentForClass(request.className());
        String observed = session.getFailureMessages().getOrDefault(failingTest, "");

        String userMessage = prompts.repairUserMessage(
                failingTest, currentTest, vbSource, observed, tier.guidance());
        AiResponse response = aiCallSupport.call(options, tier.role(), CSharpPrompts.REPAIR_SYSTEM_PROMPT,
                userMessage, 4096L, "repair", session);
        RepairPlan plan = parseRepair(JsonResponses.stripFences(response.text()));

        String terminalNoFix = tier.isFinal() ? "nofix" : "escalated";

        // No valid single-test edit at this tier — escalate without applying a bad fix.
        if (plan.noEdit() || plan.newTest() == null || plan.newTest().isBlank()) {
            return attempt(tier, modelId, plan, List.of(), new RepairAttempt.Gate(false,
                    plan.noEditNote("No valid single-test edit works at this tier.")),
                    null, terminalNoFix, suiteCode, false);
        }

        String newTest = plan.newTest();
        List<RepairAttempt.DiffLine> diff = buildDiff(currentTest, newTest);
        RepairAttempt.Gate gate = validityGate(currentTest, newTest);
        if (!gate.ok()) {
            // A produced-but-invalid rewrite (e.g. always-pass) is rejected, not counted as a win.
            return attempt(tier, modelId, plan, diff, gate, null, terminalNoFix, suiteCode, false);
        }

        String newCode = spliceMethod(suiteCode, currentTest, newTest);
        BuildResult build = runSuite(session, request.className(), newCode);
        boolean green = build.buildStatus() == BuildStatus.GREEN && build.total() > 0;
        if (green) {
            // The repaired suite now passes against the original — retain it for download.
            session.putBaselineSuite(request.className(), session.getBaselineSuite());
            RepairAttempt.Rerun rerun = new RepairAttempt.Rerun(true,
                    build.passed() + " / " + build.total() + " passing against your untouched VB.NET.");
            return attempt(tier, modelId, plan, diff, gate, rerun, "green", newCode, true);
        }

        // Still red against the untouched original. Re-run once more: a value that differs between
        // identical runs is nondeterministic (a different answer every run) and can't be pinned.
        String observed1 = session.getFailureMessages().getOrDefault(failingTest, "");
        String observed2 = rerunAndReobserve(session, request.className(), failingTest, newCode);
        boolean flaky = isFlaky(observed1, observed2);

        RepairAttempt.Rerun rerun = flaky
                ? new RepairAttempt.Rerun(false, "Red again — the value changed between runs.")
                : new RepairAttempt.Rerun(false, "Still red — " + shorten(observed2, observed));
        String tag = flaky ? "flag" : (tier.isFinal() ? "nofix" : "red");
        return attempt(tier, modelId, plan, diff, gate, rerun, tag, newCode, false);
    }

    /** Build a {@link RepairAttempt} card, filling the tier's fixed fields (name, role, model, rationale). */
    private RepairAttempt attempt(RepairTier tier, String modelId, RepairPlan plan,
                                  List<RepairAttempt.DiffLine> diff, RepairAttempt.Gate gate,
                                  RepairAttempt.Rerun rerun, String tag, String code, boolean netFaithful) {
        return new RepairAttempt(tier.displayName(), tier.role().name().toLowerCase(), modelId,
                plan.rationale(), diff, gate, rerun, tag, code, netFaithful);
    }

    /**
     * Re-run the spliced suite once more against the untouched original and return the failing test's
     * freshly observed output. The re-run's {@link BuildResult} is deliberately discarded — this is
     * invoked purely for its side effect of repopulating {@code session.getFailureMessages()}, so the
     * caller can compare this observation to the previous one and detect a value that changes every run.
     */
    private String rerunAndReobserve(MigrationSession session, String className,
                                     String failingTest, String newCode) {
        runSuite(session, className, newCode); // side-effect only: refreshes failure messages
        return session.getFailureMessages().getOrDefault(failingTest, "");
    }

    private BuildResult runSuite(MigrationSession session, String className, String code) {
        String wrapped = ensureTestClassAttribute(code);
        TestsResult suite = new TestsResult(session.getSessionId(), className,
                className + "BaselineTests", wrapped, prompts.countMsTests(wrapped));
        BuildResult build = runner.run(session, className, suite);
        session.setBaselineSuite(suite);
        session.setNetBuild(build);
        return build;
    }

    private static String shorten(String message, String fallback) {
        String m = (message == null || message.isBlank()) ? fallback : message;
        if (m == null) return "the assertion still fails.";
        m = m.trim();
        return m.length() > 160 ? m.substring(0, 157) + "…" : m;
    }

    private RepairPlan parseRepair(String json) {
        try {
            return objectMapper.readValue(json, RepairPlan.class);
        } catch (Exception e) {
            // A model that ignored the JSON contract shouldn't crash the loop — treat as no-edit.
            return new RepairPlan("The model did not return a usable rewrite.", "", true);
        }
    }

    private record RepairPlan(String rationale, String newTest, boolean noEdit) {
        String noEditNote(String fallback) {
            return rationale != null && !rationale.isBlank() ? rationale : fallback;
        }
    }

    /** Wraps the suite, runs it against the original VB on the CLR sidecar, builds the result. */
    private BaselineTestsResult executeSuite(MigrationSession session, String className, String code) {
        code = ensureTestClassAttribute(code);
        String testClassName = className + "BaselineTests";
        int generatedCount = prompts.countMsTests(code);
        TestsResult suite = new TestsResult(session.getSessionId(), className, testClassName, code, generatedCount);
        session.setBaselineSuite(suite);

        // The crux: compile the ORIGINAL VB and run the suite against it on the CLR sidecar.
        BuildResult build = runner.run(session, className, suite);
        session.setNetBuild(build);
        boolean netFaithful = build.buildStatus() == BuildStatus.GREEN && build.total() > 0;

        // Once the suite passes against the untouched original, retain it per class so the whole
        // assured portfolio can be downloaded as one MSTest project (see AssureArtifactService).
        if (netFaithful) session.putBaselineSuite(className, suite);

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

    private record SurfaceJson(List<BaselineMember> members) {}
}
