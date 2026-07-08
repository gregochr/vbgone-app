package com.vbgone.build;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;

/**
 * Reads coverage from a Coverlet Cobertura report ({@code coverage.cobertura.xml}), produced by
 * {@code dotnet test --collect:"XPlat Code Coverage"}.
 *
 * <p>Coverlet writes the report to {@code <testProject>/TestResults/<guid>/coverage.cobertura.xml}
 * and aggregates every instrumented module — including the test project itself — into the root
 * {@code line-rate}/{@code branch-rate}. To report coverage of the <em>code under test</em> rather
 * than the diluted aggregate, we prefer the {@code <package>} whose name matches the module under
 * test (its assembly name equals the class/project name), falling back to the root rate when that
 * package is absent. Coverage is informational only, so every failure path returns nulls rather
 * than throwing.
 */
public final class CoverageParser {

    private CoverageParser() {}

    /**
     * Line and branch coverage of the code under test, each a percentage (0–100) or {@code null}
     * when unavailable. Line coverage measures reach ("did each line run?"); branch coverage
     * measures decision depth ("was each branch exercised both ways?") and is the stronger
     * confidence signal.
     */
    public record Coverage(Double linePercent, Double branchPercent) {
        public static final Coverage EMPTY = new Coverage(null, null);
    }

    /**
     * Finds the newest {@code coverage.cobertura.xml} under {@code searchDir} and returns the line
     * and branch coverage of the {@code moduleUnderTest} package (or the root aggregate if that
     * package is absent). Returns {@link Coverage#EMPTY} when no report exists or it cannot be read.
     */
    public static Coverage parse(Path searchDir, String moduleUnderTest) {
        Optional<Path> report = findReport(searchDir);
        if (report.isEmpty()) {
            return Coverage.EMPTY;
        }
        try {
            String xml = Files.readString(report.get());
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            return new Coverage(
                    rateFor(doc, moduleUnderTest, "line-rate"),
                    rateFor(doc, moduleUnderTest, "branch-rate"));
        } catch (Exception e) {
            return Coverage.EMPTY;
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

    /**
     * The given rate attribute for the module-under-test package (preferred) or the root, as a
     * percentage rounded to one decimal place, or null when neither carries it.
     */
    private static Double rateFor(Document doc, String moduleUnderTest, String attr) {
        Double pkg = packageRate(doc, moduleUnderTest, attr);
        Double rate = pkg != null ? pkg : rootRate(doc, attr);
        return rate == null ? null : Math.round(rate * 1000.0) / 10.0; // one decimal place
    }

    /** {@code attr} of the {@code <package name="moduleUnderTest">} element, or null if absent. */
    private static Double packageRate(Document doc, String moduleUnderTest, String attr) {
        if (moduleUnderTest == null || moduleUnderTest.isBlank()) {
            return null;
        }
        var packages = doc.getElementsByTagName("package");
        for (int i = 0; i < packages.getLength(); i++) {
            Element pkg = (Element) packages.item(i);
            if (moduleUnderTest.equals(pkg.getAttribute("name"))) {
                return parseRate(pkg.getAttribute(attr));
            }
        }
        return null;
    }

    /** {@code attr} of the root {@code <coverage>} element, or null if unreadable. */
    private static Double rootRate(Document doc, String attr) {
        var coverage = doc.getElementsByTagName("coverage");
        if (coverage.getLength() == 0) {
            return null;
        }
        return parseRate(((Element) coverage.item(0)).getAttribute(attr));
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
