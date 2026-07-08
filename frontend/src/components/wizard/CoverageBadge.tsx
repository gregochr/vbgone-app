interface CoverageBadgeProps {
  /** Line-coverage percentage (0–100), or null/undefined when not collected. */
  coveragePercent?: number | null
  /**
   * Branch-coverage percentage (0–100). When provided it is shown alongside line coverage as the
   * stronger confidence signal ("each decision exercised both ways"). Omit to show line only.
   */
  branchPercent?: number | null
  /** Warn threshold — below this the badge turns amber. Defaults to 80%. */
  threshold?: number
  /** What the coverage is measured against, e.g. "the implementation" or "your original VB.NET". */
  ofLabel?: string
}

/**
 * Informational coverage badge. Renders nothing when line coverage was not collected
 * (null/undefined). Below the threshold it warns in amber but never blocks — coverage is a signal,
 * not a gate. Every figure is attributed to Coverlet, the independent .NET coverage tool that
 * produced it, so the number reads as verified rather than self-reported.
 */
export function CoverageBadge({
  coveragePercent,
  branchPercent,
  threshold = 80,
  ofLabel = 'the code under test',
}: CoverageBadgeProps) {
  if (coveragePercent === null || coveragePercent === undefined) {
    return null
  }

  const meets = coveragePercent >= threshold
  const pct = `${coveragePercent.toFixed(1)}%`
  const hasBranch = branchPercent !== null && branchPercent !== undefined

  return (
    <div
      className={`coverage-badge ${meets ? 'coverage-ok' : 'coverage-warn'}`}
      data-testid="coverage-badge"
      role="status"
    >
      <span className="coverage-badge-icon" aria-hidden="true">
        {meets ? '✔' : '⚠'}
      </span>
      <span className="coverage-badge-body">
        <span className="coverage-badge-text">
          <strong>{pct}</strong> line coverage of {ofLabel}
          {meets ? (
            <> — meets the {threshold}% target.</>
          ) : (
            <>
              {' '}
              — <strong>below the {threshold}% target</strong>. Consider adding tests; this is a
              warning, not a blocker.
            </>
          )}
        </span>
        <span className="coverage-badge-source">
          {hasBranch && (
            <>
              <strong>{branchPercent!.toFixed(1)}%</strong> branch coverage — each decision
              exercised both ways.{' '}
            </>
          )}
          <span className="coverage-badge-verified">Verified by Coverlet</span>, the independent
          .NET coverage tool.
        </span>
      </span>
    </div>
  )
}
