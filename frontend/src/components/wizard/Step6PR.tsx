import { useEffect, useState } from 'react'
import type { WizardState } from './WizardShell'
import type { ProjectMode } from './WizardShell'
import { raisePR } from '../../api/migrateApi'

interface Props {
  state: WizardState
  update: (partial: Partial<WizardState>) => void
  onReady: () => void
  projectMode?: ProjectMode
}

export function Step6PR({ state, update, onReady, projectMode }: Props) {
  // In project mode, show completion screen instead of raising a PR
  if (projectMode) {
    return (
      <div>
        <h2 className="step-title">Migration Complete</h2>
        <p className="step-subtitle">
          Migration complete for <strong>{projectMode.className}</strong> {'\u2713'}
        </p>
        <p className="step-subtitle">Return to queue to migrate the next class.</p>
        <div style={{ marginTop: 24 }}>
          <button className="btn-plex" onClick={projectMode.onBackToQueue}>
            {'\u2190'} Back to Queue
          </button>
        </div>
      </div>
    )
  }

  return <Step6PRSingle state={state} update={update} onReady={onReady} />
}

function Step6PRSingle({
  state,
  update,
  onReady,
}: {
  state: WizardState
  update: (partial: Partial<WizardState>) => void
  onReady: () => void
}) {
  const [loading, setLoading] = useState(!state.prResult)
  const [error, setError] = useState<string | null>(null)

  const sessionId = state.analysis?.sessionId ?? ''
  const className = state.analysis?.classes[0]?.name ?? ''
  const branchName = `migrate/${className.toLowerCase().replace(/\s+/g, '-')}`

  useEffect(() => {
    if (state.prResult) {
      onReady()
      return
    }
    raisePR(sessionId, 'gregochr', 'vbgone-output', branchName)
      .then((result) => {
        update({ prResult: result })
        setLoading(false)
        onReady()
      })
      .catch((err) => {
        setLoading(false)
        setError(err instanceof Error ? err.message : 'PR creation failed')
      })
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  if (loading) {
    return (
      <div>
        <h2 className="step-title">Raising Pull Request</h2>
        <p className="loading-text">
          <span className="spinner" />
          Committing files and raising PR...
        </p>
      </div>
    )
  }

  if (error) {
    return (
      <div>
        <h2 className="step-title">Pull Request Failed</h2>
        <div className="build-status build-red">{error}</div>
      </div>
    )
  }

  const pr = state.prResult
  if (!pr) return null

  return (
    <div>
      <h2 className="step-title">Pull Request Raised</h2>
      <p className="step-subtitle">Migration complete! Your PR is ready for review.</p>

      <button className="btn-pr-success" disabled>
        {'\u2713'} PR Raised
      </button>

      <div className="info-card">
        <div style={{ marginBottom: 16 }}>
          <a className="pr-link" href={pr.prUrl} target="_blank" rel="noopener noreferrer">
            {pr.prUrl}
          </a>
        </div>

        <div style={{ color: 'var(--grey)', fontSize: '0.85rem', marginBottom: 12 }}>
          Branch: <code>{pr.branchName}</code>
        </div>

        <h4 style={{ marginBottom: 8 }}>Files committed</h4>
        <ul className="file-list">
          {pr.filesCommitted.map((f) => (
            <li key={f}>{f}</li>
          ))}
        </ul>
      </div>
    </div>
  )
}
