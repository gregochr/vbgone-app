package com.vbgone.model;

import com.vbgone.ai.AiRequestOptions;

import java.util.Map;

public record AnalyseRequest(
        String filename,
        String content,
        String provider,
        String targetLanguage,
        Map<String, String> modelOverrides,
        /** "migrate" (default) or "assure". Selects the analysis persona. */
        String mode
) {
    /** Bundles the flat AI-provider fields into the value object the services consume. */
    public AiRequestOptions aiOptions() {
        return AiRequestOptions.of(provider, targetLanguage, modelOverrides);
    }

    /** Backwards-compatible constructor without AI-provider fields. */
    public AnalyseRequest(String filename, String content) {
        this(filename, content, null, null, null, null);
    }

    /** Backwards-compatible constructor without the mode discriminator. */
    public AnalyseRequest(String filename, String content,
                          String provider, String targetLanguage,
                          Map<String, String> modelOverrides) {
        this(filename, content, provider, targetLanguage, modelOverrides, null);
    }
}
