package com.vbgone.prompt;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.ToIntBiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Language-neutral post-processing helpers shared by the prompt strategies.
 */
final class PromptPostProcessing {

    private PromptPostProcessing() {
    }

    /**
     * Strips markdown code fences (or a natural-language preamble) from a model response.
     * The shared shape is identical across languages; only two things vary and are passed in:
     * {@code embeddedLangPattern} is the alternation used inside the embedded-block regex
     * (e.g. {@code "csharp|cs"} or {@code "java"}); {@code anchorMarkers} are the "first real
     * code" markers used when the model wrapped code in prose without fences (the earliest
     * marker present wins).
     */
    static String stripCodeFences(String text, String embeddedLangPattern, String... anchorMarkers) {
        String trimmed = text.trim();
        // If the response starts with a code fence, strip it.
        if (trimmed.startsWith("```")) {
            return trimmed.replaceAll("^```\\w*\\s*", "").replaceAll("\\s*```$", "");
        }
        // If the model returned natural language with an embedded code block, extract it.
        Matcher m = Pattern.compile(
                "```(?:" + embeddedLangPattern + ")?\\s*\\n(.*?)\\n\\s*```",
                Pattern.DOTALL).matcher(trimmed);
        if (m.find()) {
            return m.group(1).trim();
        }
        // Otherwise anchor extraction on the first real code marker.
        int startIdx = firstIndexOf(trimmed, anchorMarkers);
        if (startIdx > 0) {
            return trimmed.substring(startIdx).trim();
        }
        return trimmed;
    }

    /** Index of the earliest-appearing marker in {@code text}, or -1 if none are present. */
    static int firstIndexOf(String text, String... markers) {
        int best = -1;
        for (String marker : markers) {
            int idx = text.indexOf(marker);
            if (idx >= 0 && (best < 0 || idx < best)) {
                best = idx;
            }
        }
        return best;
    }

    /**
     * Extracts the full source of each named failing test — from its attribute/annotation line
     * through the matching closing brace — for inclusion in a retry prompt. The brace-matching
     * walk is language-neutral; the two language-specific parts are passed in:
     * {@code isMethodSignature} decides whether a line is the signature for a given test name,
     * and {@code attributeStart} walks back from that signature line to the first
     * attribute/annotation line to include.
     */
    static String extractFailingTests(String testCode, List<String> failingTestNames,
                                      BiPredicate<String, String> isMethodSignature,
                                      ToIntBiFunction<String[], Integer> attributeStart) {
        if (testCode == null || testCode.isBlank() || failingTestNames.isEmpty()) return "";

        String[] lines = testCode.split("\n");
        StringBuilder sb = new StringBuilder();

        for (String testName : failingTestNames) {
            for (int i = 0; i < lines.length; i++) {
                if (isMethodSignature.test(lines[i], testName)) {
                    int start = attributeStart.applyAsInt(lines, i);

                    // Walk forward to the matching closing brace.
                    int depth = 0;
                    int end = i;
                    for (int j = i; j < lines.length; j++) {
                        for (char c : lines[j].toCharArray()) {
                            if (c == '{') depth++;
                            else if (c == '}') depth--;
                        }
                        if (depth <= 0 && j > i) {
                            end = j;
                            break;
                        }
                    }

                    for (int j = start; j <= end && j < lines.length; j++) {
                        sb.append(lines[j]).append("\n");
                    }
                    sb.append("\n");
                    break;
                }
            }
        }
        return sb.toString().trim();
    }

    /**
     * If the model's output was truncated mid-token, the code will be missing closing
     * braces. This detects the imbalance and appends enough closing braces to make the
     * file compilable. Any incomplete method at the end is removed. Brace syntax is
     * identical between C# and Java, so this is shared.
     */
    static String repairTruncatedByBraces(String code) {
        int opens = 0;
        for (char c : code.toCharArray()) {
            if (c == '{') opens++;
            else if (c == '}') opens--;
        }
        if (opens <= 0) return code;

        // Truncation happened — remove the last incomplete method/test by trimming to
        // the last complete block ending with }.
        int lastCloseBrace = code.lastIndexOf('}');
        if (lastCloseBrace > 0) {
            code = code.substring(0, lastCloseBrace + 1);
        }

        // Recount and close remaining open braces
        opens = 0;
        for (char c : code.toCharArray()) {
            if (c == '{') opens++;
            else if (c == '}') opens--;
        }
        StringBuilder sb = new StringBuilder(code);
        for (int i = 0; i < opens; i++) {
            sb.append("\n}");
        }
        return sb.toString();
    }
}
