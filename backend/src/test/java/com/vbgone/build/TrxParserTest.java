package com.vbgone.build;

import com.vbgone.model.BuildStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrxParserTest {

    private static final String GREEN_TRX = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TestRun xmlns="http://microsoft.com/schemas/VisualStudio/TeamTest/2010">
              <ResultSummary outcome="Completed">
                <Counters total="3" executed="3" passed="3" failed="0" error="0" />
              </ResultSummary>
            </TestRun>""";

    private static final String RED_TRX = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TestRun xmlns="http://microsoft.com/schemas/VisualStudio/TeamTest/2010">
              <ResultSummary outcome="Failed">
                <Counters total="3" executed="3" passed="1" failed="2" error="0" />
              </ResultSummary>
              <Results>
                <UnitTestResult testName="SplitPerHead_ZeroHeadcount_ThrowsDivideByZero" outcome="Failed">
                  <Output><ErrorInfo><Message>Expected DivideByZeroException</Message></ErrorInfo></Output>
                </UnitTestResult>
              </Results>
            </TestRun>""";

    /** A run where the tests threw at load (Error) rather than assertion-failing (Failed). */
    private static final String ERRORED_TRX = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TestRun xmlns="http://microsoft.com/schemas/VisualStudio/TeamTest/2010">
              <ResultSummary outcome="Failed">
                <Counters total="3" executed="3" passed="1" failed="0" error="2" timeout="0" aborted="0" />
              </ResultSummary>
              <Results>
                <UnitTestResult testName="Ctor_ThrowsAtLoad" outcome="Error">
                  <Output><ErrorInfo><Message>TypeInitializationException</Message></ErrorInfo></Output>
                </UnitTestResult>
                <UnitTestResult testName="Reads_Config" outcome="Error">
                  <Output><ErrorInfo><Message>FileNotFoundException</Message></ErrorInfo></Output>
                </UnitTestResult>
                <UnitTestResult testName="Adds_Numbers" outcome="Passed" />
              </Results>
            </TestRun>""";

    private static final String TIMEOUT_TRX = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TestRun xmlns="http://microsoft.com/schemas/VisualStudio/TeamTest/2010">
              <ResultSummary outcome="Failed">
                <Counters total="2" executed="2" passed="1" failed="0" error="0" timeout="1" aborted="0" />
              </ResultSummary>
              <Results>
                <UnitTestResult testName="Loops_Forever" outcome="Timeout" />
                <UnitTestResult testName="Returns_Fast" outcome="Passed" />
              </Results>
            </TestRun>""";

    /** Two Failed results, one of which carries no &lt;Message&gt;, plus a Passed to ignore. */
    private static final String MULTI_FAIL_TRX = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TestRun xmlns="http://microsoft.com/schemas/VisualStudio/TeamTest/2010">
              <ResultSummary outcome="Failed">
                <Counters total="3" executed="3" passed="1" failed="2" error="0" />
              </ResultSummary>
              <Results>
                <UnitTestResult testName="Add_ReturnsSum" outcome="Failed">
                  <Output><ErrorInfo><Message>Expected 3 but was 2</Message></ErrorInfo></Output>
                </UnitTestResult>
                <UnitTestResult testName="Subtract_ReturnsDifference" outcome="Failed" />
                <UnitTestResult testName="Multiply_ReturnsProduct" outcome="Passed" />
              </Results>
            </TestRun>""";

    @Test
    void parse_erroredRunWithNoAssertionFailures_isRedNotFalseGreen() {
        // Regression: failed="0" but error="2" must NOT report GREEN.
        var parsed = TrxParser.parse("s1", ERRORED_TRX);
        assertThat(parsed.result().buildStatus()).isEqualTo(BuildStatus.RED);
        // error/timeout/aborted roll into the failed count so it is never < the real failures.
        assertThat(parsed.result().failed()).isEqualTo(2);
        assertThat(parsed.result().failedTests())
                .containsExactly("Ctor_ThrowsAtLoad", "Reads_Config");
        assertThat(parsed.failureMessages())
                .containsEntry("Ctor_ThrowsAtLoad", "TypeInitializationException")
                .containsEntry("Reads_Config", "FileNotFoundException");
    }

    @Test
    void parse_timeoutCountsAsFailure() {
        var parsed = TrxParser.parse("s1", TIMEOUT_TRX);
        assertThat(parsed.result().buildStatus()).isEqualTo(BuildStatus.RED);
        assertThat(parsed.result().failed()).isEqualTo(1);
        assertThat(parsed.result().failedTests()).containsExactly("Loops_Forever");
    }

    @Test
    void parse_multipleFailures_collectsAllNamesAndOmitsMissingMessage() {
        var parsed = TrxParser.parse("s1", MULTI_FAIL_TRX);
        assertThat(parsed.result().buildStatus()).isEqualTo(BuildStatus.RED);
        assertThat(parsed.result().failedTests())
                .containsExactly("Add_ReturnsSum", "Subtract_ReturnsDifference");
        assertThat(parsed.failureMessages())
                .containsOnlyKeys("Add_ReturnsSum")
                .containsEntry("Add_ReturnsSum", "Expected 3 but was 2");
    }

    @Test
    void parse_missingCounters_throwsClearError() {
        String noCounters = """
                <?xml version="1.0" encoding="UTF-8"?>
                <TestRun xmlns="http://microsoft.com/schemas/VisualStudio/TeamTest/2010">
                  <ResultSummary outcome="Completed" />
                </TestRun>""";
        assertThatThrownBy(() -> TrxParser.parse("s1", noCounters))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Counters");
    }

    @Test
    void parse_malformedXml_throws() {
        assertThatThrownBy(() -> TrxParser.parse("s1", "not-a-trx <<<"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse .trx");
    }

    @Test
    void parse_green() {
        var parsed = TrxParser.parse("s1", GREEN_TRX);
        assertThat(parsed.result().buildStatus()).isEqualTo(BuildStatus.GREEN);
        assertThat(parsed.result().total()).isEqualTo(3);
        assertThat(parsed.result().passed()).isEqualTo(3);
        assertThat(parsed.result().failed()).isZero();
        assertThat(parsed.failureMessages()).isEmpty();
    }

    @Test
    void parse_redCapturesFailingTestAndMessage() {
        var parsed = TrxParser.parse("s1", RED_TRX);
        assertThat(parsed.result().buildStatus()).isEqualTo(BuildStatus.RED);
        assertThat(parsed.result().failed()).isEqualTo(2);
        assertThat(parsed.result().failedTests())
                .containsExactly("SplitPerHead_ZeroHeadcount_ThrowsDivideByZero");
        assertThat(parsed.failureMessages())
                .containsEntry("SplitPerHead_ZeroHeadcount_ThrowsDivideByZero", "Expected DivideByZeroException");
    }
}
