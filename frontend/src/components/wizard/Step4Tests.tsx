import { useEffect, useState } from 'react'
import type { WizardState } from './WizardShell'
import { generateTests, generateStub, build } from '../../api/migrateApi'
import { ConfirmDialog } from './ConfirmDialog'
import { CollapsibleCode } from './CollapsibleCode'
import { useWizardConfig } from '../../config/WizardConfigContext'
import { LANGS, PROVIDERS, modelFor, modelLabelFor, providerColor } from '../../config/engine'

interface Props {
  state: WizardState
  update: (partial: Partial<WizardState>) => void
  onReady: () => void
}

type Phase = 'tests' | 'stub' | 'build' | 'done'

export function Step4Tests({ state, update, onReady }: Props) {
  const { provider, targetLanguage, modelOverrides, engineParams } = useWizardConfig()
  const prov = PROVIDERS[provider]
  const lang = LANGS[targetLanguage]
  const reasoningModel = modelLabelFor(provider, 'reasoning', modelOverrides)
  const mechanicalModel = modelLabelFor(provider, 'mechanical', modelOverrides)
  const reasoningModelId = modelFor(provider, 'reasoning', modelOverrides)
  const mechanicalModelId = modelFor(provider, 'mechanical', modelOverrides)
  const [phase, setPhase] = useState<Phase>(state.redBuild ? 'done' : 'tests')
  const [error, setError] = useState<string | null>(null)
  const [showConfirm, setShowConfirm] = useState(!state.redBuild)
  const [pipelineStarted, setPipelineStarted] = useState(false)

  const className =
    state.analysis?.suggestedMigrationOrder[state.currentClassIndex] ??
    state.analysis?.classes[0]?.name ??
    ''
  const sessionId = state.analysis?.sessionId ?? ''

  useEffect(() => {
    if (state.redBuild) {
      onReady()
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const runPipeline = () => {
    setShowConfirm(false)
    setPipelineStarted(true)
    let cancelled = false

    ;(async () => {
      try {
        setPhase('tests')
        const testsResult = await generateTests(sessionId, className, engineParams)
        if (cancelled) return
        update({ tests: testsResult })

        setPhase('stub')
        const stubResult = await generateStub(sessionId, className, engineParams)
        if (cancelled) return
        update({ stubResult })

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
    tests: `Generating ${lang.testFw} tests for ${className}…`,
    stub: 'Generating stub implementation…',
    build: `Running ${lang.testCmd}…`,
    done: '',
  }

  if (showConfirm) {
    return (
      <div>
        <div className="step-kicker">STEP 04 · TESTS · RED BUILD</div>
        <h2 className="step-title">Write the tests first</h2>
        <ConfirmDialog onConfirm={runPipeline} onCancel={() => setShowConfirm(false)}>
          <p>This will make two API calls via the {prov.vendor} provider:</p>
          <p>
            1.{' '}
            <strong>
              {reasoningModel} ({reasoningModelId})
            </strong>{' '}
            — generates the {lang.testFw} test suite. The reasoning model is used here because
            writing good tests requires reasoning about behaviour, edge cases, boundary values, and
            error conditions — not just mechanical translation.
          </p>
          <p>
            2.{' '}
            <strong>
              {mechanicalModel} ({mechanicalModelId})
            </strong>{' '}
            — generates the stub implementation. A stub is pure boilerplate — implement the
            interface with NotImplementedException on every method. No reasoning required, the
            mechanical model is the right tool.
          </p>
          <p>
            {'\uD83D\uDD12'} Your code is sent securely over HTTPS and is not stored beyond the
            request by {prov.vendor}.
          </p>
          <p>
            {'\uD83D\uDCB0'} Prompt caching is enabled across both calls — system prompts are cached
            and reused, reducing input token costs by up to 90% at scale.
          </p>
          <p>
            After both calls, .NET test runs automatically. Expect all tests to fail — this is the
            TDD red phase.
          </p>
          <p>Proceed?</p>
        </ConfirmDialog>
      </div>
    )
  }

  // Cancelled state — confirm dismissed but pipeline hasn't started
  if (!showConfirm && !pipelineStarted && !state.tests && !error) {
    return (
      <div>
        <div className="step-kicker">STEP 04 · TESTS · RED BUILD</div>
        <h2 className="step-title">Write the tests first</h2>
        <p className="step-subtitle">
          {prov.name} generates a {lang.testFw} suite from the original behaviour, then a stub that
          throws {lang.notImpl}. <code>{lang.testCmd}</code> runs — everything fails. That's the{' '}
          <strong style={{ color: 'var(--red)' }}>RED</strong> phase, and it's the migration
          contract.
        </p>
        <div className="run-card">
          <div className="run-card-model">
            <span className="model-dot" style={{ background: providerColor(provider) }} />
            <span className="model-name">{reasoningModel}</span>
            <span className="model-caption">TESTS · stub on {mechanicalModel}</span>
          </div>
          <button className="btn-plex" onClick={() => setShowConfirm(true)}>
            Generate tests &amp; run red build
          </button>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div>
        <div className="step-kicker">STEP 04 · TESTS · RED BUILD</div>
        <h2 className="step-title">Write the tests first</h2>
        <div className="build-status build-red">{error}</div>
      </div>
    )
  }

  if (phase !== 'done') {
    return (
      <div>
        <div className="step-kicker">STEP 04 · TESTS · RED BUILD</div>
        <h2 className="step-title">Write the tests first</h2>
        <div className="busy-row">
          <span className="spinner" />
          <span className="loading-text">{phaseMessages[phase]}</span>
        </div>
      </div>
    )
  }

  return (
    <div>
      <div className="step-kicker">STEP 04 · TESTS · RED BUILD</div>
      <h2 className="step-title">Write the tests first</h2>
      <p className="step-subtitle">
        {state.tests?.testCount} {lang.testFw} tests generated.
        {state.redBuild?.buildStatus === 'ERROR'
          ? ' Build failed — see compilation errors below.'
          : ' All failing against the stub — this is the RED phase of Red-Green TDD.'}
      </p>

      {state.redBuild?.buildStatus === 'ERROR' ? (
        <div className="build-status build-red">
          {'\uD83D\uDD34'} Build error — generated code did not compile
          {state.redBuild.errors.length > 0 && (
            <ul
              style={{ margin: '8px 0 0', paddingLeft: 20, fontSize: '0.85rem', fontWeight: 400 }}
            >
              {state.redBuild.errors.map((err, i) => (
                <li key={i}>{err}</li>
              ))}
            </ul>
          )}
        </div>
      ) : (
        <div className="build-status build-red">
          {'\uD83D\uDD34'} {state.redBuild?.failed} / {state.redBuild?.total} tests failing — stub
          throws NotImplementedException —{' '}
          <span style={{ color: 'var(--green)' }}>this is expected</span>
        </div>
      )}

      {state.tests && (
        <CollapsibleCode
          title={`Generated Tests (${state.tests.testCount} tests)`}
          code={state.tests.code}
        />
      )}

      {state.stubResult && <CollapsibleCode title="Generated Stub" code={state.stubResult.code} />}
    </div>
  )
}
