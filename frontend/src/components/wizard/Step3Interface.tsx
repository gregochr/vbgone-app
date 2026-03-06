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
  const [showConfirm, setShowConfirm] = useState(false)

  const className = state.analysis?.classes[0]?.name ?? ''
  const sessionId = state.analysis?.sessionId ?? ''

  useEffect(() => {
    if (state.interfaceResult) {
      onReady()
      return
    }
    setShowConfirm(true)
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
  if (!iface) return null

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
    </div>
  )
}
