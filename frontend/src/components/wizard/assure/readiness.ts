import type { AnalysisResult, ReadinessReport } from '../../../api/migrateApi'

/** The plain-language verdict headline shown above the readiness report. */
export function readinessSubtitle(r: ReadinessReport): string {
  const t = r.totals
  return `${t.netReady} of ${t.classes} classes are ready to assure today. ${t.windowsGated} unlock with a Windows runner; ${t.refactorFirst} need refactoring first.`
}

/**
 * Project a {@link ReadinessReport} onto the {@link AnalysisResult} shape the wizard queue drives
 * off — the ready classes become the `suggestedMigrationOrder` (the Assure drill-in queue). Shared
 * by the Readiness step (which scans) and the GitHub-repo ingest path (which pre-loads the report).
 */
export function deriveAnalysis(r: ReadinessReport, isPortfolio: boolean): AnalysisResult {
  const ready = r.classes.filter((c) => c.bucket === 'net-ready')
  const target = isPortfolio ? ready : ready.length ? ready : r.classes.slice(0, 1)
  return {
    sessionId: r.sessionId,
    classes: r.classes.map((c) => ({
      name: c.name,
      methods: c.methods.map((m) => m.name),
      dependencies: [],
      complexity: 'LOW' as const,
    })),
    suggestedMigrationOrder: target.map((c) => c.name),
    summary: isPortfolio ? readinessSubtitle(r) : (r.classes[0]?.reason ?? ''),
  }
}
