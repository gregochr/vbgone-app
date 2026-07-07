import { useEffect, useState } from 'react'
import type { WizardState } from '../WizardShell'
import { generateBaseline } from '../../../api/migrateApi'
import { ConfirmDialog } from '../ConfirmDialog'
import { useWizardConfig } from '../../../config/WizardConfigContext'
import { PROVIDERS, modelFor, modelLabelFor, providerColor } from '../../../config/engine'

interface Props {
  state: WizardState
  update: (partial: Partial<WizardState>) => void
  onReady: () => void
  /** Portfolio queue drill-in — shows the readiness breadcrumb. */
  fromQueue?: boolean
  activeClass?: string
  onBackToReadiness?: () => void
}

const KICKER = 'STEP 03 · BASELINE'
const TITLE = 'Record current behaviour'

/**
 * Protect step 3. Inverts Migrate's Interface step: instead of building a clean
 * contract, it records the concrete class's *actual* public surface against the real
 * assemblies — bugs included — so a later dependency patch surfaces any change.
 */
export function Step3Baseline({
  state,
  update,
  onReady,
  fromQueue,
  activeClass,
  onBackToReadiness,
}: Props) {
  const { provider, modelOverrides, engineParams } = useWizardConfig()
  const prov = PROVIDERS[provider]
  const mechanicalModel = modelLabelFor(provider, 'mechanical', modelOverrides)
  const mechanicalModelId = modelFor(provider, 'mechanical', modelOverrides)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showConfirm, setShowConfirm] = useState(!state.baselineResult)

  const className =
    state.analysis?.suggestedMigrationOrder[state.currentClassIndex] ??
    state.analysis?.classes[0]?.name ??
    ''
  const sessionId = state.analysis?.sessionId ?? ''

  useEffect(() => {
    if (state.baselineResult) onReady()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const runBaseline = () => {
    setShowConfirm(false)
    setLoading(true)
    generateBaseline(sessionId, className, engineParams)
      .then((result) => {
        update({ baselineResult: result })
        setLoading(false)
        onReady()
      })
      .catch((err) => {
        setLoading(false)
        setError(err instanceof Error ? err.message : 'Pinning the baseline failed')
      })
  }

  const header = (
    <>
      <div className="step-kicker">{KICKER}</div>
      {fromQueue && (
        <div className="queue-breadcrumb">
          <button className="queue-breadcrumb-link" onClick={onBackToReadiness}>
            ← readiness
          </button>
          <span className="queue-breadcrumb-sep">/</span>
          <span className="queue-breadcrumb-active">protecting {activeClass ?? className}</span>
        </div>
      )}
      <h2 className="step-title">{TITLE}</h2>
    </>
  )

  if (showConfirm) {
    return (
      <div>
        {header}
        <ConfirmDialog onConfirm={runBaseline} onCancel={() => setShowConfirm(false)}>
          <p>
            This will make an API call to {prov.name} ({mechanicalModelId}) via the {prov.vendor}{' '}
            provider to capture the current behaviour of <strong>{className}</strong>'s public
            surface.
          </p>
          <p>
            {'🔒'} Your code is sent securely over HTTPS and is not stored by Anthropic beyond the
            request.
          </p>
          <p>
            {'⚡'} Nothing is modified — VBGone records the class exactly as it runs today, bugs
            included.
          </p>
          <p>Proceed?</p>
        </ConfirmDialog>
      </div>
    )
  }

  if (loading) {
    return (
      <div>
        {header}
        <div className="busy-row">
          <span className="spinner" />
          <span className="loading-text">
            Capturing the current behaviour of {className}'s public surface against the live
            assemblies…
          </span>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div>
        {header}
        <div className="build-status build-red">{error}</div>
      </div>
    )
  }

  const baseline = state.baselineResult
  if (!baseline) {
    return (
      <div>
        {header}
        <p className="step-subtitle">
          Protect doesn't build a new, cleaner version — we keep your original as-is. This records
          the class's actual public surface, run against your real code, including the behaviours
          the scan flagged as bugs.
        </p>
        <div className="run-card">
          <div className="run-card-model">
            <span className="model-dot" style={{ background: providerColor(provider) }} />
            <span className="model-name">{mechanicalModel}</span>
            <span className="model-caption">MECHANICAL · {prov.vendor}</span>
          </div>
          <button className="btn-plex" onClick={() => setShowConfirm(true)}>
            Record the baseline
          </button>
        </div>
      </div>
    )
  }

  return (
    <div>
      {header}
      <p className="step-subtitle">
        Recorded the actual public surface of {baseline.className}, run against your real code — not
        rewritten.
      </p>

      {/* Inverted amber notice — the analogue of Migrate's "stripped & replaced" callout. */}
      <div className="pin-notice" data-testid="pin-notice">
        <span className="pin-notice-glyph" aria-hidden="true">
          {'⚠️'}
        </span>
        <div className="pin-notice-body">
          <div className="pin-notice-heading">Behaviour recorded as-is</div>
          <p>
            VBGone is recording exactly how this class runs today, including known bugs (e.g. an
            unhandled divide-by-zero). That's on purpose — Protect locks in current behaviour so
            that if patching a dependency changes anything, you'll see it.
          </p>
          <div className="pin-subbar">Green means unchanged, not correct.</div>
        </div>
      </div>

      {/* Recorded public surface — real members, with amber defect tags where flagged. */}
      <div className="code-header">
        <span>{baseline.surfaceFile}</span>
        <span className="code-header-caption">your real code · not rewritten</span>
      </div>
      <div className="pinned-surface" data-testid="pinned-surface">
        {baseline.members.map((m) => (
          <div className="pinned-surface-row" key={m.signature}>
            <span className="pinned-surface-sig">{m.signature}</span>
            {m.defect && (
              <span className="defect-tag">
                {'⚠'} {m.defect}
              </span>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
