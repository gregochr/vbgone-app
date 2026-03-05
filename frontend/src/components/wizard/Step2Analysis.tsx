import { useEffect, useState } from 'react'
import type { WizardState } from './WizardShell'
import { analyse } from '../../api/migrateApi'
import { ConfirmDialog, shouldSkipConfirm } from './ConfirmDialog'
import { InfoTip } from './InfoTip'

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
    if (shouldSkipConfirm()) {
      runAnalysis()
    } else {
      setShowConfirm(true)
    }
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
        <ConfirmDialog
          message="This will send your VB.NET source to Claude (Sonnet) for analysis. Proceed?"
          onConfirm={runAnalysis}
          onCancel={() => setShowConfirm(false)}
        />
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
      <h2 className="step-title">
        Analysis Complete
        <span className="step-infotip">
          <InfoTip>
            <p>
              <strong>Claude analyses the VB.NET source and identifies classes, methods, dependencies, and complexity.</strong>{' '}
              This uses Claude Sonnet to extract business logic from UI event handlers.
            </p>
            <p>
              Complexity is rated LOW, MEDIUM, or HIGH based on the number of methods, branching logic, and dependencies between classes.
            </p>
          </InfoTip>
        </span>
      </h2>
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
