import type { AnalysisResult, ReadinessReport } from '../../../api/migrateApi'
import type { Bucket } from '../../../config/engine'

/**
 * A class is assurable when it's net-ready, or windows-gated *and* the Windows runner is selected.
 * Tangled (refactor-first) classes are never actionable here — they need refactoring first, which the
 * Windows runner doesn't help with. This is the single source of truth the readiness UI and the
 * drill-in queue both derive from, so the RUNNER toggle flips both consistently.
 */
export const isActionable = (bucket: Bucket, windowsOn: boolean): boolean =>
  bucket === 'net-ready' || (windowsOn && bucket === 'windows-gated')

/** The plain-language verdict headline shown above the readiness report. */
export function readinessSubtitle(r: ReadinessReport, windowsOn = false): string {
  const t = r.totals
  if (windowsOn) {
    const ready = t.netReady + t.windowsGated
    return `${ready} of ${t.classes} classes are ready to assure with the Windows runner on. ${t.refactorFirst} still need refactoring first.`
  }
  return `${t.netReady} of ${t.classes} classes are ready to assure today. ${t.windowsGated} unlock with a Windows runner; ${t.refactorFirst} need refactoring first.`
}

/**
 * Project a {@link ReadinessReport} onto the {@link AnalysisResult} shape the wizard queue drives
 * off — the assurable classes become the `suggestedMigrationOrder` (the Assure drill-in queue). With
 * {@code windowsOn}, the windows-gated classes join the queue too. Shared by the Readiness step
 * (which scans) and the GitHub-repo ingest path (which pre-loads the report).
 */
export function deriveAnalysis(
  r: ReadinessReport,
  isPortfolio: boolean,
  windowsOn = false,
): AnalysisResult {
  const ready = r.classes.filter((c) => isActionable(c.bucket, windowsOn))
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
    summary: isPortfolio ? readinessSubtitle(r, windowsOn) : (r.classes[0]?.reason ?? ''),
  }
}
