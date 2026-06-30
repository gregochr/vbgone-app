import { useEffect, useState } from 'react'
import type { WizardState } from './WizardShell'
import { analyse } from '../../api/migrateApi'
import type { AnalysisResult, ObservedRow } from '../../api/migrateApi'
import { ConfirmDialog } from './ConfirmDialog'
import { useWizardConfig } from '../../config/WizardConfigContext'
import { PROVIDERS, modelFor, modelLabelFor, providerColor } from '../../config/engine'

interface Props {
  state: WizardState
  update: (partial: Partial<WizardState>) => void
  onReady: () => void
}

export function Step2Analysis({ state, update, onReady }: Props) {
  const { mode, provider, modelOverrides, engineParams } = useWizardConfig()
  const protect = mode === 'protect'
  const prov = PROVIDERS[provider]
  const reasoningModel = modelLabelFor(provider, 'reasoning', modelOverrides)
  const reasoningModelId = modelFor(provider, 'reasoning', modelOverrides)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showConfirm, setShowConfirm] = useState(!state.analysis)

  const title = protect ? 'Characterise current behaviour' : 'Analyse the source'

  useEffect(() => {
    if (state.analysis) {
      onReady()
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const runAnalysis = () => {
    setShowConfirm(false)
    setLoading(true)
    analyse(state.filename, state.content, engineParams)
      .then((result) => {
        update({ analysis: result })
        setLoading(false)
        onReady()
      })
      .catch((err) => {
        setLoading(false)
        setError(err instanceof Error ? err.message : 'Analysis failed')
      })
  }

  if (showConfirm) {
    return (
      <div>
        <div className="step-kicker">STEP 02 · ANALYSIS</div>
        <h2 className="step-title">{title}</h2>
        <ConfirmDialog onConfirm={runAnalysis} onCancel={() => setShowConfirm(false)}>
          <p>
            This will make an API call to {prov.name} ({reasoningModelId}) via the {prov.vendor}{' '}
            provider.
          </p>
          <p>
            {'\uD83D\uDD12'} Your code is sent securely over HTTPS and is not stored by Anthropic
            beyond the request.
          </p>
          <p>
            {'\uD83D\uDCB0'} Prompt caching is enabled — the system prompt is cached and reused
            across calls, reducing input token costs by up to 90% at scale.
          </p>
          <p>
            {'\u26A1'} Model: claude-sonnet-4-6 — chosen for its ability to reason about code
            structure and extract business logic from Windows Forms UI noise.
          </p>
          <p>Proceed?</p>
        </ConfirmDialog>
      </div>
    )
  }

  if (loading) {
    return (
      <div>
        <div className="step-kicker">STEP 02 · ANALYSIS</div>
        <h2 className="step-title">{title}</h2>
        <div className="busy-row">
          <span className="spinner" />
          <span className="loading-text">
            {prov.name} is {protect ? 'characterising current behaviour' : 'analysing the source'}…
          </span>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div>
        <div className="step-kicker">STEP 02 · ANALYSIS</div>
        <h2 className="step-title">{title}</h2>
        <div className="build-status build-red">{error}</div>
      </div>
    )
  }

  const analysis = state.analysis
  if (!analysis) {
    return (
      <div>
        <div className="step-kicker">STEP 02 · ANALYSIS</div>
        <h2 className="step-title">{title}</h2>
        <p className="step-subtitle">
          {protect ? (
            <>
              {prov.name} reads the VB.NET and records what each path actually does today — the
              return values and the exact exceptions it throws on edge inputs. It describes the
              faults; it does <strong>not</strong> fix them.
            </>
          ) : (
            <>
              {prov.name} reads the VB.NET, looks past the Windows Forms noise, and extracts the
              pure business logic underneath.
            </>
          )}
        </p>
        <div className="run-card">
          <div className="run-card-model">
            <span className="model-dot" style={{ background: providerColor(provider) }} />
            <span className="model-name">{reasoningModel}</span>
            <span className="model-caption">REASONING · {prov.vendor}</span>
          </div>
          <button className="btn-plex" onClick={() => setShowConfirm(true)}>
            {protect ? 'Characterise' : 'Analyse'} with {prov.name}
          </button>
        </div>
      </div>
    )
  }

  return (
    <div>
      <div className="step-kicker">STEP 02 · ANALYSIS</div>
      <h2 className="step-title">{title}</h2>
      <div className="summary-banner">
        <span className="summary-check">✓</span>
        <span>{analysis.summary}</span>
      </div>

      {analysis.classes.map((cls) => (
        <div className="info-card" key={cls.name}>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: 12,
            }}
          >
            <h3>{cls.name}</h3>
            <span
              className={`badge badge-${cls.complexity.toLowerCase()}`}
              title="Complexity is rated by Claude based on method count, branching logic, and dependencies"
            >
              {cls.complexity}
            </span>
          </div>

          <div style={{ color: 'var(--grey)', fontSize: '0.85rem', marginBottom: 8 }}>
            {cls.methods.length} methods
            {cls.dependencies.length > 0 && ` \u00b7 ${cls.dependencies.length} dependencies`}
          </div>

          <div className="method-list">
            {cls.methods.map((m) => (
              <span className="method-tag" key={m}>
                {m}
              </span>
            ))}
          </div>

          {(cls.codeQuality === 'POOR' || cls.codeQuality === 'FAIR') && (
            <div className="code-quality-section">
              <div className="code-quality-header">
                <span className={`badge badge-quality-${cls.codeQuality.toLowerCase()}`}>
                  {cls.codeQuality}
                </span>
                <span className="code-quality-label">Code Quality</span>
              </div>
              {cls.codeSmells && cls.codeSmells.length > 0 && (
                <div className="code-quality-group">
                  <h4>Code Smells</h4>
                  <ul>
                    {cls.codeSmells.map((smell, i) => (
                      <li key={i}>{smell}</li>
                    ))}
                  </ul>
                </div>
              )}
              {/* Refactoring & anti-pattern framing imply change — hidden in Protect. */}
              {!protect && cls.refactoringSuggestions && cls.refactoringSuggestions.length > 0 && (
                <div className="code-quality-group">
                  <h4>Refactoring Suggestions</h4>
                  <ul>
                    {cls.refactoringSuggestions.map((s, i) => (
                      <li key={i}>{s}</li>
                    ))}
                  </ul>
                </div>
              )}
              {!protect && cls.vbAntiPatterns && cls.vbAntiPatterns.length > 0 && (
                <div className="code-quality-group">
                  <h4>VB.NET Anti-Patterns</h4>
                  <ul>
                    {cls.vbAntiPatterns.map((p, i) => (
                      <li key={i}>{p}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}
        </div>
      ))}

      {protect && <ObservedBehaviourBlock analysis={analysis} />}

      <div className="info-card">
        <h4 style={{ marginBottom: 0 }}>{protect ? 'Coverage order' : 'Migration Order'}</h4>
        <p
          style={{
            color: '#9b9b9b',
            fontSize: '0.75rem',
            marginTop: '4px',
            marginBottom: '8px',
            fontStyle: 'italic',
          }}
        >
          {protect
            ? 'Simplest, least-coupled paths get netted first.'
            : 'Simplest and least dependent first — building confidence and test coverage before tackling complex classes'}
        </p>
        <ol style={{ paddingLeft: 20, color: 'var(--grey)' }}>
          {analysis.suggestedMigrationOrder.map((name) => (
            <li key={name}>{name}</li>
          ))}
        </ol>
      </div>
    </div>
  )
}

const ROW_KIND_CLASS: Record<ObservedRow['kind'], string> = {
  throws: 'observed-throws',
  fault: 'observed-fault',
  returns: 'observed-returns',
}

/**
 * Protect's dominant "Observed Behaviour" panel — what each method does today, faults
 * included. Renders every class's observedBehaviour as condition → outcome rows.
 */
function ObservedBehaviourBlock({ analysis }: { analysis: AnalysisResult }) {
  const methods = analysis.classes.flatMap((c) => c.observedBehaviour ?? [])
  if (methods.length === 0) return null

  return (
    <div className="observed-behaviour" data-testid="observed-behaviour">
      <div className="observed-header">
        <span className="observed-title">OBSERVED BEHAVIOUR</span>
        <span className="observed-caption">what it does today · faults included</span>
      </div>
      <p className="observed-lede">
        Per method, the real return value and the exact exception thrown on edge inputs. This is
        what the net will pin — not how to fix it.
      </p>
      {methods.map((ob) => (
        <div className="observed-method" key={`${ob.cls}.${ob.method}`}>
          <div className="observed-method-head">
            <span className="observed-method-name">{ob.method}</span>
            <span className="observed-method-cls">{ob.cls}</span>
          </div>
          {ob.rows.map((r, i) => (
            <div className="observed-row" key={i}>
              <span className="observed-cond">{r.cond}</span>
              <span className="observed-arrow" aria-hidden="true">
                →
              </span>
              <span className={`observed-outcome ${ROW_KIND_CLASS[r.kind]}`}>{r.outcome}</span>
            </div>
          ))}
        </div>
      ))}
    </div>
  )
}
