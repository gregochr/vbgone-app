import { useEffect, useState } from 'react'
import type { WizardState } from './WizardShell'
import { generateTests, generateStub, build } from '../../api/migrateApi'

interface Props {
  state: WizardState
  update: (partial: Partial<WizardState>) => void
  onReady: () => void
}

type Phase = 'tests' | 'stub' | 'build' | 'done'

export function Step4Tests({ state, update, onReady }: Props) {
  const [phase, setPhase] = useState<Phase>(state.redBuild ? 'done' : 'tests')
  const [error, setError] = useState<string | null>(null)

  const className = state.analysis?.classes[0]?.name ?? ''
  const sessionId = state.analysis?.sessionId ?? ''

  useEffect(() => {
    if (state.redBuild) {
      onReady()
      return
    }

    let cancelled = false

    ;(async () => {
      try {
        // Generate tests
        setPhase('tests')
        const testsResult = await generateTests(sessionId, className)
        if (cancelled) return
        update({ tests: testsResult })

        // Generate stub
        setPhase('stub')
        await generateStub(sessionId, className)
        if (cancelled) return

        // Build (expect red)
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
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const phaseMessages: Record<Phase, string> = {
    tests: `Generating NUnit tests for ${className}...`,
    stub: 'Generating stub implementation...',
    build: 'Running dotnet test...',
    done: '',
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
        {'\uD83D\uDD34'} {state.redBuild?.failed} / {state.redBuild?.total} tests failing
      </div>

      {state.tests && (
        <>
          <h3 style={{ marginBottom: 8 }}>Generated Tests</h3>
          <div className="code-block">{state.tests.code}</div>
        </>
      )}
    </div>
  )
}
