package com.vbgone.model;

import java.util.List;

/**
 * Outcome of a {@code dotnet test} run.
 *
 * <p>{@code coveragePercent} is the line-coverage percentage of the code under test
 * (0–100), or {@code null} when coverage was not collected (e.g. a compile-time
 * {@code ERROR} build, or a runner that does not gather coverage). It is purely
 * informational — coverage never gates the RED/GREEN outcome.
 */
public record BuildResult(
        String sessionId,
        BuildStatus buildStatus,
        int total,
        int passed,
        int failed,
        List<String> errors,
        List<String> failedTests,
        Double coveragePercent
) {

    /** Back-compat constructor for call sites that do not measure coverage. */
    public BuildResult(String sessionId, BuildStatus buildStatus, int total, int passed,
                       int failed, List<String> errors, List<String> failedTests) {
        this(sessionId, buildStatus, total, passed, failed, errors, failedTests, null);
    }

    /** Returns a copy with the given line-coverage percentage attached. */
    public BuildResult withCoverage(Double coveragePercent) {
        return new BuildResult(sessionId, buildStatus, total, passed, failed,
                errors, failedTests, coveragePercent);
    }
}
