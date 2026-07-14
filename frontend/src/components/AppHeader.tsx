import { useWizardConfig } from '../config/WizardConfigContext'
import { LANGS, PROVIDERS, hasOverrides, providerColor } from '../config/engine'
import type { Mode, RunnerMode, TargetLanguage } from '../config/engine'

const USD_TO_GBP = 0.79

const JAVA_LOCK_TITLE =
  "Assure runs tests against your original VB.NET on the CLR, so it's C# only. Switch to Migrate for Java."

const RUNNER_LOCK_TITLE =
  'Migration targets modern, cross-platform .NET — its build always runs on Linux. The Windows runner only matters in Assure, where it characterises the original .NET Framework VB.'

const RUNNER_STEP_LOCK_TITLE =
  'Pick the runner on step 1, before the run begins — it is fixed once characterisation is under way.'

const RUNNER_TITLES: Record<RunnerMode, string> = {
  linux:
    'Linux · .NET 8 sidecar. Characterises net-ready classes; framework-gated classes stay blocked.',
  windows:
    'Windows · .NET Framework 4.8 runner. Also characterises the framework-gated classes the Linux sidecar cannot build.',
}

const ENGINE_LOCK_TITLE =
  'Set the engine on step 1, before you start. In Assure the provider is fixed once the run begins.'

export function AppHeader() {
  const {
    mode,
    targetLanguage,
    provider,
    modelOverrides,
    runner,
    currentStep,
    setMode,
    setTargetLanguage,
    setRunner,
    openEngine,
    sessionCost,
  } = useWizardConfig()

  const lang = LANGS[targetLanguage]
  const prov = PROVIDERS[provider]
  const overridden = hasOverrides(modelOverrides)
  const assure = mode === 'assure'
  // Assure fixes the provider once the run is under way — the engine is only
  // changeable on step 1 (Upload). Migrate leaves it changeable throughout.
  const engineLocked = assure && currentStep > 0
  // The runner only means something in Assure. In Migrate it is forced to Linux
  // (modern .NET output); in Assure it is fixed once the run is under way.
  const effectiveRunner: RunnerMode = assure ? runner : 'linux'
  const runnerRunLocked = assure && currentStep > 0

  const modeSegment = (value: Mode, label: string) => (
    <button
      type="button"
      className={`target-seg ${mode === value ? 'active' : ''}`}
      onClick={() => setMode(value)}
      aria-pressed={mode === value}
    >
      {label}
    </button>
  )

  const segment = (value: TargetLanguage, label: string) => {
    // Assure is C#-only: the Java segment is locked, not just inactive.
    const locked = assure && value === 'java'
    return (
      <button
        type="button"
        className={`target-seg ${targetLanguage === value ? 'active' : ''}${locked ? ' locked' : ''}`}
        onClick={locked ? undefined : () => setTargetLanguage(value)}
        aria-pressed={targetLanguage === value}
        aria-disabled={locked || undefined}
        title={locked ? JAVA_LOCK_TITLE : undefined}
      >
        {label}
      </button>
    )
  }

  const runnerSegment = (value: RunnerMode, label: string) => {
    // Windows is meaningless in Migrate (modern .NET output) — lock it there with an explainer.
    const migrateWindowsLock = !assure && value === 'windows'
    const locked = migrateWindowsLock || runnerRunLocked
    const title = migrateWindowsLock
      ? RUNNER_LOCK_TITLE
      : runnerRunLocked
        ? RUNNER_STEP_LOCK_TITLE
        : RUNNER_TITLES[value]
    return (
      <button
        type="button"
        className={`target-seg ${effectiveRunner === value ? 'active' : ''}${locked ? ' locked' : ''}`}
        onClick={locked ? undefined : () => setRunner(value)}
        aria-pressed={effectiveRunner === value}
        aria-disabled={locked || undefined}
        title={title}
      >
        {label}
      </button>
    )
  }

  return (
    <header className="app-header">
      <div className="header-brand">
        <div className="brand-mark">
          <svg className="brand-glyph" viewBox="0 0 40 40" aria-hidden="true">
            <rect
              x="1"
              y="1"
              width="38"
              height="38"
              rx="11"
              fill="var(--bg-elevated)"
              stroke="var(--border-strong)"
            />
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
        <span className="brand-caption">
          {assure ? 'VB.NET · behaviour safety net' : `VB.NET → ${lang.lang}`}
        </span>
      </div>

      <div className="header-controls">
        <div className="control-group">
          <span className="micro-label">MODE</span>
          <div className="target-toggle" role="group" aria-label="Wizard mode">
            {modeSegment('migrate', 'Migrate')}
            {modeSegment('assure', 'Assure')}
          </div>
        </div>

        <div className="header-divider" />

        <div className="control-group">
          <span className="micro-label">TARGET</span>
          <div className="target-toggle" role="group" aria-label="Target language">
            {segment('csharp', 'C#')}
            {segment('java', 'Java')}
          </div>
        </div>

        <div className="header-divider" />

        <div className="control-group">
          <span className="micro-label">RUNNER</span>
          <div className="target-toggle" role="group" aria-label="Characterisation runner">
            {runnerSegment('linux', 'Linux')}
            {runnerSegment('windows', 'Windows')}
          </div>
        </div>

        <div className="header-divider" />

        <div className="control-group">
          <span className="micro-label">ENGINE</span>
          <button
            type="button"
            className={`engine-button${engineLocked ? ' locked' : ''}`}
            onClick={engineLocked ? undefined : openEngine}
            aria-disabled={engineLocked || undefined}
            title={engineLocked ? ENGINE_LOCK_TITLE : undefined}
          >
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
