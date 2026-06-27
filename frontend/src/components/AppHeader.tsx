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
          <span className="brand-dot" />
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
