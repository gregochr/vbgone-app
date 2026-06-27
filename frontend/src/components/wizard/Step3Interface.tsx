import { useEffect, useState } from 'react'
import type { WizardState } from './WizardShell'
import { generateInterface } from '../../api/migrateApi'
import { ConfirmDialog } from './ConfirmDialog'
import { CodeBlock } from './CodeBlock'
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
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showConfirm, setShowConfirm] = useState(!state.interfaceResult)

  const className =
    state.analysis?.suggestedMigrationOrder[state.currentClassIndex] ??
    state.analysis?.classes[0]?.name ??
    ''
  const sessionId = state.analysis?.sessionId ?? ''
  const currentClassInfo = state.analysis?.classes.find((c) => c.name === className)

  useEffect(() => {
    if (state.interfaceResult) {
      onReady()
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const runGeneration = () => {
    setShowConfirm(false)
    setLoading(true)
    generateInterface(sessionId, className, engineParams)
      .then((result) => {
        update({ interfaceResult: result })
        setLoading(false)
        onReady()
      })
      .catch((err) => {
        setLoading(false)
        setError(err instanceof Error ? err.message : 'Interface generation failed')
      })
  }

  if (showConfirm) {
    return (
      <div>
        <div className="step-kicker">STEP 03 · INTERFACE</div>
        <h2 className="step-title">Define the contract</h2>
        <ConfirmDialog onConfirm={runGeneration} onCancel={() => setShowConfirm(false)}>
          <p>
            This will make an API call to {prov.name} ({mechanicalModelId}) via the {prov.vendor}{' '}
            provider.
          </p>
          <p>
            {'\uD83D\uDD12'} Your code is sent securely over HTTPS and is not stored by Anthropic
            beyond the request.
          </p>
          <p>
            {'\uD83D\uDCB0'} Claude Haiku is used here deliberately — interface generation is a
            mechanical task. Haiku costs 75% less than Sonnet and is more than capable of extracting
            method signatures and producing clean C# interface boilerplate.
          </p>
          <p>
            {'\u26A1'} Model: claude-haiku-4-5 — fast, cost-efficient, and well-suited to structured
            code generation tasks.
          </p>
          <p>Proceed?</p>
        </ConfirmDialog>
      </div>
    )
  }

  if (loading) {
    return (
      <div>
        <div className="step-kicker">STEP 03 · INTERFACE</div>
        <h2 className="step-title">Define the contract</h2>
        <div className="busy-row">
          <span className="spinner" />
          <span className="loading-text">
            Generating the {lang.lang} interface for {className}…
          </span>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div>
        <div className="step-kicker">STEP 03 · INTERFACE</div>
        <h2 className="step-title">Define the contract</h2>
        <div className="build-status build-red">{error}</div>
      </div>
    )
  }

  const iface = state.interfaceResult
  if (!iface) {
    return (
      <div>
        <div className="step-kicker">STEP 03 · INTERFACE</div>
        <h2 className="step-title">Define the contract</h2>
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
          <button className="btn-plex" onClick={() => setShowConfirm(true)}>
            Generate interface
          </button>
        </div>
      </div>
    )
  }

  return (
    <div>
      <div className="step-kicker">STEP 03 · INTERFACE</div>
      <h2 className="step-title">Define the contract</h2>
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
