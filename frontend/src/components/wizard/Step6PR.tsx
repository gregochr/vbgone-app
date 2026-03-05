import { useEffect, useState } from 'react'
import type { WizardState } from './WizardShell'
import { raisePR } from '../../api/migrateApi'
import { InfoTip } from './InfoTip'

interface Props {
  state: WizardState
  update: (partial: Partial<WizardState>) => void
  onReady: () => void
}

export function Step6PR({ state, update, onReady }: Props) {
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
    raisePR(sessionId, 'chrisgregory', 'vbgone-output', branchName)
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
      <h2 className="step-title">
        Pull Request Raised
        <span className="step-infotip">
          <InfoTip>
            <p>
              <strong>The generated C# files have been committed to a new branch and a PR raised against vbgone-output.</strong>{' '}
              The PR triggers the GitHub Actions CI pipeline which runs Roslynator, Coverlet, Stryker, and CodeQL.
            </p>
            <p>
              Review the PR, check the CI results, and merge when ready.
            </p>
          </InfoTip>
        </span>
      </h2>
      <p className="step-subtitle">Migration complete! Your PR is ready for review.</p>

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
