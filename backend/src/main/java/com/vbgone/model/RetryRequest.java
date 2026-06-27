package com.vbgone.model;

import java.util.List;
import java.util.Map;

public record RetryRequest(
        String sessionId,
        String className,
        List<String> failingTests,
        int attempt,
        String provider,
        String targetLanguage,
        Map<String, String> modelOverrides
) {
    /** Backwards-compatible constructor without AI-provider fields. */
    public RetryRequest(String sessionId, String className, List<String> failingTests, int attempt) {
        this(sessionId, className, failingTests, attempt, null, null, null);
    }
}
