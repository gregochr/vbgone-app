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
}

const KICKER = 'STEP 03 · BASELINE'
const TITLE = 'Pin current behaviour'

/**
 * Protect step 3. Inverts Migrate's Interface step: instead of synthesising a clean
 * contract, it pins the concrete class's *actual* public surface against the real
 * assemblies — defects included — so a later dependency patch surfaces any change.
 */
export function Step3Baseline({ state, update, onReady }: Props) {
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
            provider to characterise the public surface of <strong>{className}</strong>.
          </p>
          <p>
            {'🔒'} Your code is sent securely over HTTPS and is not stored by Anthropic beyond the
            request.
          </p>
          <p>
            {'⚡'} Nothing is modified — VBGone pins the class exactly as it runs today, defects
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
            Characterising the public surface of {className} against the live assemblies…
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
          Protect doesn't synthesise a clean interface — there's nothing to extract toward, because
          we keep the original. This pins the concrete class's actual public surface, against the
          real assemblies, including the behaviours the analysis flagged as defects.
        </p>
        <div className="run-card">
          <div className="run-card-model">
            <span className="model-dot" style={{ background: providerColor(provider) }} />
            <span className="model-name">{mechanicalModel}</span>
            <span className="model-caption">MECHANICAL · {prov.vendor}</span>
          </div>
          <button className="btn-plex" onClick={() => setShowConfirm(true)}>
            Pin the baseline
          </button>
        </div>
      </div>
    )
  }

  return (
    <div>
      {header}
      <p className="step-subtitle">
        Pinned the actual public surface of {baseline.className} against the real assemblies — not
        synthesised.
      </p>

      {/* Inverted amber notice — the analogue of Migrate's "stripped & replaced" callout. */}
      <div className="pin-notice" data-testid="pin-notice">
        <span className="pin-notice-glyph" aria-hidden="true">
          {'⚠️'}
        </span>
        <div className="pin-notice-body">
          <div className="pin-notice-heading">Behaviour pinned as-is</div>
          <p>
            VBGone is characterising this class exactly as it runs today, including known defects
            (e.g. unhandled divide-by-zero). This is intentional — Protect locks in current
            behaviour so that patching a dependency surfaces any change.
          </p>
          <div className="pin-subbar">Green means unchanged, not correct.</div>
        </div>
      </div>

      {/* Pinned public surface — real members, with amber defect tags where flagged. */}
      <div className="code-header">
        <span>{baseline.surfaceFile}</span>
        <span className="code-header-caption">real assembly · not synthesised</span>
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
