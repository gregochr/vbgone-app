package com.vbgone.model;

import com.vbgone.ai.AiRequestOptions;

import java.util.Map;

public record ImplementRequest(
        String sessionId,
        String className,
        ImplementMode mode,
        String provider,
        String targetLanguage,
        Map<String, String> modelOverrides
) {
    /** Backwards-compatible constructor without AI-provider fields. */
    public ImplementRequest(String sessionId, String className, ImplementMode mode) {
        this(sessionId, className, mode, null, null, null);
    }

    /** Bundles the flat AI-provider fields into the value object the services consume. */
    public AiRequestOptions aiOptions() {
        return AiRequestOptions.of(provider, targetLanguage, modelOverrides);
    }
}
