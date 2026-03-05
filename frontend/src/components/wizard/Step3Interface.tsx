import { useEffect, useState } from 'react'
import type { WizardState } from './WizardShell'
import { generateInterface } from '../../api/migrateApi'
import { ConfirmDialog, shouldSkipConfirm } from './ConfirmDialog'
import { InfoTip } from './InfoTip'

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
    if (shouldSkipConfirm()) {
      runGeneration()
    } else {
      setShowConfirm(true)
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
        <ConfirmDialog
          message={`This will call Claude (Haiku) to generate a C# interface for ${className}. Proceed?`}
          onConfirm={runGeneration}
          onCancel={() => setShowConfirm(false)}
        />
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
      <h2 className="step-title">
        {iface.interfaceName}
        <span className="step-infotip">
          <InfoTip>
            <p>
              <strong>Claude extracts a clean C# interface from the VB.NET source.</strong>{' '}
              UI event handlers are stripped — only the pure business logic methods remain.
            </p>
            <p>
              This uses Claude Haiku for fast, cost-effective generation. Review and edit the interface before proceeding.
            </p>
          </InfoTip>
        </span>
      </h2>
      <p className="step-subtitle">
        Generated C# interface for {iface.className}. Review and edit if needed before proceeding.
      </p>
      <div className="code-block">{iface.code}</div>
    </div>
  )
}
