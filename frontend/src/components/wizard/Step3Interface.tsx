import { useEffect, useState } from 'react'
import type { WizardState } from './WizardShell'
import { generateInterface } from '../../api/migrateApi'

interface Props {
  state: WizardState
  update: (partial: Partial<WizardState>) => void
  onReady: () => void
}

export function Step3Interface({ state, update, onReady }: Props) {
  const [loading, setLoading] = useState(!state.interfaceResult)
  const [error, setError] = useState<string | null>(null)

  const className = state.analysis?.classes[0]?.name ?? ''
  const sessionId = state.analysis?.sessionId ?? ''

  useEffect(() => {
    if (state.interfaceResult) {
      onReady()
      return
    }
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
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

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
      <div className="code-block">{iface.code}</div>
    </div>
  )
}
