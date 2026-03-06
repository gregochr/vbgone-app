import { useEffect, useState } from 'react'
import type { WizardState } from './WizardShell'
import { generateTests, generateStub, build } from '../../api/migrateApi'
import { ConfirmDialog } from './ConfirmDialog'
import { CodeBlock } from './CodeBlock'

interface Props {
  state: WizardState
  update: (partial: Partial<WizardState>) => void
  onReady: () => void
}

type Phase = 'tests' | 'stub' | 'build' | 'done'

export function Step4Tests({ state, update, onReady }: Props) {
  const [phase, setPhase] = useState<Phase>(state.redBuild ? 'done' : 'tests')
  const [error, setError] = useState<string | null>(null)
  const [showConfirm, setShowConfirm] = useState(false)

  const className = state.analysis?.classes[0]?.name ?? ''
  const sessionId = state.analysis?.sessionId ?? ''

  useEffect(() => {
    if (state.redBuild) {
      onReady()
      return
    }
    setShowConfirm(true)
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const runPipeline = () => {
    setShowConfirm(false)
    let cancelled = false

    ;(async () => {
      try {
        setPhase('tests')
        const testsResult = await generateTests(sessionId, className)
        if (cancelled) return
        update({ tests: testsResult })

        setPhase('stub')
        await generateStub(sessionId, className)
        if (cancelled) return

        setPhase('build')
        const buildResult = await build(sessionId)
        if (cancelled) return
        update({ redBuild: buildResult })

        setPhase('done')
        onReady()
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Test generation failed')
        }
      }
    })()

    return () => {
      cancelled = true
    }
  }

  const phaseMessages: Record<Phase, string> = {
    tests: `Generating NUnit tests for ${className}...`,
    stub: 'Generating stub implementation...',
    build: 'Running dotnet test...',
    done: '',
  }

  if (showConfirm) {
    return (
      <div>
        <h2 className="step-title">Tests + Red Build</h2>
        <ConfirmDialog onConfirm={runPipeline} onCancel={() => setShowConfirm(false)}>
          <p>This will make two API calls via the Anthropic Java SDK:</p>
          <p>
            1. <strong>Claude Sonnet (claude-sonnet-4-6)</strong> — generates the NUnit test suite.
            Sonnet is used here because writing good tests requires reasoning about behaviour, edge
            cases, boundary values, and error conditions — not just mechanical translation.
          </p>
          <p>
            2. <strong>Claude Haiku (claude-haiku-4-5)</strong> — generates the stub implementation.
            A stub is pure boilerplate — implement the interface with NotImplementedException on
            every method. No reasoning required, Haiku is the right tool.
          </p>
          <p>
            {'\uD83D\uDD12'} Your code is sent securely over HTTPS and is not stored by Anthropic
            beyond the request.
          </p>
          <p>
            {'\uD83D\uDCB0'} Prompt caching is enabled across both calls — system prompts are
            cached and reused, reducing input token costs by up to 90% at scale.
          </p>
          <p>
            After both calls, dotnet test runs automatically. Expect all tests to fail — this is the
            TDD red phase.
          </p>
          <p>Proceed?</p>
        </ConfirmDialog>
      </div>
    )
  }

  if (error) {
    return (
      <div>
        <h2 className="step-title">Tests + Red Build</h2>
        <div className="build-status build-red">{error}</div>
      </div>
    )
  }

  if (phase !== 'done') {
    return (
      <div>
        <h2 className="step-title">Tests + Red Build</h2>
        <p className="loading-text">
          <span className="spinner" />
          {phaseMessages[phase]}
        </p>
      </div>
    )
  }

  return (
    <div>
      <h2 className="step-title">Tests + Red Build</h2>
      <p className="step-subtitle">
        {state.tests?.testCount} NUnit tests generated. All failing against the stub — exactly what
        we expect.
      </p>

      <div className="build-status build-red">
        {'\uD83D\uDD34'} {state.redBuild?.failed} / {state.redBuild?.total} tests failing — stub
        throws NotImplementedException
      </div>

      {state.tests && (
        <>
          <h3 style={{ marginBottom: 8 }}>Generated Tests</h3>
          <CodeBlock code={state.tests.code} />
        </>
      )}
    </div>
  )
}
