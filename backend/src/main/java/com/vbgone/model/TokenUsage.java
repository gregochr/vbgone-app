package com.vbgone.model;

public record TokenUsage(
        String step,
        String model,
        long inputTokens,
        long outputTokens,
        double cost,
        String provider
) {
    /** Backwards-compatible constructor — defaults provider to "anthropic". */
    public TokenUsage(String step, String model, long inputTokens, long outputTokens, double cost) {
        this(step, model, inputTokens, outputTokens, cost, "anthropic");
    }
}
