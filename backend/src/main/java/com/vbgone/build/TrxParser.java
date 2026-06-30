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

/**
 * Parses a VSTest {@code .trx} result file into a {@link BuildResult}. Shared by the C#
 * migrate build ({@link DotNetRuntime}) and the Protect characterisation run
 * ({@code VbCharacterisationRunner}) so the two stay byte-identical.
 */
public final class TrxParser {

    private TrxParser() {}

    /** The parsed counts plus the per-test failure messages (used for retry prompts). */
    public record Parsed(BuildResult result, Map<String, String> failureMessages) {}

    public static Parsed parse(String sessionId, String trxContent) {
        try {
            var doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new ByteArrayInputStream(trxContent.getBytes(StandardCharsets.UTF_8)));

            var counters = (org.w3c.dom.Element) doc.getElementsByTagName("Counters").item(0);
            int total = Integer.parseInt(counters.getAttribute("total"));
            int passed = Integer.parseInt(counters.getAttribute("passed"));
            int failed = Integer.parseInt(counters.getAttribute("failed"));

            List<String> failedTests = new ArrayList<>();
            Map<String, String> failureMessages = new LinkedHashMap<>();
            var results = doc.getElementsByTagName("UnitTestResult");
            for (int i = 0; i < results.getLength(); i++) {
                var el = (org.w3c.dom.Element) results.item(i);
                if ("Failed".equals(el.getAttribute("outcome"))) {
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
}
