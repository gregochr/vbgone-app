package com.vbgone.model;

import java.util.List;

/**
 * The Portfolio Readiness Assessment result — Protect's front-gate verdict. A static
 * (no-AI) classification of an uploaded VB.NET source into the three readiness buckets,
 * at class and method granularity.
 *
 * @param sessionId   session the source is stored under (so the per-class Baseline flow can run)
 * @param totals      class- and method-level counts per bucket
 * @param confidence  "static" (heuristic) or "llm-refined" (future)
 * @param classes     per-class breakdown
 */
public record ReadinessReport(
        String sessionId,
        ReadinessTotals totals,
        String confidence,
        List<ClassReadiness> classes
) {
    /**
     * Aggregate counts. Class counts drive the table; method counts drive the headline
     * stacked bar (a class is often a mix, so methods are the honest unit).
     */
    public record ReadinessTotals(
            int classes,
            int methods,
            int netReady,
            int windowsGated,
            int refactorFirst,
            int methodNetReady,
            int methodWindowsGated,
            int methodRefactorFirst
    ) {}
}
