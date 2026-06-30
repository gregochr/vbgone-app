import { useEffect, useState } from 'react'
import type { WizardState } from '../WizardShell'
import { assess } from '../../../api/migrateApi'
import type { ClassReadiness, ReadinessReport } from '../../../api/migrateApi'
import { BUCKETS } from '../../../config/engine'

interface Props {
  state: WizardState
  update: (partial: Partial<WizardState>) => void
  onReady: () => void
}

const KICKER = 'STEP 02 · READINESS'

/**
 * Protect step 2 — the front gate. A static (no-AI) pass that checks whether the source
 * exposes a headless business-logic surface before any AI is spent. For a single `.vb`
 * this renders a verdict card; a class is only "ready" (Next enabled) when it's net-ready.
 * (The portfolio report + queue is a follow-up.)
 */
export function StepReadiness({ state, update, onReady }: Props) {
  const [loading, setLoading] = useState(!state.readiness)
  const [error, setError] = useState<string | null>(null)

  const report = state.readiness

  // Derive a minimal `analysis` from a report so the existing Baseline / Baseline Tests
  // steps (which key off state.analysis) work unchanged. Protects the first net-ready class.
  const deriveAnalysis = (r: ReadinessReport): WizardState['analysis'] => {
    const target = r.classes.find((c) => c.bucket === 'net-ready') ?? r.classes[0]
    return {
      sessionId: r.sessionId,
      classes: (target ? [target] : []).map((c) => ({
        name: c.name,
        methods: c.methods.map((m) => m.name),
        dependencies: [],
        complexity: 'LOW' as const,
      })),
      suggestedMigrationOrder: target ? [target.name] : [],
      summary: r.classes[0]?.reason ?? '',
    }
  }

  // Reuse a report only if it was produced for the currently-chosen file; otherwise
  // (e.g. the user switched demos) re-scan.
  const reportMatchesFile = report?.classes[0]?.file === state.filename

  // Fetch-on-mount: kick off the static scan once. setState inside the async resolution is
  // the canonical data-fetching pattern (not a synchronous cascade), so the rule is disabled
  // for this effect.
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    if (report && reportMatchesFile) {
      if (report.classes.some((c) => c.bucket === 'net-ready')) onReady()
      return
    }
    setLoading(true)
    assess(state.filename, state.content)
      .then((r) => {
        update({ readiness: r, analysis: deriveAnalysis(r) })
        setLoading(false)
        // Ready (and the footer Next) only when there's something to protect.
        if (r.classes.some((c) => c.bucket === 'net-ready')) onReady()
      })
      .catch((err) => {
        setLoading(false)
        setError(err instanceof Error ? err.message : 'Assessment failed')
      })
  }, []) // eslint-disable-line react-hooks/exhaustive-deps
  /* eslint-enable react-hooks/set-state-in-effect */

  const header = (
    <>
      <div className="step-kicker">{KICKER}</div>
      <h2 className="step-title">Can we protect this yet?</h2>
    </>
  )

  if (loading) {
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

  if (error) {
    return (
      <div>
        {header}
        <div className="build-status build-red">{error}</div>
      </div>
    )
  }

  if (!report || report.classes.length === 0) {
    return (
      <div>
        {header}
        <div className="build-status build-red">No classes found in the source.</div>
      </div>
    )
  }

  // Single-file verdict — the worst-ranked class drives the headline.
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
            <span
              className="bucket-tag"
              style={{ background: meta.fill, color: meta.color }}
              title={meta.tip}
            >
              {meta.label}
            </span>
          </div>
          <div className="verdict-headline">{headline}</div>
          <div className="verdict-reason">{cls.reason}</div>
        </div>
      </div>

      <div className="verdict-methods">
        <div className="verdict-methods-head">PER-METHOD</div>
        {cls.methods.map((m) => {
          const mm = BUCKETS[m.bucket]
          return (
            <div className="verdict-method-row" key={m.name}>
              <span className="verdict-method-name">
                {m.name} <span className="verdict-method-vis">{m.visibility}</span>
              </span>
              <span className="verdict-method-reason">{m.reason}</span>
              <span
                className="bucket-tag bucket-tag-sm"
                style={{ background: mm.fill, color: mm.color }}
                title={mm.tip}
              >
                {mm.label}
              </span>
            </div>
          )
        })}
      </div>
    </div>
  )
}
