import { useState, useEffect } from 'react'
import type { WizardState } from './WizardShell'
import { implement, buildAfterImplement } from '../../api/migrateApi'
import { ConfirmDialog, shouldSkipConfirm } from './ConfirmDialog'
import { InfoTip } from './InfoTip'

interface Props {
  state: WizardState
  update: (partial: Partial<WizardState>) => void
  onReady: () => void
}

export function Step5Implement({ state, update, onReady }: Props) {
  const [mode, setMode] = useState<'STUB' | 'CLAUDE' | null>(null)
  const [loading, setLoading] = useState(false)
  const [phase, setPhase] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pendingMode, setPendingMode] = useState<'STUB' | 'CLAUDE' | null>(null)

  const className = state.analysis?.classes[0]?.name ?? ''
  const sessionId = state.analysis?.sessionId ?? ''

  useEffect(() => {
    if (state.greenBuild && state.implementResult) {
      onReady()
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const run = async (chosen: 'STUB' | 'CLAUDE') => {
    setMode(chosen)
    setPendingMode(null)
    setLoading(true)
    setError(null)

    try {
      setPhase(
        chosen === 'CLAUDE'
          ? 'Claude is implementing...'
          : 'Generating stub for manual implementation...',
      )
      const implResult = await implement(sessionId, className, chosen)
      update({ implementResult: implResult })

      setPhase('Running dotnet test...')
      const buildResult = await buildAfterImplement(sessionId, chosen)
      update({ greenBuild: buildResult })

      setLoading(false)
      onReady()
    } catch (err) {
      setLoading(false)
      setError(err instanceof Error ? err.message : 'Implementation failed')
    }
  }

  const handleChoice = (chosen: 'STUB' | 'CLAUDE') => {
    if (chosen === 'CLAUDE' && !shouldSkipConfirm()) {
      setPendingMode(chosen)
    } else {
      run(chosen)
    }
  }

  if (state.greenBuild && state.implementResult) {
    const b = state.greenBuild
    const isGreen = b.buildStatus === 'GREEN'

    return (
      <div>
        <h2 className="step-title">
          Implementation
          <span className="step-infotip">
            <InfoTip>
              <p>
                <strong>The implementation is tested against the NUnit suite generated in Step 4.</strong>{' '}
                Claude mode uses Sonnet to generate a complete implementation. Manual mode gives you the stub to implement yourself.
              </p>
            </InfoTip>
          </span>
        </h2>
        <p className="step-subtitle">
          Mode: {state.implementResult.mode === 'CLAUDE' ? 'AI' : 'Manual'}
        </p>

        <div className={`build-status ${isGreen ? 'build-green' : 'build-red'}`}>
          {isGreen ? '\uD83D\uDFE2' : '\uD83D\uDD34'} {b.passed} / {b.total} tests passing
        </div>

        <h3 style={{ marginBottom: 8 }}>Implementation</h3>
        <div className="code-block">{state.implementResult.code}</div>
      </div>
    )
  }

  if (pendingMode) {
    return (
      <div>
        <h2 className="step-title">Choose Implementation</h2>
        <ConfirmDialog
          message={`This will call Claude (Sonnet) to generate a full implementation of ${className}. Proceed?`}
          onConfirm={() => run(pendingMode)}
          onCancel={() => setPendingMode(null)}
        />
      </div>
    )
  }

  if (error) {
    return (
      <div>
        <h2 className="step-title">Implementation Failed</h2>
        <div className="build-status build-red">{error}</div>
      </div>
    )
  }

  if (loading) {
    return (
      <div>
        <h2 className="step-title">Implementation</h2>
        <p className="loading-text">
          <span className="spinner" />
          {phase}
        </p>
      </div>
    )
  }

  return (
    <div>
      <h2 className="step-title">
        Choose Implementation
        <span className="step-infotip">
          <InfoTip>
            <p>
              <strong>Choose how to implement the class.</strong>{' '}
              Claude Implements uses Sonnet to generate a full implementation that passes all tests. Manual gives you the stub to complete in your IDE.
            </p>
          </InfoTip>
        </span>
      </h2>
      <p className="step-subtitle">
        Let Claude implement the class, or download the stub and implement it yourself.
      </p>

      <div className="impl-choices">
        <div
          className={`impl-choice ${mode === 'CLAUDE' ? 'selected' : ''}`}
          onClick={() => handleChoice('CLAUDE')}
        >
          <div className="impl-choice-icon">{'\uD83E\uDD16'}</div>
          <div className="impl-choice-title">Claude Implements</div>
          <div className="impl-choice-desc">AI generates the full implementation</div>
        </div>

        <div
          className={`impl-choice ${mode === 'STUB' ? 'selected' : ''}`}
          onClick={() => handleChoice('STUB')}
        >
          <div className="impl-choice-icon">{'\uD83D\uDCBB'}</div>
          <div className="impl-choice-title">Manual (Stub)</div>
          <div className="impl-choice-desc">Download stub and implement in your IDE</div>
        </div>
      </div>
    </div>
  )
}
