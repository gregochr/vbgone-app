package com.vbgone.model;

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
}
