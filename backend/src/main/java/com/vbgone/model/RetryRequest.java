package com.vbgone.model;

import java.util.List;

public record RetryRequest(
        String sessionId,
        String className,
        List<String> failingTests
) {}
