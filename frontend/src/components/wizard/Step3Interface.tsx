import type { WizardState } from './WizardShell'
import { generateInterface } from '../../api/migrateApi'
import { ConfirmDialog } from './ConfirmDialog'
import { CodeBlock } from './CodeBlock'
import { StepStatus } from './StepStatus'
import { useConfirmedAction } from './useConfirmedAction'
import { useWizardConfig } from '../../config/WizardConfigContext'
import { LANGS, PROVIDERS, modelFor, modelLabelFor, providerColor } from '../../config/engine'

interface Props {
  state: WizardState
  update: (partial: Partial<WizardState>) => void
  onReady: () => void
}

export function Step3Interface({ state, update, onReady }: Props) {
  const { provider, targetLanguage, modelOverrides, engineParams } = useWizardConfig()
  const prov = PROVIDERS[provider]
  const lang = LANGS[targetLanguage]
  const mechanicalModel = modelLabelFor(provider, 'mechanical', modelOverrides)
  const mechanicalModelId = modelFor(provider, 'mechanical', modelOverrides)

  const className =
    state.analysis?.suggestedMigrationOrder[state.currentClassIndex] ??
    state.analysis?.classes[0]?.name ??
    ''
  const sessionId = state.analysis?.sessionId ?? ''
  const currentClassInfo = state.analysis?.classes.find((c) => c.name === className)

  const { confirming, loading, error, requestConfirm, cancel, run } = useConfirmedAction({
    alreadyDone: !!state.interfaceResult,
    action: () => generateInterface(sessionId, className, engineParams),
    onResult: (result) => update({ interfaceResult: result }),
    onReady,
    errorMessage: 'Interface generation failed',
  })

  const header = (
    <>
      <div className="step-kicker">STEP 03 · INTERFACE</div>
      <h2 className="step-title">Define the contract</h2>
    </>
  )

  if (loading || error) {
    return (
      <StepStatus
        header={header}
        loading={loading}
        loadingText={`Generating the ${lang.lang} interface for ${className}…`}
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
            This will make an API call to {prov.name} ({mechanicalModelId}) via the {prov.vendor}{' '}
            provider.
          </p>
          <p>
            {'\uD83D\uDD12'} Your code is sent securely over HTTPS and is not stored beyond the
            request by {prov.vendor}.
          </p>
          <p>
            {'\uD83D\uDCB0'} The mechanical model ({mechanicalModel}) is used here deliberately —
            interface generation is a mechanical task, and a cheaper model is more than capable of
            extracting method signatures and producing clean {lang.lang} interface boilerplate.
          </p>
          <p>
            {'\u26A1'} Model: {mechanicalModelId} — fast, cost-efficient, and well-suited to
            structured code generation tasks.
          </p>
          <p>Proceed?</p>
        </ConfirmDialog>
      </div>
    )
  }

  const iface = state.interfaceResult
  if (!iface) {
    return (
      <div>
        {header}
        <p className="step-subtitle">
          A {lang.lang} interface is the seam of the Strangler Fig migration — the contract both the
          legacy and the new code satisfy. UI types are stripped; only business logic remains.
        </p>
        <div className="run-card">
          <div className="run-card-model">
            <span className="model-dot" style={{ background: providerColor(provider) }} />
            <span className="model-name">{mechanicalModel}</span>
            <span className="model-caption">MECHANICAL · {prov.vendor}</span>
          </div>
          <button className="btn-plex" onClick={requestConfirm}>
            Generate interface
          </button>
        </div>
      </div>
    )
  }

  return (
    <div>
      {header}
      <p className="step-subtitle">
        Generated {lang.lang} interface for {iface.className}. Review and edit if needed before
        proceeding.
      </p>
      <div className="code-header">
        <span>
          {iface.interfaceName}
          {lang.ext}
        </span>
        <span className="code-header-caption">generated · editable</span>
      </div>
      <CodeBlock
        code={iface.code}
        editable
        onEdit={(edited) => update({ interfaceResult: { ...iface, code: edited } })}
      />
      {(currentClassInfo?.complexity === 'HIGH' || currentClassInfo?.codeQuality === 'POOR') && (
        <div className="callout-warning" data-testid="god-class-warning">
          <strong>{'\u26A0\uFE0F'} Complex class detected</strong>
          <p>
            This class has been identified as having significant complexity or poor code quality.
            VBGone has extracted only the pure business logic methods into the interface — UI event
            handlers, data access calls, and framework dependencies have been stripped.
          </p>
          <p>
            The generated tests verify the business logic contract only. This is intentional — the{' '}
            <a
              href="https://martinfowler.com/bliki/StranglerFigApplication.html"
              target="_blank"
              rel="noopener noreferrer"
            >
              Strangler Fig pattern
            </a>{' '}
            replaces the legacy class incrementally, one tested interface at a time.
          </p>
        </div>
      )}
    </div>
  )
}
