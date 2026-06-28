package com.vbgone.prompt;

/**
 * Language-neutral post-processing helpers shared by the prompt strategies.
 */
final class PromptPostProcessing {

    private PromptPostProcessing() {
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
