import { useEffect, useState } from 'react'
import type { WizardState } from '../WizardShell'
import { assess } from '../../../api/migrateApi'
import type { ClassReadiness, ReadinessReport } from '../../../api/migrateApi'
import { BUCKETS } from '../../../config/engine'
import type { Bucket } from '../../../config/engine'

interface Props {
  state: WizardState
  update: (partial: Partial<WizardState>) => void
  onReady: () => void
  /** Drill into the per-class Baseline flow for a ready class (portfolio queue). */
  onProtectClass?: (className: string) => void
}

const KICKER = 'STEP 02 · READINESS'

/**
 * Protect step 2 — the front gate. A static (no-AI) pass that classifies the source into
 * three readiness buckets. A single `.vb` renders an instant verdict card; a `.zip` portfolio
 * renders the readiness report + a queue of ready classes to protect one at a time.
 */
export function StepReadiness({ state, update, onReady, onProtectClass }: Props) {
  const isPortfolio = state.filename.toLowerCase().endsWith('.zip')
  const report = state.readiness
  // For a single .vb the report's file equals the filename; reuse only a matching report.
  const singleMatches = report?.classes[0]?.file === state.filename
  const usable = report && (isPortfolio ? report.totals.classes > 1 : singleMatches)

  const [loading, setLoading] = useState(!isPortfolio && !usable)
  const [scanning, setScanning] = useState(false)
  const [scanCount, setScanCount] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState<Bucket | 'all'>('all')
  const [expanded, setExpanded] = useState<Record<string, boolean>>({})

  const netted = state.netted ?? []

  const subtitleFor = (r: ReadinessReport) => {
    const t = r.totals
    return `${t.netReady} of ${t.classes} classes are ready to protect today. ${t.windowsGated} unlock with a Windows runner; ${t.refactorFirst} need refactoring first.`
  }

  const deriveAnalysis = (r: ReadinessReport): WizardState['analysis'] => {
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
      summary: isPortfolio ? subtitleFor(r) : (r.classes[0]?.reason ?? ''),
    }
  }

  const store = (r: ReadinessReport) => update({ readiness: r, analysis: deriveAnalysis(r) })

  // Single-file: auto-scan on mount. Portfolio: wait for an explicit "Assess readiness" click.
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    if (isPortfolio || usable) {
      if (usable && !isPortfolio && report!.classes.some((c) => c.bucket === 'net-ready')) onReady()
      return
    }
    setLoading(true)
    assess(state.filename, state.content)
      .then((r) => {
        store(r)
        setLoading(false)
        if (r.classes.some((c) => c.bucket === 'net-ready')) onReady()
      })
      .catch((err) => {
        setLoading(false)
        setError(err instanceof Error ? err.message : 'Assessment failed')
      })
  }, []) // eslint-disable-line react-hooks/exhaustive-deps
  /* eslint-enable react-hooks/set-state-in-effect */

  const runScan = () => {
    setScanning(true)
    setScanCount(0)
    assess(state.filename, state.content)
      .then((r) => {
        // Animate the scanned count up to the class total, then reveal the report.
        const total = r.totals.classes
        const start = Date.now()
        const tick = setInterval(() => {
          const p = Math.min(1, (Date.now() - start) / 900)
          setScanCount(Math.round(p * total))
          if (p >= 1) {
            clearInterval(tick)
            store(r)
            setScanning(false)
            if (r.classes.some((c) => c.bucket === 'net-ready')) onReady()
          }
        }, 40)
      })
      .catch((err) => {
        setScanning(false)
        setError(err instanceof Error ? err.message : 'Assessment failed')
      })
  }

  const header = (
    <>
      <div className="step-kicker">{KICKER}</div>
      <h2 className="step-title">
        {isPortfolio ? 'Assess the estate' : 'Can we protect this yet?'}
      </h2>
    </>
  )

  if (error) {
    return (
      <div>
        {header}
        <div className="build-status build-red">{error}</div>
      </div>
    )
  }

  // ── Portfolio: scan-idle → busy → report ──
  if (isPortfolio) {
    if (usable)
      return (
        <PortfolioReport
          {...{ report: report!, filter, setFilter, expanded, setExpanded, netted, onProtectClass }}
        />
      )
    if (scanning) {
      const pct = report ? '100%' : `${Math.min(100, Math.round((scanCount / 200) * 100))}%`
      const total = report?.totals.classes ?? '…'
      return (
        <div>
          <div className="step-kicker">{KICKER}</div>
          <h2 className="step-title">Scanning the estate…</h2>
          <p className="step-subtitle">
            Parsing VB.NET — reading imports, base classes, control field access and dialog calls.
          </p>
          <div className="scan-row">
            <span className="spinner" />
            <div className="scan-row-body">
              <div className="scan-count">
                Scanned <span className="scan-count-n">{scanCount}</span> / <span>{total}</span>{' '}
                classes…
              </div>
              <div className="scan-bar">
                <div className="scan-bar-fill" style={{ width: pct }} />
              </div>
            </div>
          </div>
        </div>
      )
    }
    return (
      <div>
        {header}
        <p className="step-subtitle">
          A fast static pass classifies every business-logic method as{' '}
          <strong style={{ color: BUCKETS['net-ready'].color }}>ready to protect</strong>,{' '}
          <strong style={{ color: BUCKETS['windows-gated'].color }}>needs a Windows runner</strong>,
          or <strong style={{ color: BUCKETS['refactor-first'].color }}>tangled in the UI</strong> —
          so you know up front how much of this Protect can cover today.
        </p>
        <div className="scan-idle-row">
          <span className="scan-idle-note">
            STATIC SCAN · NO AI CALL · NOTHING LEAVES YOUR TENANT
          </span>
          <button className="btn-plex" onClick={runScan}>
            Assess readiness
          </button>
        </div>
      </div>
    )
  }

  // ── Single file: loading → verdict ──
  if (loading || !report) {
    return (
      <div>
        {header}
        <div className="busy-row">
          <span className="spinner" />
          <span className="loading-text">Scanning {state.filename} for a headless surface…</span>
        </div>
      </div>
    )
  }

  const cls = report.classes[0]
  const ready = cls.bucket === 'net-ready'

  return (
    <div>
      {header}
      <p className="step-subtitle">
        Before any AI is spent, VBGone checks whether{' '}
        <code className="inline-mono">{cls.file}</code> exposes a headless business-logic surface —
        one that compiles and runs on the CLR without WinForms.
      </p>
      <VerdictCard cls={cls} ready={ready} />
      <div className="confidence-note">
        Static estimate · heuristic classification · no code sent to the model.
      </div>
    </div>
  )
}

function BucketTag({ bucket, sm }: { bucket: Bucket; sm?: boolean }) {
  const meta = BUCKETS[bucket]
  return (
    <span
      className={`bucket-tag${sm ? ' bucket-tag-sm' : ''}`}
      style={{ background: meta.fill, color: meta.color }}
      title={meta.tip}
    >
      {meta.label}
    </span>
  )
}

function VerdictCard({ cls, ready }: { cls: ClassReadiness; ready: boolean }) {
  const meta = BUCKETS[cls.bucket]
  const headline = ready
    ? 'Ready to protect — this class has a headless business-logic surface.'
    : `Can't protect this as-is — ${cls.reason}.`

  return (
    <div
      className={`verdict-card ${ready ? 'verdict-ready' : 'verdict-blocked'}`}
      data-testid="verdict-card"
    >
      <div className="verdict-head">
        <span className="verdict-icon" style={{ background: meta.fill, color: meta.color }}>
          {ready ? '✓' : '✕'}
        </span>
        <div className="verdict-head-text">
          <div className="verdict-title-row">
            <span className="verdict-class">{cls.name}</span>
            <BucketTag bucket={cls.bucket} />
          </div>
          <div className="verdict-headline">{headline}</div>
          <div className="verdict-reason">{cls.reason}</div>
        </div>
      </div>
      <div className="verdict-methods">
        <div className="verdict-methods-head">PER-METHOD</div>
        {cls.methods.map((m) => (
          <div className="verdict-method-row" key={m.name}>
            <span className="verdict-method-name">
              {m.name} <span className="verdict-method-vis">{m.visibility}</span>
            </span>
            <span className="verdict-method-reason">{m.reason}</span>
            <BucketTag bucket={m.bucket} sm />
          </div>
        ))}
      </div>
    </div>
  )
}

interface ReportProps {
  report: ReadinessReport
  filter: Bucket | 'all'
  setFilter: (f: Bucket | 'all') => void
  expanded: Record<string, boolean>
  setExpanded: (e: Record<string, boolean>) => void
  netted: string[]
  onProtectClass?: (name: string) => void
}

function PortfolioReport({
  report,
  filter,
  setFilter,
  expanded,
  setExpanded,
  netted,
  onProtectClass,
}: ReportProps) {
  const t = report.totals
  const methodsLabel = t.methods.toLocaleString('en-US')
  const pctN = Math.round((t.methodNetReady / t.methods) * 100)
  const pctW = Math.round((t.methodWindowsGated / t.methods) * 100)
  const pctR = 100 - pctN - pctW
  const seg = (pct: number) => ({ width: `${pct}%`, minWidth: pct > 0 ? 3 : 0 })

  const subtitle = `${t.netReady} of ${t.classes} classes are ready to protect today. ${t.windowsGated} unlock with a Windows runner; ${t.refactorFirst} need refactoring first.`

  const tiles: { bucket: Bucket; count: number; methods: number }[] = [
    { bucket: 'net-ready', count: t.netReady, methods: t.methodNetReady },
    { bucket: 'windows-gated', count: t.windowsGated, methods: t.methodWindowsGated },
    { bucket: 'refactor-first', count: t.refactorFirst, methods: t.methodRefactorFirst },
  ]
  const chips: { key: Bucket | 'all'; label: string; count: number }[] = [
    { key: 'all', label: 'All', count: t.classes },
    { key: 'net-ready', label: BUCKETS['net-ready'].label, count: t.netReady },
    { key: 'windows-gated', label: BUCKETS['windows-gated'].label, count: t.windowsGated },
    { key: 'refactor-first', label: BUCKETS['refactor-first'].label, count: t.refactorFirst },
  ]

  const visible = report.classes.filter((c) => filter === 'all' || c.bucket === filter)
  const rowsCaption =
    `Showing ${visible.length} of ${t.classes} classes` +
    (filter === 'all' ? '' : ` · ${BUCKETS[filter].label}`)

  const nettedReady = report.classes.filter(
    (c) => c.bucket === 'net-ready' && netted.includes(c.name),
  ).length
  const queueActive = nettedReady > 0
  const queuePct = `${Math.round((nettedReady / Math.max(t.netReady, 1)) * 100)}%`
  const remaining = t.netReady - nettedReady
  const firstReady = report.classes.find(
    (c) => c.bucket === 'net-ready' && !netted.includes(c.name),
  )?.name
  const blocked = t.netReady === 0

  const toggle = (name: string) => setExpanded({ ...expanded, [name]: !expanded[name] })

  return (
    <div className="readiness-report" data-testid="readiness-report">
      <div className="step-kicker">{KICKER}</div>
      <h2 className="step-title">Protect readiness</h2>
      <p className="step-subtitle report-subtitle">{subtitle}</p>

      {/* breakdown */}
      <div className="breakdown-panel">
        <div className="breakdown-caption">
          <span className="breakdown-caption-l">READY TO PROTECT TODAY</span>
          <span className="breakdown-caption-r">
            share of {methodsLabel} business-logic methods
          </span>
        </div>
        <div className="breakdown-bar">
          <div
            className="bar-seg"
            style={{ ...seg(pctN), background: BUCKETS['net-ready'].color }}
          />
          <div
            className="bar-seg"
            style={{ ...seg(pctW), background: BUCKETS['windows-gated'].color }}
          />
          <div
            className="bar-seg"
            style={{ ...seg(pctR), background: BUCKETS['refactor-first'].color }}
          />
        </div>
        <div className="breakdown-legend">
          {(
            [
              ['net-ready', pctN],
              ['windows-gated', pctW],
              ['refactor-first', pctR],
            ] as [Bucket, number][]
          ).map(([b, pct]) => (
            <div className="legend-item" key={b}>
              <span className="legend-swatch" style={{ background: BUCKETS[b].color }} />
              <span className="legend-label">{BUCKETS[b].label}</span>
              <span className="legend-pct">{pct}%</span>
            </div>
          ))}
        </div>
        <div className="stat-tiles">
          {tiles.map((tile) => (
            <div className="stat-tile" key={tile.bucket}>
              <div className="stat-tile-head">
                <span
                  className="stat-tile-dot"
                  style={{ background: BUCKETS[tile.bucket].color }}
                />
                <span className="stat-tile-label">{BUCKETS[tile.bucket].label.toUpperCase()}</span>
              </div>
              <div className="stat-tile-count">
                <span style={{ color: BUCKETS[tile.bucket].color }}>{tile.count}</span> classes
              </div>
              <div className="stat-tile-methods">{tile.methods} methods</div>
            </div>
          ))}
        </div>
      </div>

      <div className="confidence-note report-confidence">
        Static estimate · heuristic classification · no code sent to the model. Drill into any class
        to see the call.
      </div>

      {queueActive && (
        <div className="protect-progress-card" data-testid="queue-progress">
          <div className="protect-progress-head">
            <span className="protect-progress-title">Protection progress</span>
            <span className="protect-progress-count">
              {nettedReady} / {t.netReady} protected
            </span>
          </div>
          <div className="protect-progress-track">
            <div className="protect-progress-fill" style={{ width: queuePct }} />
          </div>
        </div>
      )}

      {/* filter chips */}
      <div className="filter-chips">
        {chips.map((c) => (
          <button
            key={c.key}
            className={`filter-chip ${filter === c.key ? 'active' : ''}`}
            onClick={() => setFilter(c.key)}
          >
            {c.label} <span className="filter-chip-count">{c.count}</span>
          </button>
        ))}
      </div>

      {/* per-class table */}
      <div className="class-table" data-testid="class-table">
        {visible.map((c) => {
          const readyCount = c.methods.filter((m) => m.bucket === 'net-ready').length
          const isNetted = netted.includes(c.name)
          const isReady = c.bucket === 'net-ready'
          return (
            <div className="class-row-wrap" key={c.name}>
              <div className="class-row" onClick={() => toggle(c.name)}>
                <div className="class-row-name">
                  <span className={`class-chevron ${expanded[c.name] ? 'open' : ''}`}>▸</span>
                  <span className="class-name">{c.name}</span>
                  <span className="class-file">{c.file}</span>
                </div>
                <BucketTag bucket={c.bucket} />
                <div className="class-row-mid">
                  <div className="class-rollup">
                    {c.methods.length} methods · {readyCount} ready
                  </div>
                  <div className="class-reason">{c.reason}</div>
                </div>
                <div className="class-action" onClick={(e) => e.stopPropagation()}>
                  {isNetted ? (
                    <span className="protected-chip">✓ Protected</span>
                  ) : isReady ? (
                    <button className="btn-plex btn-sm" onClick={() => onProtectClass?.(c.name)}>
                      Protect this class →
                    </button>
                  ) : (
                    <span className="disabled-chip" title={BUCKETS[c.bucket].tip}>
                      {c.bucket === 'windows-gated' ? 'Needs Windows runner' : 'Untangle first'}
                    </span>
                  )}
                </div>
              </div>
              {expanded[c.name] && (
                <div className="class-methods">
                  {c.methods.map((m) => (
                    <div className="class-method-row" key={m.name}>
                      <div>
                        <span className="class-method-name">{m.name}</span>
                        <span className="class-method-vis">{m.visibility}</span>
                        <div className="class-method-reason">{m.reason}</div>
                      </div>
                      <BucketTag bucket={m.bucket} sm />
                    </div>
                  ))}
                </div>
              )}
            </div>
          )
        })}
        <div className="class-table-caption">{rowsCaption}</div>
      </div>

      {blocked ? (
        <div className="portfolio-gate" data-testid="portfolio-gate">
          <span className="portfolio-gate-glyph" aria-hidden="true">
            ⚠️
          </span>
          <div>
            <div className="portfolio-gate-title">Nothing to protect yet</div>
            <div className="portfolio-gate-body">
              Every class here couples its logic to the WinForms UI, so there's no headless surface
              to pin. Protect can't cover it as-is — the business logic has to be separated into
              UI-free classes first (that's a Migrate job), or wait for the Windows runner for the
              methods that are pure but UI-bound.
            </div>
          </div>
        </div>
      ) : (
        <div className="proceed-panel" data-testid="proceed-panel">
          <div>
            <div className="proceed-title">
              {nettedReady === 0 ? 'Ready to protect' : 'Keep going'}
            </div>
            <div className="proceed-desc">
              {nettedReady === 0
                ? 'Start with the classes that are ready. Each drills into Baseline → Baseline Tests, then returns here.'
                : `${remaining} classes still ready to protect. Windows-runner and UI-tangled classes stay queued.`}
            </div>
          </div>
          <button
            className="btn-plex"
            onClick={() => firstReady && onProtectClass?.(firstReady)}
            disabled={!firstReady}
          >
            Protect the {remaining} ready classes →
          </button>
        </div>
      )}
    </div>
  )
}
