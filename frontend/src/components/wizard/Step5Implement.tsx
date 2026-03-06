import { useState, useEffect } from 'react'
import type { WizardState } from './WizardShell'
import { implement, buildAfterImplement } from '../../api/migrateApi'
import { ConfirmDialog } from './ConfirmDialog'
import { CodeBlock } from './CodeBlock'

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
    setPendingMode(chosen)
  }

  const handleCancel = () => {
    setPendingMode(null)
    setMode(null)
    setLoading(false)
    setError(null)
  }

  if (state.greenBuild && state.implementResult) {
    const b = state.greenBuild
    const isGreen = b.buildStatus === 'GREEN'

    return (
      <div>
        <h2 className="step-title">Implementation</h2>
        <p className="step-subtitle">
          Mode: {state.implementResult.mode === 'CLAUDE' ? 'AI' : 'Manual'}
        </p>

        <div className={`build-status ${isGreen ? 'build-green' : 'build-red'}`}>
          {b.buildStatus === 'ERROR'
            ? `\uD83D\uDD34 Build error — generated code did not compile`
            : isGreen
              ? `\uD83D\uDFE2 ${b.passed} / ${b.total} tests passing`
              : `\uD83D\uDD34 ${b.failed} / ${b.total} tests failing`}
        </div>

        <h3 style={{ marginBottom: 8 }}>Implementation</h3>
        <CodeBlock code={state.implementResult.code} />
      </div>
    )
  }

  if (pendingMode) {
    return (
      <div>
        <h2 className="step-title">Choose Implementation</h2>
        <ConfirmDialog onConfirm={() => run(pendingMode)} onCancel={handleCancel}>
          {pendingMode === 'CLAUDE' ? (
            <>
              <p>
                This will make an API call to Claude Sonnet (claude-sonnet-4-6) via the Anthropic
                Java SDK.
              </p>
              <p>
                {'\uD83D\uDD12'} Your code is sent securely over HTTPS and is not stored by
                Anthropic beyond the request.
              </p>
              <p>
                {'\uD83D\uDCB0'} Prompt caching is enabled — the system prompt is cached and reused
                across calls, reducing input token costs by up to 90% at scale.
              </p>
              <p>
                {'\u26A1'} Model: claude-sonnet-4-6 — chosen for its ability to write correct,
                idiomatic modern C#. The implementation will use expression-bodied members, pattern
                matching, and nullable reference types where appropriate.
              </p>
              <p>
                After the implementation is generated, dotnet test runs automatically. All tests
                should pass — if any fail, the implementation will need review.
              </p>
            </>
          ) : (
            <>
              <p>You have chosen to implement the C# yourself.</p>
              <p>
                {'\uD83D\uDCE5'} The stub will be downloaded — a C# class that implements the
                interface with NotImplementedException on every method.
              </p>
              <p>
                {'\uD83E\uDDEA'} Open the stub in Rider or Visual Studio alongside the NUnit test
                suite. Your goal is to make every test pass — the tests define the correct behaviour
                based on the original VB.NET source.
              </p>
              <p>
                {'\uD83D\uDCA1'} This is the recommended path for production migrations. The
                developer who implements the class reads the original VB.NET, understands the
                business logic, and owns every line of the C# replacement. No AI shortcut — just TDD
                discipline.
              </p>
              <p>No API call will be made at this step.</p>
            </>
          )}
          <p>Proceed?</p>
        </ConfirmDialog>
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
      <h2 className="step-title">Choose Implementation</h2>
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
