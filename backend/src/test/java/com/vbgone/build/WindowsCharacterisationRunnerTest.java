package com.vbgone.build;

import com.vbgone.model.BuildResult;
import com.vbgone.model.BuildStatus;
import com.vbgone.model.MigrationSession;
import com.vbgone.model.TestsResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WindowsCharacterisationRunnerTest {

    private static final String GREEN_TRX = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TestRun xmlns="http://microsoft.com/schemas/VisualStudio/TeamTest/2010">
              <ResultSummary outcome="Completed">
                <Counters total="3" executed="3" passed="3" failed="0" error="0" />
              </ResultSummary>
            </TestRun>""";

    @Mock
    private WindowsRunnerTransport transport;

    private WindowsCharacterisationRunner runner;

    @BeforeEach
    void setUp() {
        runner = new WindowsCharacterisationRunner(transport);
    }

    private MigrationSession session() {
        MigrationSession s = new MigrationSession("s1");
        s.setVbContent("Public Class Settlement\nEnd Class");
        return s;
    }

    private TestsResult suite() {
        return new TestsResult("s1", "Settlement", "SettlementBaselineTests",
                "[TestClass] public class SettlementBaselineTests { [TestMethod] public void T() {} }", 1);
    }

    @Test
    void greenTrxFromRunnerProducesGreenBuild() throws Exception {
        when(transport.characterise(anyString(), anyMap())).thenReturn(GREEN_TRX);

        BuildResult result = runner.run(session(), "Settlement", suite());

        assertThat(result.buildStatus()).isEqualTo(BuildStatus.GREEN);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.passed()).isEqualTo(3);
    }

    @Test
    void transportFailureSurfacesAsError() throws Exception {
        when(transport.characterise(anyString(), anyMap())).thenThrow(new IOException("dispatch boom"));

        BuildResult result = runner.run(session(), "Settlement", suite());

        assertThat(result.buildStatus()).isEqualTo(BuildStatus.ERROR);
        assertThat(result.errors()).anyMatch(e -> e.contains("dispatch boom"));
    }

    @Test
    void projectFilesAreNet48WithLangVersionPin() {
        Map<String, String> files = runner.projectFiles(
                "Settlement", "Public Class Settlement\nEnd Class", suite());

        assertThat(files).containsOnlyKeys(
                "runner-workspace/Settlement.Vb/Settlement.vbproj",
                "runner-workspace/Settlement.Vb/Settlement.vb",
                "runner-workspace/Settlement.Baseline/Settlement.Baseline.csproj",
                "runner-workspace/Settlement.Baseline/SettlementBaselineTests.cs");

        assertThat(files.get("runner-workspace/Settlement.Baseline/Settlement.Baseline.csproj"))
                .contains("net48")
                .contains("<LangVersion>latest</LangVersion>");
        assertThat(files.get("runner-workspace/Settlement.Vb/Settlement.vbproj"))
                .contains("net48")
                .contains("System.Data.Linq")
                // WinForms refs are essential — the bucket is dominated by Form-inheriting classes.
                .contains("System.Windows.Forms")
                .contains("System.Drawing")
                // ...and a project-level Import, so an unqualified `Inherits Form` resolves (BC30002).
                .contains("<Import Include=\"System.Windows.Forms\" />");
    }
}
