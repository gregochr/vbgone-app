package com.vbgone.model;

/**
 * Client-facing snapshot of an async Assure baseline-tests job — the Windows runner path, where a
 * characterisation dispatched to a GitHub {@code windows-latest} runner takes minutes and so must
 * not block the HTTP request. {@code result} is set only once {@code state} is {@code DONE};
 * {@code error} only when {@code FAILED}.
 */
public record BaselineJobStatus(
        String jobId,
        String state, // PENDING | RUNNING | DONE | FAILED
        BaselineTestsResult result,
        String error
) {}
