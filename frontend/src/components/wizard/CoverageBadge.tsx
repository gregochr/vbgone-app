interface CoverageBadgeProps {
  /** Line-coverage percentage (0–100), or null/undefined when not collected. */
  coveragePercent?: number | null
  /** Warn threshold — below this the badge turns amber. Defaults to 80%. */
  threshold?: number
  /** What the coverage is measured against, e.g. "the implementation" or "your original VB.NET". */
  ofLabel?: string
}

/**
 * Informational line-coverage badge. Renders nothing when coverage was not collected
 * (null/undefined). Below the threshold it warns in amber but never blocks — coverage is
 * a signal, not a gate.
 */
export function CoverageBadge({
  coveragePercent,
  threshold = 80,
  ofLabel = 'the code under test',
}: CoverageBadgeProps) {
  if (coveragePercent === null || coveragePercent === undefined) {
    return null
  }

  const meets = coveragePercent >= threshold
  const pct = `${coveragePercent.toFixed(1)}%`

  return (
    <div
      className={`coverage-badge ${meets ? 'coverage-ok' : 'coverage-warn'}`}
      data-testid="coverage-badge"
      role="status"
    >
      <span className="coverage-badge-icon" aria-hidden="true">
        {meets ? '✔' : '⚠'}
      </span>
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
    </div>
  )
}
