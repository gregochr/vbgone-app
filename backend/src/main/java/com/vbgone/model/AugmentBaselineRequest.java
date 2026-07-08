package com.vbgone.model;

import java.util.Map;

/**
 * "Add more tests" — extend an existing green characterisation suite to pin more of the class.
 * Carries the current suite ({@code code}) and the current line coverage ({@code coveragePercent},
 * nullable) for prompt context. Makes an AI call, so it carries the provider/model fields.
 */
public record AugmentBaselineRequest(
        String sessionId,
        String className,
        String code,
        Double coveragePercent,
        String provider,
        String targetLanguage,
        Map<String, String> modelOverrides
) {}
