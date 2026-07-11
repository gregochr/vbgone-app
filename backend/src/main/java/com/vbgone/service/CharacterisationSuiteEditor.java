package com.vbgone.service;

import com.vbgone.model.RepairAttempt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pure text-surgery on a C#/MSTest characterisation suite: isolating a test method by name,
 * quarantining it with {@code [Ignore]}, splicing a rewrite in, gating a rewrite for validity,
 * diffing two method blocks, spotting a flaky observed value, and re-attaching a dropped
 * {@code [TestClass]}.
 *
 * <p>Extracted from {@code AssureService}: these operate purely on suite source text and touch
 * none of the service's session / registry / runner / prompt state, so they live here as pure
 * static functions. {@code AssureService} orchestrates and delegates to them. Package-private so
 * the co-located unit tests ({@code AssureRepairTest}, {@code AssureServiceTest}) exercise them
 * directly.
 */
final class CharacterisationSuiteEditor {

    private CharacterisationSuiteEditor() {}

    private static final Pattern SUT_CALL = Pattern.compile("\\.\\s*([A-Za-z_]\\w*)\\s*\\(");

    /**
     * Index of the line declaring the named test method (matches {@code " name("} or
     * {@code " name ("}), or {@code -1} if it isn't found. The one signature-finder shared by
     * {@link #extractTestMethod} and {@link #markTestIgnored}, which then apply their own
     * (deliberately different) attribute walk-up.
     */
    private static int findSignatureLine(String[] lines, String testName) {
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(" " + testName + "(") || lines[i].contains(" " + testName + " (")) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Isolate a single {@code [TestMethod] ... }} block by name: walk back to the attribute line,
     * forward by brace matching to the method's closing brace. Returns "" if not found.
     */
    static String extractTestMethod(String code, String testName) {
        if (code == null || testName == null || testName.isBlank()) return "";
        String[] lines = code.split("\n", -1);
        int sig = findSignatureLine(lines, testName);
        if (sig < 0) return "";
        // Keep the single attribute line directly above the signature.
        int start = sig;
        while (start > 0 && !lines[start - 1].trim().startsWith("[")) start--;
        if (start > 0 && lines[start - 1].trim().startsWith("[")) start--;
        int depth = 0;
        boolean opened = false;
        int end = sig;
        for (int j = sig; j < lines.length; j++) {
            for (char c : lines[j].toCharArray()) {
                if (c == '{') { depth++; opened = true; }
                else if (c == '}') depth--;
            }
            if (opened && depth <= 0) { end = j; break; }
        }
        StringBuilder sb = new StringBuilder();
        for (int j = start; j <= end && j < lines.length; j++) {
            sb.append(lines[j]);
            if (j < end) sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Insert {@code [Ignore("<reason>")]} above a test method's attribute block so it is retained in
     * the suite (visible for review) but skipped by MSTest — keeping the run green. Idempotent, and
     * a no-op if the named test isn't found. {@code Ignore} lives in the same namespace as
     * {@code TestMethod}, so no extra {@code using} is needed.
     */
    static String markTestIgnored(String code, String testName, String reason) {
        if (code == null || testName == null || testName.isBlank()) return code;
        String[] lines = code.split("\n", -1);
        int sig = findSignatureLine(lines, testName);
        if (sig < 0) return code;
        // Walk up over the method's contiguous attribute lines ([TestMethod], [DataRow], ...).
        int attr = sig;
        while (attr > 0 && lines[attr - 1].trim().startsWith("[")) attr--;
        for (int i = attr; i <= sig; i++) {
            if (lines[i].contains("[Ignore(")) return code; // already quarantined
        }
        String line = lines[attr];
        String indent = line.substring(0, line.length() - line.stripLeading().length());
        String ignore = indent + "[Ignore(\"" + reason.replace("\"", "'") + "\")]";
        List<String> out = new ArrayList<>(Arrays.asList(lines));
        out.add(attr, ignore);
        return String.join("\n", out);
    }

    static String spliceMethod(String code, String oldBlock, String newBlock) {
        if (oldBlock == null || oldBlock.isBlank() || !code.contains(oldBlock)) return code;
        return code.replace(oldBlock, newBlock);
    }

    /**
     * The gate that stops the loop cheating. A green re-run is not enough — the rewrite must still
     * call the same method under test and make a real assertion, never a meaningless always-pass
     * test. Rejects: no assertion, a tautology (Assert.IsTrue(true) / AreEqual(x, x)), or dropping
     * the method under test.
     */
    static RepairAttempt.Gate validityGate(String oldBlock, String newBlock) {
        String body = newBlock == null ? "" : newBlock;
        String normalized = body.replaceAll("\\s+", "");
        if (!body.contains("Assert.")) {
            return new RepairAttempt.Gate(false,
                    "The rewrite has no real assertion — a meaningless always-pass test. Rejected.");
        }
        if (normalized.contains("Assert.IsTrue(true)") || normalized.contains("Assert.IsFalse(false)")
                || isTrivialAreEqual(normalized)) {
            return new RepairAttempt.Gate(false,
                    "The rewrite is an always-pass test (a tautology). Rejected.");
        }
        String method = primaryCall(oldBlock);
        if (method != null && !body.contains("." + method + "(") && !body.contains("." + method + " (")) {
            return new RepairAttempt.Gate(false,
                    "The rewrite no longer calls " + method + " — the method under test changed. Rejected.");
        }
        String note = method != null
                ? "Still calls " + method + " and still checks the return value or thrown exception. "
                    + "Not a meaningless always-pass test."
                : "Keeps a real assertion on the same test. Not a meaningless always-pass test.";
        return new RepairAttempt.Gate(true, note);
    }

    /** The method under test called on the system-under-test variable (first non-Assert call). */
    private static String primaryCall(String block) {
        if (block == null) return null;
        var m = SUT_CALL.matcher(block);
        while (m.find()) {
            String name = m.group(1);
            if (!name.startsWith("Assert") && !name.equals("ThrowsException") && !name.equals("Equals")) {
                return name;
            }
        }
        return null;
    }

    private static boolean isTrivialAreEqual(String normalized) {
        var m = Pattern.compile("Assert\\.AreEqual\\(([^,]+),([^)]+)\\)").matcher(normalized);
        while (m.find()) {
            if (m.group(1).equals(m.group(2))) return true;
        }
        return false;
    }

    /** A minimal line-level diff: lines only in old are removed, lines only in new are added. */
    static List<RepairAttempt.DiffLine> buildDiff(String oldBlock, String newBlock) {
        List<String> oldLines = trimmedLines(oldBlock);
        List<String> newLines = trimmedLines(newBlock);
        Set<String> oldSet = new HashSet<>(oldLines);
        Set<String> newSet = new HashSet<>(newLines);
        List<RepairAttempt.DiffLine> diff = new ArrayList<>();
        for (String line : oldLines) {
            if (!newSet.contains(line)) diff.add(new RepairAttempt.DiffLine("-", line));
        }
        for (String line : newLines) {
            if (!oldSet.contains(line)) diff.add(new RepairAttempt.DiffLine("+", line));
        }
        return diff;
    }

    private static List<String> trimmedLines(String block) {
        if (block == null || block.isBlank()) return List.of();
        return block.lines().map(String::stripTrailing).filter(l -> !l.isBlank()).toList();
    }

    /** The observed value differs between two identical runs → nondeterministic (not fixable). */
    static boolean isFlaky(String message1, String message2) {
        String a = observedActual(message1);
        String b = observedActual(message2);
        return !a.isBlank() && !b.isBlank() && !a.equals(b);
    }

    private static String observedActual(String message) {
        if (message == null) return "";
        var m = Pattern.compile("Actual:\\s*<?([^>\\n]*)>?").matcher(message);
        return m.find() ? m.group(1).trim() : "";
    }

    /**
     * The baseline prompt (correctly) instructs the model to omit {@code using} lines — the
     * csproj supplies them as global usings — so the model's output starts with {@code [TestClass]}.
     * {@code stripCodeFences} then anchors on {@code "public class "} and returns everything from
     * that point, silently dropping the leading attribute. Without {@code [TestClass]}, MSTest
     * discovers zero tests: the suite compiles and runs but the .trx reports 0/0, which the UI
     * reads as "baseline not faithful". Re-attach the attribute if it went missing (idempotent, so
     * it also guards the user-edited re-run path).
     */
    static String ensureTestClassAttribute(String code) {
        if (code == null || code.isBlank() || code.contains("[TestClass]")) return code;
        boolean hasTests = code.contains("[TestMethod]") || code.contains("[DataTestMethod]");
        if (!hasTests) return code;
        int idx = code.indexOf("public class ");
        if (idx < 0) idx = code.indexOf("class ");
        if (idx < 0) return code;
        int lineStart = code.lastIndexOf('\n', idx) + 1;
        String indent = code.substring(lineStart, idx);
        return code.substring(0, lineStart) + indent + "[TestClass]\n" + code.substring(lineStart);
    }
}
