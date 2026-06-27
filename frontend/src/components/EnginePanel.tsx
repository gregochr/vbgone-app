import { useState } from 'react'
import { useWizardConfig } from '../config/WizardConfigContext'
import { ENGINE_ROWS, MODELS, PROVIDERS, modelFor, providerColor } from '../config/engine'
import type { ProviderId } from '../config/engine'
import './EnginePanel.css'

export function EnginePanel() {
  const {
    engineOpen,
    closeEngine,
    provider,
    setProvider,
    modelOverrides,
    setOverride,
    resetOverrides,
  } = useWizardConfig()
  const [advanced, setAdvanced] = useState(false)

  if (!engineOpen) return null

  const prov = PROVIDERS[provider]

  const providerCard = (id: ProviderId) => {
    const p = PROVIDERS[id]
    const selected = provider === id
    return (
      <button
        type="button"
        className={`provider-card ${selected ? 'selected' : ''}`}
        style={
          selected
            ? ({
                borderColor: providerColor(id),
                boxShadow: `0 0 0 3px color-mix(in srgb, ${providerColor(id)} 12%, transparent)`,
              } as React.CSSProperties)
            : undefined
        }
        onClick={() => setProvider(id)}
        aria-pressed={selected}
      >
        <div className="provider-card-head">
          <span className="provider-dot" style={{ background: providerColor(id) }} />
          <span className="provider-name">{p.name}</span>
        </div>
        <div className="provider-blurb">{p.blurb}</div>
      </button>
    )
  }

  return (
    <div className="engine-scrim" onClick={closeEngine} data-testid="engine-panel">
      <div
        className="engine-modal"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-label="AI engine"
      >
        <div className="engine-header">
          <div>
            <div className="engine-title">AI engine</div>
            <div className="engine-subtitle">
              One provider abstraction · set once, applies to the whole run.
            </div>
          </div>
          <button type="button" className="engine-close" onClick={closeEngine} aria-label="Close">
            ×
          </button>
        </div>

        <div className="engine-body">
          <div className="micro-label engine-section-label">PROVIDER</div>
          <div className="provider-grid">
            {providerCard('anthropic')}
            {providerCard('copilot')}
          </div>

          <div className="engine-stage-head">
            <div className="micro-label">MODEL PER STAGE</div>
            <button
              type="button"
              className={`override-toggle ${advanced ? 'on' : ''}`}
              onClick={() => setAdvanced((v) => !v)}
              aria-pressed={advanced}
            >
              {advanced ? 'Using overrides' : 'Override'}
            </button>
          </div>

          {ENGINE_ROWS.map((row) => {
            const current = modelFor(provider, row.role, modelOverrides)
            return (
              <div className="engine-row" key={row.label}>
                <div className="engine-row-text">
                  <div className="engine-row-label">{row.label}</div>
                  <div className="engine-row-desc">{row.desc}</div>
                </div>
                {advanced ? (
                  <select
                    className="engine-select"
                    value={current}
                    onChange={(e) => setOverride(row.role, e.target.value)}
                    aria-label={`${row.label} model`}
                  >
                    {MODELS[provider].map((m) => (
                      <option key={m.id} value={m.id}>
                        {m.label}
                      </option>
                    ))}
                  </select>
                ) : (
                  <span className="engine-model-chip">
                    {MODELS[provider].find((m) => m.id === current)?.label ?? current}
                  </span>
                )}
              </div>
            )
          })}

          <div className="engine-footer">
            <button type="button" className="engine-reset" onClick={resetOverrides}>
              Reset to {prov.name} defaults
            </button>
            <button type="button" className="btn-primary btn-sm" onClick={closeEngine}>
              Done
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
