package com.vbgone.model;

import com.vbgone.ai.AiRequestOptions;

import java.util.Map;

public record ClassRequest(
        String sessionId,
        String className,
        String provider,
        String targetLanguage,
        Map<String, String> modelOverrides
) {
    /** Backwards-compatible constructor without AI-provider fields. */
    public ClassRequest(String sessionId, String className) {
        this(sessionId, className, null, null, null);
    }

    /** Bundles the flat AI-provider fields into the value object the services consume. */
    public AiRequestOptions aiOptions() {
        return AiRequestOptions.of(provider, targetLanguage, modelOverrides);
    }
}
