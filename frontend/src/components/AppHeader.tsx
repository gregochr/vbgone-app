import { useWizardConfig } from '../config/WizardConfigContext'
import { LANGS, PROVIDERS, hasOverrides, providerColor } from '../config/engine'
import type { TargetLanguage } from '../config/engine'

const USD_TO_GBP = 0.79

export function AppHeader() {
  const { targetLanguage, provider, modelOverrides, setTargetLanguage, openEngine, sessionCost } =
    useWizardConfig()

  const lang = LANGS[targetLanguage]
  const prov = PROVIDERS[provider]
  const overridden = hasOverrides(modelOverrides)

  const segment = (value: TargetLanguage, label: string) => (
    <button
      type="button"
      className={`target-seg ${targetLanguage === value ? 'active' : ''}`}
      onClick={() => setTargetLanguage(value)}
      aria-pressed={targetLanguage === value}
    >
      {label}
      {LANGS[value].preview && <span className="target-seg-preview">preview</span>}
    </button>
  )

  return (
    <header className="app-header">
      <div className="header-brand">
        <div className="brand-mark">
          <svg className="brand-glyph" viewBox="0 0 40 40" aria-hidden="true">
            <rect x="1" y="1" width="38" height="38" rx="11" fill="var(--bg-elevated)" stroke="var(--border-strong)" />
            <path
              d="M13 14 L21 20 L13 26"
              fill="none"
              stroke="var(--accent)"
              strokeWidth="3"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
            <rect x="22" y="23.2" width="11" height="3.4" rx="1.7" fill="var(--accent)" />
          </svg>
          <span className="brand-word">vbgone</span>
        </div>
        <span className="brand-caption">VB.NET → {lang.lang}</span>
      </div>

      <div className="header-controls">
        <div className="control-group">
          <span className="micro-label">TARGET</span>
          <div className="target-toggle" role="group" aria-label="Target language">
            {segment('csharp', 'C#')}
            {segment('java', 'Java')}
          </div>
        </div>

        <div className="header-divider" />

        <div className="control-group">
          <span className="micro-label">ENGINE</span>
          <button type="button" className="engine-button" onClick={openEngine}>
            <span className="engine-dot" style={{ background: providerColor(provider) }} />
            <span>{prov.name}</span>
            {overridden && <span className="engine-custom">custom</span>}
            <span className="engine-gear" aria-hidden="true">
              ⚙
            </span>
          </button>
        </div>

        {sessionCost > 0 && (
          <>
            <div className="header-divider" />
            <div
              className="cost-readout"
              data-testid="cost-display"
              title="Estimated API spend this session"
            >
              <span className="cost-dim">~</span>${sessionCost.toFixed(4)}{' '}
              <span className="cost-dim">· £{(sessionCost * USD_TO_GBP).toFixed(4)}</span>
            </div>
          </>
        )}
      </div>
    </header>
  )
}
