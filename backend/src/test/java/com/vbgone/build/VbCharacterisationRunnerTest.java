package com.vbgone.build;

import com.vbgone.model.BuildResult;
import com.vbgone.model.BuildStatus;
import com.vbgone.model.MigrationSession;
import com.vbgone.model.TestsResult;
import com.vbgone.service.ProcessOutput;
import com.vbgone.service.ProcessRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VbCharacterisationRunnerTest {

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
            </TestRun>""";

    @Mock
    private ProcessRunner processRunner;

    @TempDir
    Path tempDir;

    private VbCharacterisationRunner runner;

    @BeforeEach
    void setUp() {
        runner = new VbCharacterisationRunner(tempDir.toString(), "dotnet-runner", processRunner);
    }

    private MigrationSession session(String id) {
        MigrationSession s = new MigrationSession(id);
        s.setVbContent("Public Class OrderProcessor\nEnd Class");
        return s;
    }

    private TestsResult suite(String id) {
        return new TestsResult(id, "OrderProcessor", "OrderProcessorBaselineTests",
                "[TestClass] public class OrderProcessorBaselineTests { [TestMethod] public void T() {} }", 1);
    }

    private void writeTrx(String sessionId, String trx) throws IOException {
        Path dir = tempDir.resolve(sessionId).resolve("assure")
                .resolve("OrderProcessor.Baseline").resolve("TestResults");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("results.trx"), trx);
    }

    @Test
    void run_greenWhenSuitePassesAgainstOriginal() throws Exception {
        when(processRunner.run(anyList())).thenAnswer(inv -> {
            writeTrx("s1", GREEN_TRX);
            return new ProcessOutput(0, "Passed!", "");
        });

        BuildResult result = runner.run(session("s1"), "OrderProcessor", suite("s1"));

        assertThat(result.buildStatus()).isEqualTo(BuildStatus.GREEN);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.passed()).isEqualTo(3);
    }

    @Test
    void run_redWhenAssertionsFail() throws Exception {
        when(processRunner.run(anyList())).thenAnswer(inv -> {
            writeTrx("s1", RED_TRX);
            return new ProcessOutput(1, "", "Test Run Failed.");
        });

        BuildResult result = runner.run(session("s1"), "OrderProcessor", suite("s1"));

        assertThat(result.buildStatus()).isEqualTo(BuildStatus.RED);
        assertThat(result.failed()).isEqualTo(2);
    }

    @Test
    void run_errorWhenVbDoesNotCompile() throws Exception {
        // No .trx produced + non-zero exit = a compile failure (e.g. WinForms-coupled VB on Linux).
        when(processRunner.run(anyList()))
                .thenReturn(new ProcessOutput(1, "", "OrderProcessor.vb(3): error BC30002: Type not found"));

        BuildResult result = runner.run(session("s1"), "OrderProcessor", suite("s1"));

        assertThat(result.buildStatus()).isEqualTo(BuildStatus.ERROR);
        assertThat(result.total()).isZero();
        assertThat(result.errors()).anyMatch(e -> e.contains("error BC30002"));
    }

    @Test
    void run_compilesTheAssurableSubsetWhenPinned() throws Exception {
        // When a net-ready subset is pinned (portfolio estate), compile THAT — not the whole
        // uploaded source, which may contain WinForms classes that can't build headless.
        MigrationSession s = session("s1");
        s.setAssurableSource("Public Class OrderProcessor\n"
                + "  Public Function Fee(x As Decimal) As Decimal\n    Return x * 0.1D\n  End Function\n"
                + "End Class");
        when(processRunner.run(anyList())).thenAnswer(inv -> {
            writeTrx("s1", GREEN_TRX);
            return new ProcessOutput(0, "Passed!", "");
        });

        runner.run(s, "OrderProcessor", suite("s1"));

        Path vb = tempDir.resolve("s1").resolve("assure")
                .resolve("OrderProcessor.Vb").resolve("OrderProcessor.vb");
        // The subset (not the session's raw vbContent) is what got written.
        assertThat(Files.readString(vb)).contains("Public Function Fee(")
                .doesNotContain("End Class\nEnd Class");
    }

    @Test
    void run_writesOriginalVbAndBaselineSuiteToWorkspace() throws Exception {
        when(processRunner.run(anyList())).thenAnswer(inv -> {
            writeTrx("s1", GREEN_TRX);
            return new ProcessOutput(0, "Passed!", "");
        });

        runner.run(session("s1"), "OrderProcessor", suite("s1"));

        Path assure = tempDir.resolve("s1").resolve("assure");
        assertThat(Files.readString(assure.resolve("OrderProcessor.Vb").resolve("OrderProcessor.vb")))
                .contains("Public Class OrderProcessor");
        assertThat(assure.resolve("OrderProcessor.Vb").resolve("OrderProcessor.vbproj")).exists();
        assertThat(Files.readString(assure.resolve("OrderProcessor.Baseline")
                .resolve("OrderProcessor.Baseline.csproj")))
                .contains("../OrderProcessor.Vb/OrderProcessor.vbproj");
        assertThat(assure.resolve("OrderProcessor.Baseline").resolve("OrderProcessorBaselineTests.cs")).exists();
    }
}
