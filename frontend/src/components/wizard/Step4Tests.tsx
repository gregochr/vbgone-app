import type { WizardState } from './WizardShell'
import { generateTests, generateStub, build } from '../../api/migrateApi'
import { ConfirmDialog } from './ConfirmDialog'
import { CollapsibleCode } from './CollapsibleCode'
import { StepStatus } from './StepStatus'
import { useConfirmedPipeline, pipelineStep } from './useConfirmedPipeline'
import { selectActiveClass } from './wizardState'
import { useWizardConfig } from '../../config/WizardConfigContext'
import { LANGS, PROVIDERS, modelFor, modelLabelFor, providerColor } from '../../config/engine'

interface Props {
  state: WizardState
  update: (partial: Partial<WizardState>) => void
  onReady: () => void
}

export function Step4Tests({ state, update, onReady }: Props) {
  const { provider, targetLanguage, modelOverrides, engineParams } = useWizardConfig()
  const prov = PROVIDERS[provider]
  const lang = LANGS[targetLanguage]
  const reasoningModel = modelLabelFor(provider, 'reasoning', modelOverrides)
  const mechanicalModel = modelLabelFor(provider, 'mechanical', modelOverrides)
  const reasoningModelId = modelFor(provider, 'reasoning', modelOverrides)
  const mechanicalModelId = modelFor(provider, 'mechanical', modelOverrides)
  const { className, sessionId } = selectActiveClass(state)

  const { confirming, phase, error, requestConfirm, cancel, run } = useConfirmedPipeline(
    [
      pipelineStep(
        'tests',
        () => generateTests(sessionId, className, engineParams),
        (r) => update({ tests: r }),
      ),
      pipelineStep(
        'stub',
        () => generateStub(sessionId, className, engineParams),
        (r) => update({ stubResult: r }),
      ),
      pipelineStep(
        'build',
        () => build(sessionId),
        (r) => update({ redBuild: r }),
      ),
    ],
    { alreadyDone: !!state.redBuild, onReady, errorMessage: 'Test generation failed' },
  )

  const header = (
    <>
      <div className="step-kicker">STEP 04 · TESTS · RED BUILD</div>
      <h2 className="step-title">Write the tests first</h2>
    </>
  )

  const phaseMessages: Record<string, string> = {
    tests: `Generating ${lang.testFw} tests for ${className}…`,
    stub: 'Generating stub implementation…',
    build: `Running ${lang.testCmd}…`,
  }

  if (confirming) {
    return (
      <div>
        {header}
        <ConfirmDialog onConfirm={run} onCancel={cancel}>
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

  if (phase !== null || error) {
    return (
      <StepStatus
        header={header}
        loading={phase !== null}
        loadingText={phase ? phaseMessages[phase] : ''}
        error={error}
      />
    )
  }

  // Idle — confirm dismissed (or not yet run) and nothing generated yet.
  if (!state.tests) {
    return (
      <div>
        {header}
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
          <button className="btn-plex" onClick={requestConfirm}>
            Generate tests &amp; run red build
          </button>
        </div>
      </div>
    )
  }

  return (
    <div>
      {header}
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
