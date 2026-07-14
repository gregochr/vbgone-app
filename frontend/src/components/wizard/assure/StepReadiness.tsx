import { useEffect, useState } from 'react'
import type { WizardState } from '../WizardShell'
import {
  assess,
  assessProject,
  downloadClassTests,
  downloadTestsBundle,
} from '../../../api/migrateApi'
import type { ClassReadiness, ReadinessReport, RestApiEndpoint } from '../../../api/migrateApi'
import { BUCKETS } from '../../../config/engine'
import type { Bucket } from '../../../config/engine'
import { useWizardConfig } from '../../../config/WizardConfigContext'
import { deriveAnalysis, isActionable, readinessSubtitle } from './readiness'

interface Props {
  state: WizardState
  update: (partial: Partial<WizardState>) => void
  onReady: () => void
  /** Drill into the per-class Baseline flow for a ready class (portfolio queue). */
  onAssureClass?: (className: string) => void
}

const KICKER = 'STEP 02 · READINESS'

/**
 * Assure step 2 — the front gate. A static (no-AI) pass that classifies the source into
 * three readiness buckets. A single `.vb` renders an instant verdict card; a `.zip` portfolio
 * renders the readiness report + a queue of ready classes to assure one at a time.
 */
export function StepReadiness({ state, update, onReady, onAssureClass }: Props) {
  const { runner } = useWizardConfig()
  const windowsOn = runner === 'windows'
  const isPortfolio = state.filename.toLowerCase().endsWith('.zip')
  const report = state.readiness
  // For a single .vb the report's file equals the filename; reuse only a matching report.
  const singleMatches = report?.classes[0]?.file === state.filename
  const usable = report && (isPortfolio ? report.totals.classes >= 1 : singleMatches)
  // A completed scan that found nothing (e.g. an empty/placeholder source).
  const scannedEmpty = isPortfolio && report != null && report.totals.classes === 0

  const [loading, setLoading] = useState(!isPortfolio && !usable)
  const [scanning, setScanning] = useState(false)
  const [scanCount, setScanCount] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState<Bucket | 'all'>('all')
  const [expanded, setExpanded] = useState<Record<string, boolean>>({})

  const netted = state.netted ?? []
  const assuredGreen = state.assuredGreen ?? []

  const store = (r: ReadinessReport) =>
    update({ readiness: r, analysis: deriveAnalysis(r, isPortfolio, windowsOn) })

  // Single-file: auto-scan on mount. Portfolio: wait for an explicit "Assess readiness" click.
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    if (isPortfolio || usable) {
      if (usable && !isPortfolio && report!.classes.some((c) => isActionable(c.bucket, windowsOn)))
        onReady()
      return
    }
    setLoading(true)
    assess(state.filename, state.content)
      .then((r) => {
        store(r)
        setLoading(false)
        if (r.classes.some((c) => isActionable(c.bucket, windowsOn))) onReady()
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
    // A real uploaded estate goes to /assess-project (extract + classify across files);
    // the content-based demos go to /assess.
    const scan = state.zipFile
      ? assessProject(state.zipFile)
      : assess(state.filename, state.content)
    scan
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
            if (r.classes.some((c) => isActionable(c.bucket, windowsOn))) onReady()
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
        {isPortfolio ? 'Assess the estate' : 'Can we assure this yet?'}
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
          {...{
            report: report!,
            filter,
            setFilter,
            expanded,
            setExpanded,
            netted,
            assuredGreen,
            onAssureClass,
            windowsOn,
          }}
        />
      )
    if (scanning && !scannedEmpty) {
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
    if (scannedEmpty) {
      // A GitHub-repo estate keeps its source server-side only (no zipFile, empty content), so
      // re-scanning from here can't recover it — point the user back to Upload instead of offering
      // a Re-scan that would run against empty content.
      const fromRepo = !!state.repoSlug
      const canRescan = !fromRepo && !!(state.zipFile || state.content)
      return (
        <div>
          <div className="step-kicker">{KICKER}</div>
          <h2 className="step-title">No classes found</h2>
          <div className="portfolio-gate" data-testid="scan-empty">
            <span className="portfolio-gate-glyph" aria-hidden="true">
              ⚠️
            </span>
            <div>
              <div className="portfolio-gate-title">Nothing to classify</div>
              <div className="portfolio-gate-body">
                The scan didn't find any VB.NET classes in{' '}
                <code className="inline-mono">{state.repoSlug ?? state.filename}</code>.{' '}
                {fromRepo
                  ? 'It may hold only modules or web-API controllers — try a different repository.'
                  : 'Upload a source with at least one class, then re-scan.'}
              </div>
            </div>
          </div>
          {canRescan && (
            <div className="net-rerun-row">
              <button className="btn-plex" onClick={runScan}>
                Re-scan
              </button>
            </div>
          )}
        </div>
      )
    }
    return (
      <div>
        {header}
        <p className="step-subtitle">
          A fast static pass classifies every business-logic method as{' '}
          <strong style={{ color: BUCKETS['net-ready'].color }}>ready to assure</strong>,{' '}
          <strong style={{ color: BUCKETS['windows-gated'].color }}>needs a Windows runner</strong>,
          or <strong style={{ color: BUCKETS['refactor-first'].color }}>tangled in the UI</strong> —
          so you know up front how much of this Assure can cover today.
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
          <span className="loading-text">
            Scanning {state.filename} for business logic that runs on its own…
          </span>
        </div>
      </div>
    )
  }

  const cls = report.classes[0]
  const ready = isActionable(cls.bucket, windowsOn)

  return (
    <div>
      {header}
      <p className="step-subtitle">
        Before any AI is spent, VBGone checks whether{' '}
        <code className="inline-mono">{cls.file}</code> has business logic that can run on its own —
        without needing the WinForms screen.
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
    ? 'Ready to assure — this class has business logic that runs on its own.'
    : `Can't assure this as-is — ${cls.reason}.`

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
  /** Subset of netted whose baseline went green — the classes with a downloadable suite. */
  assuredGreen: string[]
  onAssureClass?: (name: string) => void
  /** Windows runner selected — windows-gated classes become assurable, not just net-ready ones. */
  windowsOn: boolean
}

function PortfolioReport({
  report,
  filter,
  setFilter,
  expanded,
  setExpanded,
  netted,
  assuredGreen,
  onAssureClass,
  windowsOn,
}: ReportProps) {
  const t = report.totals
  const sessionId = report.sessionId
  // The set the drill-in queue and the "assure the N classes" CTA count against: net-ready always,
  // plus windows-gated when the Windows runner is on.
  const actionableTotal = t.netReady + (windowsOn ? t.windowsGated : 0)
  const methodsLabel = t.methods.toLocaleString('en-US')
  const pctN = Math.round((t.methodNetReady / t.methods) * 100)
  const pctW = Math.round((t.methodWindowsGated / t.methods) * 100)
  const pctR = 100 - pctN - pctW
  const seg = (pct: number) => ({ width: `${pct}%`, minWidth: pct > 0 ? 3 : 0 })

  const subtitle = readinessSubtitle(report, windowsOn)

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
    (c) => isActionable(c.bucket, windowsOn) && netted.includes(c.name),
  ).length
  const queueActive = nettedReady > 0
  const queuePct = `${Math.round((nettedReady / Math.max(actionableTotal, 1)) * 100)}%`
  const remaining = actionableTotal - nettedReady
  const firstReady = report.classes.find(
    (c) => isActionable(c.bucket, windowsOn) && !netted.includes(c.name),
  )?.name
  const blocked = actionableTotal === 0
  // Every ready class is assured: the panel flips from a "keep going" CTA to a download-the-suite one.
  const allAssured = remaining === 0 && nettedReady > 0
  // Downloadable = assurable classes whose baseline actually went green (a suite exists server-side).
  // This is a subset of the netted queue, which also holds classes left early or quarantined.
  const downloadableCount = report.classes.filter(
    (c) => isActionable(c.bucket, windowsOn) && assuredGreen.includes(c.name),
  ).length

  const toggle = (name: string) => setExpanded({ ...expanded, [name]: !expanded[name] })

  return (
    <div className="readiness-report" data-testid="readiness-report">
      <div className="step-kicker">{KICKER}</div>
      <h2 className="step-title">Assure readiness</h2>
      <p className="step-subtitle report-subtitle">{subtitle}</p>

      {/* breakdown */}
      <div className="breakdown-panel">
        <div className="breakdown-caption">
          <span className="breakdown-caption-l">READY TO ASSURE TODAY</span>
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

      {report.restApis && report.restApis.length > 0 && (
        <RestApiPanel endpoints={report.restApis} />
      )}

      {queueActive && (
        <div className="assure-progress-card" data-testid="queue-progress">
          <div className="assure-progress-head">
            <span className="assure-progress-title">Assurance progress</span>
            <div className="assure-progress-head-right">
              <span className="assure-progress-count">
                {nettedReady} / {actionableTotal} assured
              </span>
              {downloadableCount > 0 && (
                <button
                  type="button"
                  className="btn-download"
                  title="Download every assured class's test suite as a zip"
                  onClick={() => downloadTestsBundle(sessionId)}
                >
                  ↓ Download all tests ({downloadableCount})
                </button>
              )}
            </div>
          </div>
          <div className="assure-progress-track">
            <div className="assure-progress-fill" style={{ width: queuePct }} />
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
          const isReady = isActionable(c.bucket, windowsOn)
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
                    <div className="class-action-netted">
                      <span className="assured-chip">✓ Assured</span>
                      {assuredGreen.includes(c.name) && (
                        <button
                          type="button"
                          className="btn-download"
                          title={`Download ${c.name}Tests.cs`}
                          onClick={() => downloadClassTests(sessionId, c.name)}
                        >
                          ↓ tests
                        </button>
                      )}
                    </div>
                  ) : isReady ? (
                    <button className="btn-plex btn-sm" onClick={() => onAssureClass?.(c.name)}>
                      Assure this class →
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
            <div className="portfolio-gate-title">Nothing to assure yet</div>
            <div className="portfolio-gate-body">
              Every class here ties its logic to the WinForms screen, so there's nothing that can
              run on its own to test. Assure can't cover it yet — the business logic has to be
              pulled out into separate classes first (that's a Migrate job), or wait for the Windows
              runner for the methods that are clean but stuck in the UI.
            </div>
          </div>
        </div>
      ) : allAssured ? (
        <div className="proceed-panel" data-testid="proceed-panel">
          <div>
            <div className="proceed-title">All ready classes assured</div>
            <div className="proceed-desc">
              {downloadableCount > 0
                ? `${downloadableCount} baseline test ${downloadableCount === 1 ? 'suite' : 'suites'} recorded against your untouched VB.NET. Download them as a runnable MSTest project to drop into your own CI — or grab any single class from its row above.`
                : 'Every ready class has been through the baseline flow, but none produced a faithful suite to download yet — revisit any class to re-run its baseline.'}
            </div>
          </div>
          {downloadableCount > 0 && (
            <button
              type="button"
              className="btn-plex"
              onClick={() => downloadTestsBundle(sessionId)}
            >
              ↓ Download all tests (.zip)
            </button>
          )}
        </div>
      ) : (
        <div className="proceed-panel" data-testid="proceed-panel">
          <div>
            <div className="proceed-title">
              {nettedReady === 0 ? 'Ready to assure' : 'Keep going'}
            </div>
            <div className="proceed-desc">
              {nettedReady === 0
                ? 'Start with the classes that are ready. Each drills into Baseline → Baseline Tests, then returns here.'
                : `${remaining} classes still ready to assure. ${
                    windowsOn
                      ? 'UI-tangled classes stay queued.'
                      : 'Windows-runner and UI-tangled classes stay queued.'
                  }`}
            </div>
          </div>
          <button
            className="btn-plex"
            onClick={() => firstReady && onAssureClass?.(firstReady)}
            disabled={!firstReady}
          >
            Assure the {remaining} ready classes →
          </button>
        </div>
      )}
    </div>
  )
}

/** Verb → chip colour. Hex (not CSS vars) so the border can carry an alpha suffix. */
const VERB_COLOR: Record<RestApiEndpoint['verb'], string> = {
  GET: '#34d399',
  POST: '#6d6af2',
  PUT: '#e3a83c',
  PATCH: '#e3a83c',
  DELETE: '#fb6f73',
}

/**
 * The web API endpoints the scan found. Assure can't wrap these yet, so this is a read-only,
 * expandable list shown next to (not inside) the readiness buckets. Rows expand to show their
 * inputs and sample request/response bodies.
 */
function RestApiPanel({ endpoints }: { endpoints: RestApiEndpoint[] }) {
  const [open, setOpen] = useState<Record<string, boolean>>({})
  const fileCount = new Set(endpoints.map((e) => e.source)).size
  const toggle = (key: string) => setOpen((o) => ({ ...o, [key]: !o[key] }))

  return (
    <div className="rest-panel" data-testid="rest-panel">
      <div className="rest-panel-head">
        <div className="rest-panel-head-main">
          <div className="rest-panel-labels">
            <span className="rest-panel-kicker">REST API ENDPOINTS FOUND</span>
            <span className="rest-panel-badge">SUPPORT COMING LATER</span>
          </div>
          <div className="rest-panel-desc">
            The scan also found{' '}
            <strong>
              {endpoints.length} web API endpoint{endpoints.length === 1 ? '' : 's'}
            </strong>{' '}
            across{' '}
            <strong>
              {fileCount} file{fileCount === 1 ? '' : 's'}
            </strong>
            . Assure can't put a safety net around these yet — support is planned, so we're listing
            them here for now.
          </div>
        </div>
        <div className="rest-panel-count">
          <div className="rest-panel-count-n">{endpoints.length}</div>
          <div className="rest-panel-count-label">endpoints</div>
        </div>
      </div>

      <div className="rest-list">
        {endpoints.map((e) => {
          const key = `${e.verb} ${e.route}`
          const isOpen = !!open[key]
          const color = VERB_COLOR[e.verb]
          const twoCol = e.req != null
          return (
            <div className="rest-row-wrap" key={key}>
              <div className="rest-row" onClick={() => toggle(key)}>
                <span className={`rest-chevron ${isOpen ? 'open' : ''}`} aria-hidden="true">
                  ▸
                </span>
                <span className="rest-verb" style={{ color, borderColor: `${color}55` }}>
                  {e.verb}
                </span>
                <span className="rest-route">{e.route}</span>
                <span className="rest-handler">{e.handler}</span>
                <span className="rest-kind">{e.kind}</span>
              </div>
              {isOpen && (
                <div className="rest-detail">
                  {e.params.length > 0 && (
                    <div className="rest-params">
                      <div className="rest-detail-label">INPUTS</div>
                      {e.params.map((p) => (
                        <div className="rest-param-row" key={p.name}>
                          <span className="rest-param-in">{p.in}</span>
                          <span className="rest-param-name">{p.name}</span>
                          <span className="rest-param-type">{p.type}</span>
                          <span className="rest-param-note">{p.note}</span>
                        </div>
                      ))}
                    </div>
                  )}
                  <div className={`rest-payloads ${twoCol ? 'two-col' : ''}`}>
                    {twoCol && (
                      <div className="rest-payload">
                        <div className="rest-payload-head">
                          <span className="rest-detail-label">REQUEST BODY</span>
                          <span className="rest-payload-type req">{e.reqType}</span>
                        </div>
                        <pre className="rest-code">{e.req}</pre>
                      </div>
                    )}
                    <div className="rest-payload">
                      <div className="rest-payload-head">
                        <span className="rest-detail-label">RESPONSE</span>
                        <span className="rest-payload-status">{e.resStatus}</span>
                        <span className="rest-payload-type res">{e.resType}</span>
                      </div>
                      {e.res && <pre className="rest-code">{e.res}</pre>}
                    </div>
                  </div>
                </div>
              )}
            </div>
          )
        })}
      </div>

      <div className="rest-footnote">
        Found by reading route settings and controller types in your code · not included in the
        class counts above · nothing was called and no request was sent.
      </div>
    </div>
  )
}
