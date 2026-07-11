package com.vbgone.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vbgone.ai.AiProviderRegistry;
import com.vbgone.ai.AiRequestOptions;
import com.vbgone.ai.anthropic.AnthropicProvider;
import com.vbgone.ai.github.GitHubModelsProvider;
import com.vbgone.build.VbCharacterisationRunner;
import com.vbgone.model.*;
import com.vbgone.prompt.CSharpPrompts;
import com.vbgone.session.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssureServiceTest {

    @Mock
    private ClaudeClient claudeClient;

    @Mock
    private SessionStore sessionStore;

    @Mock
    private VbCharacterisationRunner runner;

    private AssureService service;

    @BeforeEach
    void setUp() {
        AnthropicProvider anthropicProvider = new AnthropicProvider(claudeClient);
        GitHubModelsProvider copilotProvider = new GitHubModelsProvider("", new ObjectMapper());
        AiProviderRegistry registry = new AiProviderRegistry(List.of(anthropicProvider, copilotProvider));
        AiCallSupport aiCallSupport = new AiCallSupport(registry);
        service = new AssureService(registry, sessionStore, new ObjectMapper(), runner, aiCallSupport);
        // getOrThrow delegates to the (stubbed) get(); let the real method run over the mock.
        lenient().when(sessionStore.getOrThrow(anyString())).thenCallRealMethod();
    }

    private ClaudeClient.ClaudeResponse claudeResponse(String text) {
        return new ClaudeClient.ClaudeResponse(text, 100, 50);
    }

    private MigrationSession session(String id) {
        MigrationSession s = new MigrationSession(id);
        s.setVbContent("Public Class OrderProcessor\nEnd Class");
        return s;
    }

    private BuildResult build(BuildStatus status, int total, int passed, int failed) {
        return new BuildResult("s1", status, total, passed, failed, List.of(), List.of());
    }

    private BuildResult build(BuildStatus status, int total, int passed, int failed,
                              List<String> failedTests) {
        return new BuildResult("s1", status, total, passed, failed, List.of(), failedTests);
    }

    // ── generateBaseline ──

    @Test
    void generateBaseline_parsesPublicSurfaceAndDefects() {
        MigrationSession session = session("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse("""
                        {
                          "members": [
                            { "signature": "decimal CalculateTotal(IReadOnlyList<LineItem> items)", "defect": null },
                            { "signature": "decimal SplitPerHead(decimal total, int headcount)", "defect": "throws DivideByZeroException when headcount = 0" }
                          ]
                        }"""));

        BaselineResult result = service.generateBaseline("s1", "OrderProcessor", AiRequestOptions.defaults());

        assertThat(result.surfaceFile()).isEqualTo("OrderProcessor.dll · public surface");
        assertThat(result.members()).hasSize(2);
        assertThat(result.members().get(0).defect()).isNull();
        assertThat(result.members().get(1).defect()).contains("DivideByZeroException");
        assertThat(session.getBaselineResult()).isEqualTo(result);
    }

    @Test
    void generateBaseline_throwsOnMissingSession() {
        when(sessionStore.get("bad")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.generateBaseline("bad", "OrderProcessor", AiRequestOptions.defaults()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session not found");
    }

    // ── runBaselineTests ──

    @Test
    void runBaselineTests_greenRunIsFaithful() {
        MigrationSession session = session("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse(
                        "[TestClass] public class OrderProcessorBaselineTests { [TestMethod] public void A() {} }"));
        when(runner.run(any(), eq("OrderProcessor"), any()))
                .thenReturn(build(BuildStatus.GREEN, 43, 43, 0));

        BaselineTestsResult result = service.runBaselineTests("s1", "OrderProcessor", AiRequestOptions.defaults());

        assertThat(result.netFaithful()).isTrue();
        assertThat(result.testClassName()).isEqualTo("OrderProcessorBaselineTests");
        assertThat(result.testCount()).isEqualTo(43);
        assertThat(result.build().buildStatus()).isEqualTo(BuildStatus.GREEN);
        assertThat(result.failures()).isEmpty();
        assertThat(session.getBaselineSuite()).isNotNull();
        assertThat(session.getNetBuild().buildStatus()).isEqualTo(BuildStatus.GREEN);
    }

    @Test
    void runBaselineTests_failingRunSurfacesTheDriftedAssertions() {
        MigrationSession session = session("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse(
                        "[TestClass] public class OrderProcessorBaselineTests { [TestMethod] public void A() {} }"));
        // The runner stores per-assertion messages on the session (as the real one does).
        when(runner.run(any(), eq("OrderProcessor"), any())).thenAnswer(inv -> {
            MigrationSession s = inv.getArgument(0);
            s.setFailureMessages(Map.of("ApplyDiscount_UnknownCode_ReturnsSubtotalUnchanged",
                    "Expected: 100 but was: 90"));
            return build(BuildStatus.RED, 43, 42, 1,
                    List.of("ApplyDiscount_UnknownCode_ReturnsSubtotalUnchanged"));
        });

        BaselineTestsResult result = service.runBaselineTests("s1", "OrderProcessor", AiRequestOptions.defaults());

        assertThat(result.netFaithful()).isFalse();
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).name())
                .isEqualTo("ApplyDiscount_UnknownCode_ReturnsSubtotalUnchanged");
        assertThat(result.failures().get(0).message()).contains("but was: 90");
    }

    @Test
    void runBaselineTests_greenRunRecordsSuitePerClassForDownload() {
        MigrationSession session = session("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse(
                        "[TestClass] public class OrderProcessorBaseline { [TestMethod] public void A() {} }"));
        when(runner.run(any(), eq("OrderProcessor"), any()))
                .thenReturn(build(BuildStatus.GREEN, 43, 43, 0));

        BaselineTestsResult result = service.runBaselineTests("s1", "OrderProcessor", AiRequestOptions.defaults());

        // A faithful run retains the suite keyed by class so it can be downloaded later.
        assertThat(session.getBaselineSuites()).containsKey("OrderProcessor");
        assertThat(session.getBaselineSuites().get("OrderProcessor").code()).isEqualTo(result.code());
    }

    @Test
    void runBaselineTests_redRunDoesNotRecordSuiteForDownload() {
        MigrationSession session = session("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse(
                        "[TestClass] public class OrderProcessorBaseline { [TestMethod] public void A() {} }"));
        when(runner.run(any(), eq("OrderProcessor"), any()))
                .thenReturn(build(BuildStatus.RED, 43, 42, 1, List.of("A")));

        service.runBaselineTests("s1", "OrderProcessor", AiRequestOptions.defaults());

        // An unfaithful (red) suite is never offered for download.
        assertThat(session.getBaselineSuites()).doesNotContainKey("OrderProcessor");
    }

    // ── augmentBaselineTests ──

    @Test
    void augmentBaselineTests_usesTheAugmentPromptWithTheCurrentSuiteAndCoverage() {
        MigrationSession session = session("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse(
                        "[TestClass] public class OrderProcessorBaselineTests { [TestMethod] public void A() {} [TestMethod] public void B() {} }"));
        when(runner.run(any(), eq("OrderProcessor"), any()))
                .thenReturn(build(BuildStatus.GREEN, 60, 60, 0));

        String currentSuite = "[TestClass] public class OrderProcessorBaselineTests { [TestMethod] public void A() {} }";
        BaselineTestsResult result = service.augmentBaselineTests(
                "s1", "OrderProcessor", currentSuite, 45.5, AiRequestOptions.defaults());

        assertThat(result.netFaithful()).isTrue();
        assertThat(result.testCount()).isEqualTo(60);

        // Prove it drove the AUGMENT prompt, and fed the model the existing suite + the coverage gap.
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).sendWithCachedSystemPrompt(system.capture(), user.capture(), any(), anyLong());
        assertThat(system.getValue()).isEqualTo(CSharpPrompts.AUGMENT_BASELINE_TESTS_SYSTEM_PROMPT);
        assertThat(user.getValue()).contains("46% of the class").contains(currentSuite);
    }

    @Test
    void augmentBaselineTests_throwsOnMissingSession() {
        when(sessionStore.get("bad")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.augmentBaselineTests("bad", "OrderProcessor", "code", null, AiRequestOptions.defaults()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session not found");
    }

    @Test
    void quarantineBaseline_ignoresFailingTestRerunsGreenAndRecordsDownloadableSuite() {
        MigrationSession session = session("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        // With the failing test set aside, the remaining tests pass → green.
        when(runner.run(any(), eq("OrderProcessor"), any()))
                .thenReturn(build(BuildStatus.GREEN, 42, 42, 0));
        String suite = """
                [TestClass]
                public class OrderProcessorBaselineTests
                {
                    [TestMethod]
                    public void Bad() { Assert.AreEqual(1, 2); }
                    [TestMethod]
                    public void Good() { Assert.AreEqual(1, 1); }
                }
                """;

        BaselineTestsResult result = service.quarantineBaseline(
                "s1", "OrderProcessor", suite, List.of("Bad"));

        assertThat(result.netFaithful()).isTrue();
        assertThat(result.code()).contains("[Ignore("); // the failing test was set aside, not removed
        assertThat(result.code()).contains("public void Bad()").contains("public void Good()");
        // A class with a quarantined test is still downloadable (with the passing tests).
        assertThat(session.getBaselineSuites()).containsKey("OrderProcessor");
        assertThat(session.getBaselineSuites().get("OrderProcessor").code()).contains("[Ignore(");
    }

    @Test
    void quarantineBaseline_setsAsideTheActuallyFailingTestWhenNamesDoNotMatch() {
        MigrationSession session = session("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        // First run (the supplied name doesn't match, so nothing is ignored) → red on the real
        // failing test; second run (after setting THAT aside) → green.
        when(runner.run(any(), eq("OrderProcessor"), any()))
                .thenReturn(build(BuildStatus.RED, 5, 4, 1, List.of("ActuallyFailing")))
                .thenReturn(build(BuildStatus.GREEN, 5, 5, 0));
        String suite = """
                [TestClass]
                public class OrderProcessorBaselineTests
                {
                    [TestMethod]
                    public void ActuallyFailing() { Assert.AreEqual(1, 2); }
                    [TestMethod]
                    public void Good() { Assert.AreEqual(1, 1); }
                }
                """;

        BaselineTestsResult result = service.quarantineBaseline(
                "s1", "OrderProcessor", suite, List.of("WrongName"));

        assertThat(result.netFaithful()).isTrue();
        assertThat(result.code()).contains("[Ignore("); // the actually-failing test was set aside
        assertThat(session.getBaselineSuites()).containsKey("OrderProcessor");
    }

    @Test
    void quarantineBaseline_thatCannotBeMadeGreenIsNotRecorded() {
        MigrationSession session = session("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        // A compile-style failure with no runnable failing tests to set aside → can't converge.
        when(runner.run(any(), eq("OrderProcessor"), any()))
                .thenReturn(build(BuildStatus.RED, 0, 0, 0));

        BaselineTestsResult result = service.quarantineBaseline(
                "s1", "OrderProcessor",
                "[TestClass]\npublic class X {\n    [TestMethod]\n    public void Bad() {}\n}",
                List.of("Bad"));

        assertThat(result.netFaithful()).isFalse();
        assertThat(session.getBaselineSuites()).doesNotContainKey("OrderProcessor");
    }

    @Test
    void rerunBaselineTests_runsEditedNetWithNoAiCall() {
        MigrationSession session = session("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(runner.run(any(), eq("OrderProcessor"), any()))
                .thenReturn(build(BuildStatus.GREEN, 43, 43, 0));

        BaselineTestsResult result = service.rerunBaselineTests("s1", "OrderProcessor",
                "[TestClass] public class OrderProcessorBaselineTests { [TestMethod] public void A() {} }");

        assertThat(result.netFaithful()).isTrue();
        assertThat(result.failures()).isEmpty();
        // Re-running an edited net must NOT call the model.
        verify(claudeClient, never()).sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong());
        assertThat(session.getTokenUsages()).isEmpty();
    }

    @Test
    void runBaselineTests_reattachesTestClassAttributeStrippedByCodeFencing() {
        // The prompt tells the model to omit `using` lines, so its output starts with the
        // [TestClass] attribute. stripCodeFences anchors on "public class" and drops that line —
        // leaving a suite MSTest discovers 0 tests in. The service must put the attribute back.
        MigrationSession session = session("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse("""
                        [TestClass]
                        public class OrderProcessorBaselineTests
                        {
                            [TestMethod]
                            public void SplitPerHead_ZeroHeadcount_Throws() { }
                        }"""));
        when(runner.run(any(), eq("OrderProcessor"), any()))
                .thenReturn(build(BuildStatus.GREEN, 1, 1, 0));

        service.runBaselineTests("s1", "OrderProcessor", AiRequestOptions.defaults());

        // The suite actually handed to the CLR runner must carry [TestClass].
        assertThat(session.getBaselineSuite().code()).contains("[TestClass]");
    }

    @Test
    void ensureTestClassAttribute_reattachesWhenMissing() {
        String stripped = "public class OrderProcessorBaselineTests\n{\n    [TestMethod]\n    public void A() {}\n}";
        String fixed = CharacterisationSuiteEditor.ensureTestClassAttribute(stripped);
        assertThat(fixed).contains("[TestClass]\npublic class OrderProcessorBaselineTests");
    }

    @Test
    void ensureTestClassAttribute_leavesCodeThatAlreadyHasItUntouched() {
        String already = "[TestClass]\npublic class X\n{\n    [TestMethod]\n    public void A() {}\n}";
        assertThat(CharacterisationSuiteEditor.ensureTestClassAttribute(already)).isEqualTo(already);
    }

    @Test
    void ensureTestClassAttribute_ignoresCodeWithoutTestMethods() {
        String noTests = "public class Helper\n{\n    public int A() => 1;\n}";
        assertThat(CharacterisationSuiteEditor.ensureTestClassAttribute(noTests)).isEqualTo(noTests);
    }

    @Test
    void runBaselineTests_compileErrorIsNotFaithful() {
        MigrationSession session = session("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(claudeClient.sendWithCachedSystemPrompt(anyString(), anyString(), any(), anyLong()))
                .thenReturn(claudeResponse(
                        "[TestClass] public class OrderProcessorBaselineTests { [TestMethod] public void A() {} }"));
        when(runner.run(any(), eq("OrderProcessor"), any()))
                .thenReturn(build(BuildStatus.ERROR, 0, 0, 0));

        BaselineTestsResult result = service.runBaselineTests("s1", "OrderProcessor", AiRequestOptions.defaults());

        assertThat(result.netFaithful()).isFalse();
        assertThat(result.build().buildStatus()).isEqualTo(BuildStatus.ERROR);
    }
}
