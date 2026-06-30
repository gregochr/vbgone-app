package com.vbgone.build;

import com.vbgone.model.BuildStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
