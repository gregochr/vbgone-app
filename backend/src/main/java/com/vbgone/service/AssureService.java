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
    private final VbCharacterisationRunner runner;
    private final CSharpPrompts prompts = new CSharpPrompts();

    public AssureService(AiProviderRegistry registry, SessionStore sessionStore,
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

    /**
     * Accept a red baseline by quarantining the unrepairable test(s): mark each {@code [Ignore(...)]}
     * (kept in the suite and flagged for a human, but skipped by MSTest) and re-run the rest against
     * the original VB. When the remainder is green this records a downloadable per-class suite, so a
     * class with a set-aside test is still "assured" with the passing tests. No AI call.
     */
    public BaselineTestsResult quarantineBaseline(String sessionId, String className,
                                                  String code, List<String> tests) {
        MigrationSession session = getSession(sessionId);
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
        AiRequestOptions options = AiRequestOptions.of(
                request.provider(), request.targetLanguage(), request.modelOverrides());
        MigrationSession session = getSession(request.sessionId());

        int tier = request.tier();
        ModelRole role = tierRole(tier);
        String tierName = tierName(tier);
        String modelId = registry.modelFor(options.provider(), role, options.modelOverrides());

        String failingTest = request.failingTest();
        String suiteCode = request.code();
        String currentTest = extractTestMethod(suiteCode, failingTest);
        String vbSource = session.getVbContentForClass(request.className());
        String observed = session.getFailureMessages().getOrDefault(failingTest, "");

        String userMessage = prompts.repairUserMessage(
                failingTest, currentTest, vbSource, observed, tierGuidance(tier));
        AiResponse response = call(options, role, CSharpPrompts.REPAIR_SYSTEM_PROMPT,
                userMessage, 4096L, "repair", session);
        RepairPlan plan = parseRepair(stripJsonFences(response.text()));

        String terminalNoFix = tier >= 3 ? "nofix" : "escalated";

        // No valid single-test edit at this tier — escalate without applying a bad fix.
        if (plan.noEdit() || plan.newTest() == null || plan.newTest().isBlank()) {
            return new RepairAttempt(tierName, role.name().toLowerCase(), modelId, plan.rationale(),
                    List.of(), new RepairAttempt.Gate(false,
                    plan.noEditNote("No valid single-test edit works at this tier.")),
                    null, terminalNoFix, suiteCode, false);
        }

        String newTest = plan.newTest();
        List<RepairAttempt.DiffLine> diff = buildDiff(currentTest, newTest);
        RepairAttempt.Gate gate = validityGate(currentTest, newTest);
        if (!gate.ok()) {
            // A produced-but-invalid rewrite (e.g. always-pass) is rejected, not counted as a win.
            return new RepairAttempt(tierName, role.name().toLowerCase(), modelId, plan.rationale(),
                    diff, gate, null, terminalNoFix, suiteCode, false);
        }

        String newCode = spliceMethod(suiteCode, currentTest, newTest);
        BuildResult build = runSuite(session, request.className(), newCode);
        boolean green = build.buildStatus() == BuildStatus.GREEN && build.total() > 0;
        if (green) {
            // The repaired suite now passes against the original — retain it for download.
            session.putBaselineSuite(request.className(), session.getBaselineSuite());
            RepairAttempt.Rerun rerun = new RepairAttempt.Rerun(true,
                    build.passed() + " / " + build.total() + " passing against your untouched VB.NET.");
            return new RepairAttempt(tierName, role.name().toLowerCase(), modelId, plan.rationale(),
                    diff, gate, rerun, "green", newCode, true);
        }

        // Still red against the untouched original. Re-run once more: if the observed value differs
        // between identical runs it's nondeterministic (a different answer every run), not fixable.
        String observed1 = session.getFailureMessages().getOrDefault(failingTest, "");
        runSuite(session, request.className(), newCode);
        String observed2 = session.getFailureMessages().getOrDefault(failingTest, "");
        boolean flaky = isFlaky(observed1, observed2);

        RepairAttempt.Rerun rerun = flaky
                ? new RepairAttempt.Rerun(false, "Red again — the value changed between runs.")
                : new RepairAttempt.Rerun(false, "Still red — " + shorten(observed2, observed));
        String tag = flaky ? "flag" : (tier >= 3 ? "nofix" : "red");
        return new RepairAttempt(tierName, role.name().toLowerCase(), modelId, plan.rationale(),
                diff, gate, rerun, tag, newCode, false);
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

    // ── Auto-repair helpers (package-private for unit testing) ──

    static ModelRole tierRole(int tier) {
        return switch (tier) {
            case 1 -> ModelRole.MECHANICAL;
            case 3 -> ModelRole.ESCALATION;
            default -> ModelRole.REASONING;
        };
    }

    static String tierName(int tier) {
        return switch (tier) {
            case 1 -> "Mechanical";
            case 3 -> "Escalation";
            default -> "Reasoning";
        };
    }

    static String tierGuidance(int tier) {
        return switch (tier) {
            case 1 -> "Cheap mechanical tier: only a deterministic single-value swap is allowed — "
                    + "change the expected literal to the observed value and fix a now-misleading "
                    + "name/comment. If the code THREW where a value was expected, there is nothing "
                    + "to swap: set noEdit true so a stronger model can restructure the test.";
            case 3 -> "Final attempt with full context. Tighten to the EXACT runtime exception type "
                    + "or value from the output. If the value is different on every run, no fixed "
                    + "test can match it — set noEdit true; never fake a pass.";
            default -> "Restructure the assertion using the method source and the failure output "
                    + "(for example, a value assertion becomes Assert.ThrowsException<T> for the "
                    + "exact exception type actually thrown).";
        };
    }

    /**
     * Isolate a single {@code [TestMethod] ... }} block by name: walk back to the attribute line,
     * forward by brace matching to the method's closing brace. Returns "" if not found.
     */
    static String extractTestMethod(String code, String testName) {
        if (code == null || testName == null || testName.isBlank()) return "";
        String[] lines = code.split("\n", -1);
        int sig = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(" " + testName + "(") || lines[i].contains(" " + testName + " (")) {
                sig = i;
                break;
            }
        }
        if (sig < 0) return "";
        int start = sig;
        while (start > 0 && !lines[start - 1].trim().startsWith("[")) start--;
        if (start > 0 && lines[start - 1].trim().startsWith("[")) start--;
        int depth = 0;
        boolean opened = false;
        int end = sig;
        for (int j = sig; j < lines.length; j++) {
            for (char c : lines[j].toCharArray()) {
                if (c == '{') { depth++; opened = true; }
                else if (c == '}') depth--;
            }
            if (opened && depth <= 0) { end = j; break; }
        }
        StringBuilder sb = new StringBuilder();
        for (int j = start; j <= end && j < lines.length; j++) {
            sb.append(lines[j]);
            if (j < end) sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Insert {@code [Ignore("<reason>")]} above a test method's attribute block so it is retained in
     * the suite (visible for review) but skipped by MSTest — keeping the run green. Idempotent, and
     * a no-op if the named test isn't found. {@code Ignore} lives in the same namespace as
     * {@code TestMethod}, so no extra {@code using} is needed.
     */
    static String markTestIgnored(String code, String testName, String reason) {
        if (code == null || testName == null || testName.isBlank()) return code;
        String[] lines = code.split("\n", -1);
        int sig = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(" " + testName + "(") || lines[i].contains(" " + testName + " (")) {
                sig = i;
                break;
            }
        }
        if (sig < 0) return code;
        // Walk up over the method's contiguous attribute lines ([TestMethod], [DataRow], ...).
        int attr = sig;
        while (attr > 0 && lines[attr - 1].trim().startsWith("[")) attr--;
        for (int i = attr; i <= sig; i++) {
            if (lines[i].contains("[Ignore(")) return code; // already quarantined
        }
        String line = lines[attr];
        String indent = line.substring(0, line.length() - line.stripLeading().length());
        String ignore = indent + "[Ignore(\"" + reason.replace("\"", "'") + "\")]";
        java.util.List<String> out = new java.util.ArrayList<>(java.util.Arrays.asList(lines));
        out.add(attr, ignore);
        return String.join("\n", out);
    }

    static String spliceMethod(String code, String oldBlock, String newBlock) {
        if (oldBlock == null || oldBlock.isBlank() || !code.contains(oldBlock)) return code;
        return code.replace(oldBlock, newBlock);
    }

    private static final java.util.regex.Pattern SUT_CALL =
            java.util.regex.Pattern.compile("\\.\\s*([A-Za-z_]\\w*)\\s*\\(");

    /**
     * The gate that stops the loop cheating. A green re-run is not enough — the rewrite must still
     * call the same method under test and make a real assertion, never a meaningless always-pass
     * test. Rejects: no assertion, a tautology (Assert.IsTrue(true) / AreEqual(x, x)), or dropping
     * the method under test.
     */
    static RepairAttempt.Gate validityGate(String oldBlock, String newBlock) {
        String body = newBlock == null ? "" : newBlock;
        String normalized = body.replaceAll("\\s+", "");
        if (!body.contains("Assert.")) {
            return new RepairAttempt.Gate(false,
                    "The rewrite has no real assertion — a meaningless always-pass test. Rejected.");
        }
        if (normalized.contains("Assert.IsTrue(true)") || normalized.contains("Assert.IsFalse(false)")
                || isTrivialAreEqual(normalized)) {
            return new RepairAttempt.Gate(false,
                    "The rewrite is an always-pass test (a tautology). Rejected.");
        }
        String method = primaryCall(oldBlock);
        if (method != null && !body.contains("." + method + "(") && !body.contains("." + method + " (")) {
            return new RepairAttempt.Gate(false,
                    "The rewrite no longer calls " + method + " — the method under test changed. Rejected.");
        }
        String note = method != null
                ? "Still calls " + method + " and still checks the return value or thrown exception. "
                    + "Not a meaningless always-pass test."
                : "Keeps a real assertion on the same test. Not a meaningless always-pass test.";
        return new RepairAttempt.Gate(true, note);
    }

    /** The method under test called on the system-under-test variable (first non-Assert call). */
    private static String primaryCall(String block) {
        if (block == null) return null;
        var m = SUT_CALL.matcher(block);
        while (m.find()) {
            String name = m.group(1);
            if (!name.startsWith("Assert") && !name.equals("ThrowsException") && !name.equals("Equals")) {
                return name;
            }
        }
        return null;
    }

    private static boolean isTrivialAreEqual(String normalized) {
        var m = java.util.regex.Pattern
                .compile("Assert\\.AreEqual\\(([^,]+),([^)]+)\\)").matcher(normalized);
        while (m.find()) {
            if (m.group(1).equals(m.group(2))) return true;
        }
        return false;
    }

    /** A minimal line-level diff: lines only in old are removed, lines only in new are added. */
    static List<RepairAttempt.DiffLine> buildDiff(String oldBlock, String newBlock) {
        List<String> oldLines = trimmedLines(oldBlock);
        List<String> newLines = trimmedLines(newBlock);
        java.util.Set<String> oldSet = new java.util.HashSet<>(oldLines);
        java.util.Set<String> newSet = new java.util.HashSet<>(newLines);
        List<RepairAttempt.DiffLine> diff = new java.util.ArrayList<>();
        for (String line : oldLines) {
            if (!newSet.contains(line)) diff.add(new RepairAttempt.DiffLine("-", line));
        }
        for (String line : newLines) {
            if (!oldSet.contains(line)) diff.add(new RepairAttempt.DiffLine("+", line));
        }
        return diff;
    }

    private static List<String> trimmedLines(String block) {
        if (block == null || block.isBlank()) return List.of();
        return block.lines().map(String::stripTrailing).filter(l -> !l.isBlank()).toList();
    }

    /** The observed value differs between two identical runs → nondeterministic (not fixable). */
    static boolean isFlaky(String message1, String message2) {
        String a = observedActual(message1);
        String b = observedActual(message2);
        return !a.isBlank() && !b.isBlank() && !a.equals(b);
    }

    private static String observedActual(String message) {
        if (message == null) return "";
        var m = java.util.regex.Pattern.compile("Actual:\\s*<?([^>\\n]*)>?").matcher(message);
        return m.find() ? m.group(1).trim() : "";
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

    /**
     * The baseline prompt (correctly) instructs the model to omit {@code using} lines — the
     * csproj supplies them as global usings — so the model's output starts with {@code [TestClass]}.
     * {@code stripCodeFences} then anchors on {@code "public class "} and returns everything from
     * that point, silently dropping the leading attribute. Without {@code [TestClass]}, MSTest
     * discovers zero tests: the suite compiles and runs but the .trx reports 0/0, which the UI
     * reads as "baseline not faithful". Re-attach the attribute if it went missing (idempotent, so
     * it also guards the user-edited re-run path).
     */
    static String ensureTestClassAttribute(String code) {
        if (code == null || code.isBlank() || code.contains("[TestClass]")) return code;
        boolean hasTests = code.contains("[TestMethod]") || code.contains("[DataTestMethod]");
        if (!hasTests) return code;
        int idx = code.indexOf("public class ");
        if (idx < 0) idx = code.indexOf("class ");
        if (idx < 0) return code;
        int lineStart = code.lastIndexOf('\n', idx) + 1;
        String indent = code.substring(lineStart, idx);
        return code.substring(0, lineStart) + indent + "[TestClass]\n" + code.substring(lineStart);
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
