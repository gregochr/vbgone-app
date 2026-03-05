package com.vbgone.model;

public record ImplementResult(
        String sessionId,
        String className,
        String code,
        ImplementMode mode
) {}
