package com.vbgone.model;

/**
 * Re-run a corrected characterisation net against the original VB. Carries the edited suite
 * {@code code}; no provider fields are needed because re-running makes no AI call.
 */
public record BaselineRerunRequest(
        String sessionId,
        String className,
        String code
) {}
