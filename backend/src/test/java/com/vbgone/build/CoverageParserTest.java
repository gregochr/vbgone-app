package com.vbgone.build;

import com.vbgone.build.CoverageParser.Coverage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CoverageParserTest {

    // A Coverlet Cobertura report with two modules: the code under test (OrderProcessor, 85% lines /
    // 70% branches) and the test project itself (OrderProcessor.Tests, 100%). The root blends both.
    private static final String COBERTURA = """
            <?xml version="1.0" encoding="utf-8"?>
            <coverage line-rate="0.925" branch-rate="0.86" version="1.9">
              <packages>
                <package name="OrderProcessor" line-rate="0.85" branch-rate="0.7">
                  <classes/>
                </package>
                <package name="OrderProcessor.Tests" line-rate="1" branch-rate="1">
                  <classes/>
                </package>
              </packages>
            </coverage>""";

    private void writeReport(Path dir, String xml) throws IOException {
        Path guidDir = dir.resolve("TestResults").resolve("abc-123-guid");
        Files.createDirectories(guidDir);
        Files.writeString(guidDir.resolve("coverage.cobertura.xml"), xml);
    }

    @Test
    void prefersTheModuleUnderTestOverTheAggregate(@TempDir Path dir) throws IOException {
        writeReport(dir, COBERTURA);

        Coverage cov = CoverageParser.parse(dir.resolve("TestResults"), "OrderProcessor");

        // The module rates, not the blended root (which counts the 100% test project).
        assertThat(cov.linePercent()).isEqualTo(85.0);
        assertThat(cov.branchPercent()).isEqualTo(70.0);
    }

    @Test
    void fallsBackToRootWhenModuleNotFound(@TempDir Path dir) throws IOException {
        writeReport(dir, COBERTURA);

        Coverage cov = CoverageParser.parse(dir.resolve("TestResults"), "NoSuchModule");

        assertThat(cov.linePercent()).isEqualTo(92.5);
        assertThat(cov.branchPercent()).isEqualTo(86.0);
    }

    @Test
    void roundsToOneDecimalPlace(@TempDir Path dir) throws IOException {
        writeReport(dir, """
                <?xml version="1.0" encoding="utf-8"?>
                <coverage line-rate="0.8337" branch-rate="0.6661">
                  <packages>
                    <package name="Calc" line-rate="0.8337" branch-rate="0.6661"/>
                  </packages>
                </coverage>""");

        Coverage cov = CoverageParser.parse(dir.resolve("TestResults"), "Calc");

        assertThat(cov.linePercent()).isEqualTo(83.4);
        assertThat(cov.branchPercent()).isEqualTo(66.6);
    }

    @Test
    void returnsNullBranchWhenAttributeAbsent(@TempDir Path dir) throws IOException {
        writeReport(dir, """
                <coverage line-rate="0.9">
                  <packages><package name="Calc" line-rate="0.9"/></packages>
                </coverage>""");

        Coverage cov = CoverageParser.parse(dir.resolve("TestResults"), "Calc");

        assertThat(cov.linePercent()).isEqualTo(90.0);
        assertThat(cov.branchPercent()).isNull();
    }

    @Test
    void returnsEmptyWhenNoReportPresent(@TempDir Path dir) {
        Coverage cov = CoverageParser.parse(dir.resolve("TestResults"), "Anything");

        assertThat(cov.linePercent()).isNull();
        assertThat(cov.branchPercent()).isNull();
    }

    @Test
    void returnsEmptyOnMalformedXml(@TempDir Path dir) throws IOException {
        writeReport(dir, "not xml at all <<<");

        Coverage cov = CoverageParser.parse(dir.resolve("TestResults"), "Anything");

        assertThat(cov.linePercent()).isNull();
        assertThat(cov.branchPercent()).isNull();
    }

    @Test
    void picksTheNewestReportWhenSeveralExist(@TempDir Path dir) throws IOException {
        Path results = dir.resolve("TestResults");
        Path older = results.resolve("older-guid");
        Path newer = results.resolve("newer-guid");
        Files.createDirectories(older);
        Files.createDirectories(newer);
        Files.writeString(older.resolve("coverage.cobertura.xml"), """
                <coverage line-rate="0.5"><packages><package name="Calc" line-rate="0.5" branch-rate="0.5"/></packages></coverage>""");
        Path newest = newer.resolve("coverage.cobertura.xml");
        Files.writeString(newest, """
                <coverage line-rate="0.9"><packages><package name="Calc" line-rate="0.9" branch-rate="0.8"/></packages></coverage>""");
        // Make the "newer" report unambiguously the most recently modified.
        older.resolve("coverage.cobertura.xml").toFile().setLastModified(1_000_000L);
        newest.toFile().setLastModified(2_000_000L);

        Coverage cov = CoverageParser.parse(results, "Calc");

        assertThat(cov.linePercent()).isEqualTo(90.0);
        assertThat(cov.branchPercent()).isEqualTo(80.0);
    }
}
