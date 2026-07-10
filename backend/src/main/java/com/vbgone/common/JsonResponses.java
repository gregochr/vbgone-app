package com.vbgone.common;

/**
 * Helpers for handling raw LLM responses that carry JSON.
 */
public final class JsonResponses {

    private JsonResponses() {
    }

    /**
     * Strips a surrounding Markdown code fence from a model response, if present.
     *
     * <p>Models often wrap JSON in <code>```json ... ```</code> (or a bare <code>``` ... ```</code>).
     * Returns the trimmed inner content, or the trimmed input unchanged when there is no fence.
     */
    public static String stripFences(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }
        return trimmed;
    }
}
