package com.vbgone.model;

import java.util.Map;

public record AnalyseRequest(
        String filename,
        String content,
        String provider,
        String targetLanguage,
        Map<String, String> modelOverrides
) {
    /** Backwards-compatible constructor without AI-provider fields. */
    public AnalyseRequest(String filename, String content) {
        this(filename, content, null, null, null);
    }
}
