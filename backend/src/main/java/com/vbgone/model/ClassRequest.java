package com.vbgone.model;

import com.vbgone.ai.AiRequestOptions;

import java.util.Map;

public record ClassRequest(
        String sessionId,
        String className,
        String provider,
        String targetLanguage,
        Map<String, String> modelOverrides,
        String runner
) {
    /** Backwards-compatible constructor without AI-provider fields. */
    public ClassRequest(String sessionId, String className) {
        this(sessionId, className, null, null, null, null);
    }

    /** Backwards-compatible constructor without the runner field (defaults to Linux). */
    public ClassRequest(String sessionId, String className, String provider,
                        String targetLanguage, Map<String, String> modelOverrides) {
        this(sessionId, className, provider, targetLanguage, modelOverrides, null);
    }

    /** Bundles the flat AI-provider fields into the value object the services consume. */
    public AiRequestOptions aiOptions() {
        return AiRequestOptions.of(provider, targetLanguage, modelOverrides);
    }

    /** The chosen Assure runner, defaulting to {@code "linux"} when the client didn't send one. */
    public String runnerMode() {
        return (runner == null || runner.isBlank()) ? "linux" : runner;
    }
}
