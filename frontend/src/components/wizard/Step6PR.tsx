import { useEffect, useState } from 'react'
import type { WizardState } from './WizardShell'
import type { ProjectMode } from './WizardShell'
import { ConfirmDialog } from './ConfirmDialog'
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
  const isMultiClass = (state.analysis?.suggestedMigrationOrder?.length ?? 1) > 1
  const [showConfirm, setShowConfirm] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const sessionId = state.analysis?.sessionId ?? ''
  const totalClasses = state.analysis?.suggestedMigrationOrder?.length ?? 1
  const branchName =
    totalClasses > 1
      ? `migrate/${state.analysis?.classes[0]?.name?.toLowerCase().replace(/\s+/g, '-') ?? 'batch'}-batch`
      : `migrate/${(state.analysis?.suggestedMigrationOrder[0] ?? state.analysis?.classes[0]?.name ?? '').toLowerCase().replace(/\s+/g, '-')}`

  const doRaisePR = () => {
    setShowConfirm(false)
    setLoading(true)
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
  }

  useEffect(() => {
    if (state.prResult) {
      onReady()
      return
    }
    // Always show confirm dialog — never auto-fire PR
    setShowConfirm(true)
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  if (showConfirm) {
    return (
      <div>
        <h2 className="step-title">Raise Pull Request</h2>
        <p className="step-subtitle">
          {isMultiClass
            ? `All ${totalClasses} classes migrated successfully. Review the summary below and raise a PR when ready.`
            : 'Migration complete. Review and raise a PR when ready.'}
        </p>

        {isMultiClass && state.completedClasses.length > 0 && (
          <div className="info-card" style={{ marginBottom: 16 }}>
            <h4 style={{ marginBottom: 12 }}>Completed Classes</h4>
            {state.completedClasses.map((cls) => (
              <div
                key={cls.className}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '6px 0',
                  color: 'var(--grey)',
                  fontSize: '0.9rem',
                }}
              >
                <span style={{ color: 'var(--green)' }}>{'\u2713'}</span>
                <strong style={{ color: 'var(--text)' }}>{cls.className}</strong>
                <span>
                  — {cls.interfaceResult.interfaceName}, {cls.tests.testCount} tests, implementation
                  complete
                </span>
              </div>
            ))}
          </div>
        )}

        <ConfirmDialog onConfirm={doRaisePR} onCancel={() => setShowConfirm(false)}>
          <p>
            This will commit {totalClasses} {totalClasses === 1 ? 'interface' : 'interfaces'},{' '}
            {totalClasses} {totalClasses === 1 ? 'implementation' : 'implementations'}, and{' '}
            {totalClasses} test {totalClasses === 1 ? 'suite' : 'suites'} to a new branch and raise
            a Pull Request against vbgone-output.
          </p>
          <p>
            Branch: <code>{branchName}</code>
          </p>
          <p>{'\uD83D\uDD12'} No Claude API call — this is pure GitHub API.</p>
          <p>Proceed?</p>
        </ConfirmDialog>
      </div>
    )
  }

  // Cancelled state — confirm dismissed but PR hasn't been raised
  if (!loading && !error && !state.prResult) {
    return (
      <div>
        <h2 className="step-title">Raise Pull Request</h2>
        <p className="step-subtitle">
          Ready to raise a Pull Request{isMultiClass ? ' with all migrated classes' : ''}.
        </p>
        <button className="btn-plex" onClick={() => setShowConfirm(true)}>
          Raise Pull Request
        </button>
      </div>
    )
  }

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
      <p className="step-subtitle">
        Migration complete! {totalClasses > 1 ? `All ${totalClasses} classes committed.` : ''} Your
        PR is ready for review.
      </p>

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
