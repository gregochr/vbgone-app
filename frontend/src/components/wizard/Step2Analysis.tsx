import { useEffect, useState } from 'react'
import type { WizardState } from './WizardShell'
import { analyse } from '../../api/migrateApi'
import { ConfirmDialog } from './ConfirmDialog'

interface Props {
  state: WizardState
  update: (partial: Partial<WizardState>) => void
  onReady: () => void
}

export function Step2Analysis({ state, update, onReady }: Props) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showConfirm, setShowConfirm] = useState(false)

  useEffect(() => {
    if (state.analysis) {
      onReady()
      return
    }
    setShowConfirm(true)
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const runAnalysis = () => {
    setShowConfirm(false)
    setLoading(true)
    analyse(state.filename, state.content)
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
        <h2 className="step-title">Analysing VB.NET Source</h2>
        <ConfirmDialog onConfirm={runAnalysis} onCancel={() => setShowConfirm(false)}>
          <p>
            This will make an API call to Claude Sonnet (claude-sonnet-4-6) via the Anthropic Java
            SDK.
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
        <h2 className="step-title">Analysing VB.NET Source</h2>
        <p className="loading-text">
          <span className="spinner" />
          Claude is analysing your code...
        </p>
      </div>
    )
  }

  if (error) {
    return (
      <div>
        <h2 className="step-title">Analysis Failed</h2>
        <div className="build-status build-red">{error}</div>
      </div>
    )
  }

  const analysis = state.analysis
  if (!analysis) return null

  return (
    <div>
      <h2 className="step-title">Analysis Complete</h2>
      <p className="step-subtitle">{analysis.summary}</p>

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
        </div>
      ))}

      <div className="info-card">
        <h4 style={{ marginBottom: 0 }}>Migration Order</h4>
        <p
          style={{
            color: '#9b9b9b',
            fontSize: '0.75rem',
            marginTop: '4px',
            marginBottom: '8px',
            fontStyle: 'italic',
          }}
        >
          Simplest and least dependent first — building confidence and test coverage before tackling
          complex classes
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
