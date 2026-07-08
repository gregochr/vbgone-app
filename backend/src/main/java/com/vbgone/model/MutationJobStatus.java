package com.vbgone.model;

/**
 * Immutable client-facing snapshot of a mutation-testing job (returned by the poll endpoint).
 * {@code result} is null until {@code state} is DONE; {@code error} is set only when FAILED.
 */
public record MutationJobStatus(
        String jobId,
        String state,
        int done,
        int total,
        MutationResult result,
        String error
) {}
