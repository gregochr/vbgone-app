package com.vbgone.model;

public record VbSourceFile(
        String relativePath,
        String filename,
        String content
) {}
