import { useEffect, useState } from 'react'
import type { WizardState } from './WizardShell'
import type { ProjectMode } from './WizardShell'
import { ConfirmDialog } from './ConfirmDialog'
import { raisePR } from '../../api/migrateApi'
import { useWizardConfig } from '../../config/WizardConfigContext'
import { LANGS } from '../../config/engine'

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
  const { provider, targetLanguage } = useWizardConfig()
  const lang = LANGS[targetLanguage]
  const isCopilot = provider === 'copilot'
  const ciTools = isCopilot
    ? `Copilot Code Review · ${lang.linter} · ${lang.mutationTool}`
    : `${lang.linter} · ${lang.mutationTool} · CodeQL`
  const isMultiClass = (state.analysis?.suggestedMigrationOrder?.length ?? 1) > 1
  const [showConfirm, setShowConfirm] = useState(!state.prResult)
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
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  if (showConfirm) {
    return (
      <div>
        <div className="step-kicker">STEP 06 · PULL REQUEST</div>
        <h2 className="step-title">Ship it</h2>
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
        <div className="step-kicker">STEP 06 · PULL REQUEST</div>
        <h2 className="step-title">Ship it</h2>
        <p className="step-subtitle">
          Interface, implementation, and tests are committed to a branch and a Pull Request is
          raised against <code>{lang.outputRepo}</code>. The CI pipeline triggers automatically.
        </p>
        <div className="run-card">
          <div className="run-card-model">
            <span className="model-caption">NO AI CALL · GITHUB API</span>
          </div>
          <button className="btn-plex" onClick={() => setShowConfirm(true)}>
            Raise Pull Request
          </button>
        </div>
        {isCopilot && (
          <div className="provider-callout" data-testid="copilot-callout">
            <span className="provider-callout-icon">✦</span>
            <div>
              <div className="provider-callout-title">
                Copilot Code Review will auto-review this PR
              </div>
              <div className="provider-callout-body">
                Because the engine is GitHub Copilot, the PR is reviewed automatically on open —
                inline suggestions on the generated {lang.lang}, no extra step. The Anthropic path
                runs the {lang.linter} + {lang.mutationTool} pipeline instead.
              </div>
            </div>
          </div>
        )}
      </div>
    )
  }

  if (loading) {
    return (
      <div>
        <div className="step-kicker">STEP 06 · PULL REQUEST</div>
        <h2 className="step-title">Ship it</h2>
        <div className="busy-row">
          <span className="spinner" />
          <span className="loading-text">Committing files and opening the Pull Request…</span>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div>
        <div className="step-kicker">STEP 06 · PULL REQUEST</div>
        <h2 className="step-title">Ship it</h2>
        <div className="build-status build-red">{error}</div>
      </div>
    )
  }

  const pr = state.prResult
  if (!pr) return null

  return (
    <div>
      <div className="step-kicker">STEP 06 · PULL REQUEST</div>
      <h2 className="step-title">Ship it</h2>
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

        <div className="ci-footer" data-testid="ci-footer">
          CI triggered · {ciTools}
        </div>
      </div>
    </div>
  )
}
