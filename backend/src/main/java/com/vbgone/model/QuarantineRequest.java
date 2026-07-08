package com.vbgone.model;

import java.util.List;

/**
 * Accept a red baseline by setting the unrepairable test(s) aside: they are marked
 * {@code [Ignore(...)]} (kept in the suite, flagged for a human) and the rest is re-run against the
 * original VB. Carries the current suite {@code code} and the {@code tests} to quarantine; no
 * provider fields because this makes no AI call.
 */
public record QuarantineRequest(
        String sessionId,
        String className,
        String code,
        List<String> tests
) {}
