import { useEffect, useState } from 'react'
import type { WizardState } from './WizardShell'
import { analyse } from '../../api/migrateApi'

interface Props {
  state: WizardState
  update: (partial: Partial<WizardState>) => void
  onReady: () => void
}

export function Step2Analysis({ state, update, onReady }: Props) {
  const [loading, setLoading] = useState(!state.analysis)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (state.analysis) {
      onReady()
      return
    }
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
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

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
            <span className={`badge badge-${cls.complexity.toLowerCase()}`}>{cls.complexity}</span>
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
        <h4 style={{ marginBottom: 8 }}>Migration Order</h4>
        <ol style={{ paddingLeft: 20, color: 'var(--grey)' }}>
          {analysis.suggestedMigrationOrder.map((name) => (
            <li key={name}>{name}</li>
          ))}
        </ol>
      </div>
    </div>
  )
}
