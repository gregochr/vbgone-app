package com.vbgone.model;

public record ImplementRequest(
        String sessionId,
        String className,
        ImplementMode mode
) {}
