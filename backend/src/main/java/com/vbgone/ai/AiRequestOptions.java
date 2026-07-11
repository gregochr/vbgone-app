package com.vbgone.ai;

import java.util.Collections;
import java.util.Map;

/**
 * Normalised AI-request options carried from the controller into the services.
 * Applies the contract defaults: null provider -> "anthropic",
 * null targetLanguage -> "csharp", null overrides -> empty map.
 */
public record AiRequestOptions(
        String provider,
        String targetLanguage,
        Map<String, String> modelOverrides
) {
    public static final String DEFAULT_PROVIDER = "anthropic";
    public static final String DEFAULT_TARGET_LANGUAGE = "csharp";

    public AiRequestOptions {
        provider = (provider == null || provider.isBlank()) ? DEFAULT_PROVIDER : provider;
        targetLanguage = (targetLanguage == null || targetLanguage.isBlank())
                ? DEFAULT_TARGET_LANGUAGE : targetLanguage;
        modelOverrides = modelOverrides == null ? Collections.emptyMap() : Map.copyOf(modelOverrides);
    }

    public static AiRequestOptions of(String provider, String targetLanguage, Map<String, String> overrides) {
        return new AiRequestOptions(provider, targetLanguage, overrides);
    }

    /** The contract defaults (anthropic / csharp / no overrides) — for callers that don't tune the AI. */
    public static AiRequestOptions defaults() {
        return new AiRequestOptions(null, null, null);
    }

    public boolean isJavaTarget() {
        return "java".equalsIgnoreCase(targetLanguage);
    }
}
