package com.vbgone.model;

public record TokenUsage(
        String step,
        String model,
        long inputTokens,
        long outputTokens,
        double cost
) {}
