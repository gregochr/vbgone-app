package com.vbgone.build;

import com.vbgone.model.BuildResult;
import com.vbgone.model.BuildStatus;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses a VSTest {@code .trx} result file into a {@link BuildResult}. Shared by the C#
 * migrate build ({@link DotNetRuntime}) and the Assure characterisation run
 * ({@code VbCharacterisationRunner}) so the two stay byte-identical.
 */
public final class TrxParser {

    private TrxParser() {}

    /**
     * VSTest outcomes that mean a test did not pass. A suite is only GREEN when none of
     * these occurred: an assertion failure ({@code Failed}), a fixture/class-init throw
     * ({@code Error}), a hang ({@code Timeout}) or an aborted host ({@code Aborted}) all
     * count as failures. ({@code NotExecuted}/{@code Inconclusive} are skips, not failures.)
     */
    private static final Set<String> FAILING_OUTCOMES = Set.of("Failed", "Error", "Timeout", "Aborted");

    /** The parsed counts plus the per-test failure messages (used for retry prompts). */
    public record Parsed(BuildResult result, Map<String, String> failureMessages) {}

    public static Parsed parse(String sessionId, String trxContent) {
        try {
            var doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new ByteArrayInputStream(trxContent.getBytes(StandardCharsets.UTF_8)));

            var counters = (org.w3c.dom.Element) doc.getElementsByTagName("Counters").item(0);
            if (counters == null) {
                throw new IllegalArgumentException("no <Counters> element (truncated or malformed .trx)");
            }
            int total = intAttr(counters, "total");
            int passed = intAttr(counters, "passed");
            // A run can be RED without a single assertion failure: a fixture/class-init throw
            // is counted as "error", a hang as "timeout" and an aborted host as "aborted".
            // Counting only "failed" would report a false GREEN with passed < total.
            int failed = intAttr(counters, "failed")
                    + intAttr(counters, "error")
                    + intAttr(counters, "timeout")
                    + intAttr(counters, "aborted");

            List<String> failedTests = new ArrayList<>();
            Map<String, String> failureMessages = new LinkedHashMap<>();
            var results = doc.getElementsByTagName("UnitTestResult");
            for (int i = 0; i < results.getLength(); i++) {
                var el = (org.w3c.dom.Element) results.item(i);
                if (FAILING_OUTCOMES.contains(el.getAttribute("outcome"))) {
                    String testName = el.getAttribute("testName");
                    failedTests.add(testName);
                    var outputNodes = el.getElementsByTagName("Message");
                    if (outputNodes.getLength() > 0) {
                        failureMessages.put(testName, outputNodes.item(0).getTextContent().trim());
                    }
                }
            }

            BuildStatus status = (failed == 0) ? BuildStatus.GREEN : BuildStatus.RED;
            BuildResult result = new BuildResult(sessionId, status, total, passed, failed, List.of(), failedTests);
            return new Parsed(result, failureMessages);

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse .trx results: " + e.getMessage(), e);
        }
    }

    /** Reads an integer {@code <Counters>} attribute, treating absent/blank/non-numeric as 0. */
    private static int intAttr(org.w3c.dom.Element counters, String name) {
        String value = counters.getAttribute(name);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
