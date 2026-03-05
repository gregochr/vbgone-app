package com.vbgone.model;

import java.util.List;

public record CostResult(
        String sessionId,
        List<TokenUsage> steps,
        double totalCost
) {}
