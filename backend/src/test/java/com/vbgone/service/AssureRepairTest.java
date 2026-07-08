package com.vbgone.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vbgone.ai.AiProviderRegistry;
import com.vbgone.ai.anthropic.AnthropicProvider;
import com.vbgone.ai.github.GitHubModelsProvider;
import com.vbgone.build.VbCharacterisationRunner;
import com.vbgone.model.*;
import com.vbgone.session.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Auto-repair loop — the pure gate/diff/extract helpers plus the escalating attempt flow. */
@ExtendWith(MockitoExtension.class)
class AssureRepairTest {

    @Mock private ClaudeClient claudeClient;
    @Mock private SessionStore sessionStore;
    @Mock private VbCharacterisationRunner runner;

    private AssureService service;

    private static final String SUITE = """
            [TestClass]
            public class OrderProcessorBaselineTests
            {
                [TestMethod]
                public void PlaceOrder_TotalWithFraction_TruncatesToInt()
                {
                    var sut = new OrderProcessor();
                    int result = sut.PlaceOrder(3, 9.9m);
                    Assert.AreEqual(12, result);
                }
            }
            """;

    @BeforeEach
    void setUp() {
        AnthropicProvider anthropic = new AnthropicProvider(claudeClient);
        GitHubModelsProvider copilot = new GitHubModelsProvider("", new ObjectMapper());
        AiProviderRegistry registry = new AiProviderRegistry(List.of(anthropic, copilot));
        service = new AssureService(registry, sessionStore, new ObjectMapper(), runner);
    }

    private ClaudeClient.ClaudeResponse ai(String text) {
        return new ClaudeClient.ClaudeResponse(text, 100, 50);
    }

    private MigrationSession session() {
        MigrationSession s = new MigrationSession("s1");
        s.setVbContent("Public Class OrderProcessor\nEnd Class");
        s.setFailureMessages(new java.util.HashMap<>(Map.of(
                "PlaceOrder_TotalWithFraction_TruncatesToInt",
                "Assert.AreEqual failed. Expected:<12>. Actual:<13>.")));
        return s;
    }

    private BuildResult build(BuildStatus status, int total, int passed, int failed) {
        return new BuildResult("s1", status, total, passed, failed, List.of(), List.of());
    }

    private RepairRequest request(int tier) {
        return new RepairRequest("s1", "OrderProcessor", "anthropic", "csharp", Map.of(),
                SUITE, "PlaceOrder_TotalWithFraction_TruncatesToInt", tier);
    }

    // ── pure helpers ──

    @Test
    void extractTestMethod_isolatesTheNamedBlock() {
        String block = AssureService.extractTestMethod(SUITE, "PlaceOrder_TotalWithFraction_TruncatesToInt");
        assertThat(block).contains("[TestMethod]").contains("Assert.AreEqual(12, result);");
        assertThat(block).doesNotContain("public class OrderProcessorBaselineTests");
    }

    @Test
    void validityGate_rejectsAnAlwaysPassRewrite() {
        String old = "int result = sut.PlaceOrder(3, 9.9m);\nAssert.AreEqual(12, result);";
        assertThat(AssureService.validityGate(old, "Assert.IsTrue(true);").ok()).isFalse();
        assertThat(AssureService.validityGate(old, "int result = sut.PlaceOrder(3, 9.9m);").ok()).isFalse();
    }

    @Test
    void validityGate_rejectsDroppingTheMethodUnderTest() {
        String old = "var r = sut.PlaceOrder(3, 9.9m);\nAssert.AreEqual(12, r);";
        String rewrite = "var r = sut.CalculateTotal(3, 9.9m);\nAssert.AreEqual(13, r);";
        assertThat(AssureService.validityGate(old, rewrite).ok()).isFalse();
    }

    @Test
    void validityGate_acceptsASameMethodRealAssertion() {
        String old = "var r = sut.PlaceOrder(3, 9.9m);\nAssert.AreEqual(12, r);";
        String rewrite = "var r = sut.PlaceOrder(3, 9.9m);\nAssert.AreEqual(13, r);";
        assertThat(AssureService.validityGate(old, rewrite).ok()).isTrue();
    }

    @Test
    void buildDiff_groupsRemovedThenAdded() {
        var diff = AssureService.buildDiff(
                "Assert.AreEqual(12, result);", "Assert.AreEqual(13, result);");
        assertThat(diff).extracting(RepairAttempt.DiffLine::op).containsExactly("-", "+");
    }

    @Test
    void isFlaky_trueOnlyWhenObservedValueDiffersAcrossRuns() {
        assertThat(AssureService.isFlaky("Expected:<1>. Actual:<1043>.", "Expected:<1>. Actual:<1071>."))
                .isTrue();
        assertThat(AssureService.isFlaky("Expected:<12>. Actual:<13>.", "Expected:<12>. Actual:<13>."))
                .isFalse();
    }

    @Test
    void tierRole_mapsAttemptsToEscalatingModels() {
        assertThat(AssureService.tierRole(1).name()).isEqualTo("MECHANICAL");
        assertThat(AssureService.tierRole(2).name()).isEqualTo("REASONING");
        assertThat(AssureService.tierRole(3).name()).isEqualTo("ESCALATION");
    }

    // ── repairAttempt flow ──

    @Test
    void repairAttempt_greenWhenTheSwapPassesTheGateAndReRunsGreen() {
        MigrationSession session = session();
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(ai("""
                        {"rationale":"CInt banker-rounds 9.9 to 10, so the truncates premise was wrong.",
                         "newTest":"[TestMethod]\\npublic void PlaceOrder_TotalWithFraction_RoundsToInt()\\n{\\n    var sut = new OrderProcessor();\\n    int result = sut.PlaceOrder(3, 9.9m);\\n    Assert.AreEqual(13, result);\\n}",
                         "noEdit":false}"""));
        when(runner.run(any(), anyString(), any())).thenReturn(build(BuildStatus.GREEN, 23, 23, 0));

        RepairAttempt attempt = service.repairAttempt(request(1));

        assertThat(attempt.tier()).isEqualTo("Mechanical");
        assertThat(attempt.tag()).isEqualTo("green");
        assertThat(attempt.netFaithful()).isTrue();
        assertThat(attempt.gate().ok()).isTrue();
        assertThat(attempt.rerun().green()).isTrue();
        assertThat(attempt.code()).contains("Assert.AreEqual(13, result);");
    }

    @Test
    void repairAttempt_greenRepairRecordsTheClassSuiteForDownload() {
        MigrationSession session = session();
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(ai("""
                        {"rationale":"CInt banker-rounds 9.9 to 10, so the truncates premise was wrong.",
                         "newTest":"[TestMethod]\\npublic void PlaceOrder_TotalWithFraction_RoundsToInt()\\n{\\n    var sut = new OrderProcessor();\\n    int result = sut.PlaceOrder(3, 9.9m);\\n    Assert.AreEqual(13, result);\\n}",
                         "noEdit":false}"""));
        when(runner.run(any(), anyString(), any())).thenReturn(build(BuildStatus.GREEN, 23, 23, 0));

        RepairAttempt attempt = service.repairAttempt(request(1));

        // A repaired-green class becomes downloadable: its corrected suite is retained per class.
        assertThat(session.getBaselineSuites()).containsKey("OrderProcessor");
        assertThat(session.getBaselineSuites().get("OrderProcessor").code())
                .isEqualTo(attempt.code());
    }

    @Test
    void repairAttempt_quarantinedClassIsNotRecordedForDownload() {
        MigrationSession session = session();
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(ai("""
                        {"rationale":"still wrong",
                         "newTest":"[TestMethod]\\npublic void PlaceOrder_TotalWithFraction_RoundsToInt()\\n{\\n    var sut = new OrderProcessor();\\n    int result = sut.PlaceOrder(3, 9.9m);\\n    Assert.AreEqual(13, result);\\n}",
                         "noEdit":false}"""));
        when(runner.run(any(), anyString(), any())).thenReturn(build(BuildStatus.RED, 23, 22, 1));

        service.repairAttempt(request(1));

        // A still-red (headed-for-quarantine) repair leaves nothing downloadable.
        assertThat(session.getBaselineSuites()).doesNotContainKey("OrderProcessor");
    }

    @Test
    void repairAttempt_escalatesWithoutRunningWhenTheModelReportsNoEdit() {
        MigrationSession session = session();
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(ai("""
                        {"rationale":"The code threw where a value was expected — no literal to swap.",
                         "newTest":"","noEdit":true}"""));

        RepairAttempt attempt = service.repairAttempt(request(1));

        assertThat(attempt.tag()).isEqualTo("escalated");
        assertThat(attempt.gate().ok()).isFalse();
        assertThat(attempt.rerun()).isNull();
        verify(runner, never()).run(any(), anyString(), any());
    }

    @Test
    void repairAttempt_rejectsAnAlwaysPassRewriteWithoutCountingItAsAWin() {
        MigrationSession session = session();
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(ai("""
                        {"rationale":"Making it pass.",
                         "newTest":"[TestMethod]\\npublic void X(){ Assert.IsTrue(true); }",
                         "noEdit":false}"""));

        RepairAttempt attempt = service.repairAttempt(request(3));

        assertThat(attempt.gate().ok()).isFalse();
        assertThat(attempt.tag()).isEqualTo("nofix");
        verify(runner, never()).run(any(), anyString(), any());
    }

    @Test
    void repairAttempt_flagsANonDeterministicValueThatDiffersEveryRun() {
        MigrationSession session = session();
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(ai("""
                        {"rationale":"Swapping the expected value to the observed one.",
                         "newTest":"[TestMethod]\\npublic void PlaceOrder_TotalWithFraction_TruncatesToInt()\\n{\\n    var sut = new OrderProcessor();\\n    int result = sut.PlaceOrder(3, 9.9m);\\n    Assert.AreEqual(1043, result);\\n}",
                         "noEdit":false}"""));
        // Each run reports a different Actual — the value is time-based, not fixable.
        AtomicInteger runs = new AtomicInteger();
        when(runner.run(any(), anyString(), any())).thenAnswer(inv -> {
            MigrationSession s = inv.getArgument(0);
            int n = runs.incrementAndGet();
            s.setFailureMessages(new java.util.HashMap<>(Map.of(
                    "PlaceOrder_TotalWithFraction_TruncatesToInt",
                    "Assert.AreEqual failed. Expected:<1043>. Actual:<" + (1000 + n * 7) + ">.")));
            return build(BuildStatus.RED, 23, 22, 1);
        });

        RepairAttempt attempt = service.repairAttempt(request(1));

        assertThat(attempt.tag()).isEqualTo("flag");
        assertThat(attempt.netFaithful()).isFalse();
        assertThat(attempt.rerun().green()).isFalse();
    }
}
