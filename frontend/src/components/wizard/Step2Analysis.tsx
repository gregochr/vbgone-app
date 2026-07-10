import type { WizardState } from './WizardShell'
import { analyse } from '../../api/migrateApi'
import { ConfirmDialog } from './ConfirmDialog'
import { StepStatus } from './StepStatus'
import { useConfirmedAction } from './useConfirmedAction'
import { useWizardConfig } from '../../config/WizardConfigContext'
import { PROVIDERS, modelFor, modelLabelFor, providerColor } from '../../config/engine'

interface Props {
  state: WizardState
  update: (partial: Partial<WizardState>) => void
  onReady: () => void
}

export function Step2Analysis({ state, update, onReady }: Props) {
  const { provider, modelOverrides, engineParams } = useWizardConfig()
  const prov = PROVIDERS[provider]
  const reasoningModel = modelLabelFor(provider, 'reasoning', modelOverrides)
  const reasoningModelId = modelFor(provider, 'reasoning', modelOverrides)

  const { confirming, loading, error, requestConfirm, cancel, run } = useConfirmedAction({
    alreadyDone: !!state.analysis,
    action: () => analyse(state.filename, state.content, engineParams),
    onResult: (result) => update({ analysis: result }),
    onReady,
    errorMessage: 'Analysis failed',
  })

  const header = (
    <>
      <div className="step-kicker">STEP 02 · ANALYSIS</div>
      <h2 className="step-title">Analyse the source</h2>
    </>
  )

  if (loading || error) {
    return (
      <StepStatus
        header={header}
        loading={loading}
        loadingText={`${prov.name} is analysing the source…`}
        error={error}
      />
    )
  }

  if (confirming) {
    return (
      <div>
        {header}
        <ConfirmDialog onConfirm={run} onCancel={cancel}>
          <p>
            This will make an API call to {prov.name} ({reasoningModelId}) via the {prov.vendor}{' '}
            provider.
          </p>
          <p>
            {'🔒'} Your code is sent securely over HTTPS and is not stored by {prov.vendor} beyond
            the request.
          </p>
          <p>
            {'💰'} Prompt caching is enabled — the system prompt is cached and reused across calls,
            reducing input token costs by up to 90% at scale.
          </p>
          <p>
            {'⚡'} Model: {reasoningModelId} — chosen for its ability to reason about code structure
            and extract business logic from Windows Forms UI noise.
          </p>
          <p>Proceed?</p>
        </ConfirmDialog>
      </div>
    )
  }

  const analysis = state.analysis
  if (!analysis) {
    return (
      <div>
        {header}
        <p className="step-subtitle">
          {prov.name} reads the VB.NET, looks past the Windows Forms noise, and extracts the pure
          business logic underneath.
        </p>
        <div className="run-card">
          <div className="run-card-model">
            <span className="model-dot" style={{ background: providerColor(provider) }} />
            <span className="model-name">{reasoningModel}</span>
            <span className="model-caption">REASONING · {prov.vendor}</span>
          </div>
          <button className="btn-plex" onClick={requestConfirm}>
            Analyse with {prov.name}
          </button>
        </div>
      </div>
    )
  }

  return (
    <div>
      {header}
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
            {cls.dependencies.length > 0 && ` · ${cls.dependencies.length} dependencies`}
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
              {cls.refactoringSuggestions && cls.refactoringSuggestions.length > 0 && (
                <div className="code-quality-group">
                  <h4>Refactoring Suggestions</h4>
                  <ul>
                    {cls.refactoringSuggestions.map((s, i) => (
                      <li key={i}>{s}</li>
                    ))}
                  </ul>
                </div>
              )}
              {cls.vbAntiPatterns && cls.vbAntiPatterns.length > 0 && (
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
