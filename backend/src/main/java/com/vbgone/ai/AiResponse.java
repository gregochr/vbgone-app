package com.vbgone.ai;

/**
 * Provider-agnostic response from a single chat generation call.
 */
public record AiResponse(
        String text,
        long inputTokens,
        long outputTokens,
        String modelId,
        String provider
) {}
