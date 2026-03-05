package com.vbgone.model;

public record TestsResult(
        String sessionId,
        String className,
        String testClassName,
        String code,
        int testCount
) {}
