package com.vbgone.build;

import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;

/**
 * Reads the line-coverage percentage from a Coverlet Cobertura report
 * ({@code coverage.cobertura.xml}), produced by {@code dotnet test --collect:"XPlat Code Coverage"}.
 *
 * <p>Coverlet writes the report to {@code <testProject>/TestResults/<guid>/coverage.cobertura.xml}
 * and aggregates every instrumented module — including the test project itself — into the root
 * {@code line-rate}. To report coverage of the <em>code under test</em> rather than the diluted
 * aggregate, we prefer the {@code <package>} whose name matches the module under test (its assembly
 * name equals the class/project name), falling back to the root {@code line-rate} when that package
 * is absent. Coverage is informational only, so every failure path returns {@code null} rather than
 * throwing.
 */
public final class CoverageParser {

    private CoverageParser() {}

    /**
     * Finds the newest {@code coverage.cobertura.xml} under {@code searchDir} and returns the line
     * coverage of the {@code moduleUnderTest} package as a percentage (0–100), or the root coverage
     * if that package is not present. Returns {@code null} when no report exists or it cannot be read.
     */
    public static Double parseLineCoveragePercent(Path searchDir, String moduleUnderTest) {
        Optional<Path> report = findReport(searchDir);
        if (report.isEmpty()) {
            return null;
        }
        try {
            String xml = Files.readString(report.get());
            var doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            Double moduleRate = lineRateForPackage(doc, moduleUnderTest);
            Double rate = moduleRate != null ? moduleRate : rootLineRate(doc);
            return rate == null ? null : Math.round(rate * 1000.0) / 10.0; // one decimal place
        } catch (Exception e) {
            return null;
        }
    }

    private static Optional<Path> findReport(Path searchDir) {
        if (searchDir == null || !Files.isDirectory(searchDir)) {
            return Optional.empty();
        }
        try (var walk = Files.walk(searchDir)) {
            return walk
                    .filter(p -> p.getFileName().toString().equals("coverage.cobertura.xml"))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Line-rate of the {@code <package name="moduleUnderTest">} element, or null if absent. */
    private static Double lineRateForPackage(org.w3c.dom.Document doc, String moduleUnderTest) {
        if (moduleUnderTest == null || moduleUnderTest.isBlank()) {
            return null;
        }
        var packages = doc.getElementsByTagName("package");
        for (int i = 0; i < packages.getLength(); i++) {
            Element pkg = (Element) packages.item(i);
            if (moduleUnderTest.equals(pkg.getAttribute("name"))) {
                return parseRate(pkg.getAttribute("line-rate"));
            }
        }
        return null;
    }

    /** Line-rate of the root {@code <coverage>} element, or null if unreadable. */
    private static Double rootLineRate(org.w3c.dom.Document doc) {
        var coverage = doc.getElementsByTagName("coverage");
        if (coverage.getLength() == 0) {
            return null;
        }
        return parseRate(((Element) coverage.item(0)).getAttribute("line-rate"));
    }

    private static Double parseRate(String rate) {
        if (rate == null || rate.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(rate);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
