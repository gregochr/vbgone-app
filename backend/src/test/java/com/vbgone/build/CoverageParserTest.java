package com.vbgone.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CoverageParserTest {

    // A Coverlet Cobertura report with two modules: the code under test (OrderProcessor, 85% lines)
    // and the test project itself (OrderProcessor.Tests, 100%). The root aggregate blends both.
    private static final String COBERTURA = """
            <?xml version="1.0" encoding="utf-8"?>
            <coverage line-rate="0.925" branch-rate="0.8" version="1.9">
              <packages>
                <package name="OrderProcessor" line-rate="0.85" branch-rate="0.75">
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

        Double pct = CoverageParser.parseLineCoveragePercent(dir.resolve("TestResults"), "OrderProcessor");

        // 0.85 → 85.0, not the blended 92.5 root rate (which counts the test project).
        assertThat(pct).isEqualTo(85.0);
    }

    @Test
    void fallsBackToRootWhenModuleNotFound(@TempDir Path dir) throws IOException {
        writeReport(dir, COBERTURA);

        Double pct = CoverageParser.parseLineCoveragePercent(dir.resolve("TestResults"), "NoSuchModule");

        assertThat(pct).isEqualTo(92.5);
    }

    @Test
    void roundsToOneDecimalPlace(@TempDir Path dir) throws IOException {
        writeReport(dir, """
                <?xml version="1.0" encoding="utf-8"?>
                <coverage line-rate="0.8337">
                  <packages>
                    <package name="Calc" line-rate="0.8337"/>
                  </packages>
                </coverage>""");

        Double pct = CoverageParser.parseLineCoveragePercent(dir.resolve("TestResults"), "Calc");

        assertThat(pct).isEqualTo(83.4);
    }

    @Test
    void returnsNullWhenNoReportPresent(@TempDir Path dir) {
        Double pct = CoverageParser.parseLineCoveragePercent(dir.resolve("TestResults"), "Anything");

        assertThat(pct).isNull();
    }

    @Test
    void returnsNullOnMalformedXml(@TempDir Path dir) throws IOException {
        writeReport(dir, "not xml at all <<<");

        Double pct = CoverageParser.parseLineCoveragePercent(dir.resolve("TestResults"), "Anything");

        assertThat(pct).isNull();
    }

    @Test
    void picksTheNewestReportWhenSeveralExist(@TempDir Path dir) throws IOException {
        Path results = dir.resolve("TestResults");
        Path older = results.resolve("older-guid");
        Path newer = results.resolve("newer-guid");
        Files.createDirectories(older);
        Files.createDirectories(newer);
        Files.writeString(older.resolve("coverage.cobertura.xml"), """
                <coverage line-rate="0.5"><packages><package name="Calc" line-rate="0.5"/></packages></coverage>""");
        Path newest = newer.resolve("coverage.cobertura.xml");
        Files.writeString(newest, """
                <coverage line-rate="0.9"><packages><package name="Calc" line-rate="0.9"/></packages></coverage>""");
        // Make the "newer" report unambiguously the most recently modified.
        older.resolve("coverage.cobertura.xml").toFile().setLastModified(1_000_000L);
        newest.toFile().setLastModified(2_000_000L);

        Double pct = CoverageParser.parseLineCoveragePercent(results, "Calc");

        assertThat(pct).isEqualTo(90.0);
    }
}
