package com.vbgone.model;

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
}
