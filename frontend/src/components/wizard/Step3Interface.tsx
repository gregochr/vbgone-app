import { useEffect, useState } from 'react'
import type { WizardState } from './WizardShell'
import { generateInterface } from '../../api/migrateApi'
import { ConfirmDialog } from './ConfirmDialog'
import { CodeBlock } from './CodeBlock'

interface Props {
  state: WizardState
  update: (partial: Partial<WizardState>) => void
  onReady: () => void
}

export function Step3Interface({ state, update, onReady }: Props) {
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
    generateInterface(sessionId, className)
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
        <h2 className="step-title">Generating C# Interface</h2>
        <ConfirmDialog onConfirm={runGeneration} onCancel={() => setShowConfirm(false)}>
          <p>
            This will make an API call to Claude Haiku (claude-haiku-4-5) via the Anthropic Java
            SDK.
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
        <h2 className="step-title">Generating C# Interface</h2>
        <p className="loading-text">
          <span className="spinner" />
          Claude is generating the interface for {className}...
        </p>
      </div>
    )
  }

  if (error) {
    return (
      <div>
        <h2 className="step-title">Interface Generation Failed</h2>
        <div className="build-status build-red">{error}</div>
      </div>
    )
  }

  const iface = state.interfaceResult
  if (!iface) {
    return (
      <div>
        <h2 className="step-title">Generating C# Interface</h2>
        <p className="step-subtitle">Ready to generate the C# interface for {className}.</p>
        <button className="btn-plex" onClick={() => setShowConfirm(true)}>
          Generate Interface
        </button>
      </div>
    )
  }

  return (
    <div>
      <h2 className="step-title">{iface.interfaceName}</h2>
      <p className="step-subtitle">
        Generated C# interface for {iface.className}. Review and edit if needed before proceeding.
      </p>
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
