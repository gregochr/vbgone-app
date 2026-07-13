import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { DEFAULTS } from './engine'
import type {
  EngineParams,
  Mode,
  ModelOverrides,
  ProviderId,
  Role,
  RunnerMode,
  TargetLanguage,
} from './engine'

export interface WizardConfig {
  mode: Mode
  targetLanguage: TargetLanguage
  provider: ProviderId
  modelOverrides: ModelOverrides
  /** Assure characterisation runner (Linux net8.0 vs Windows net48). Ignored in Migrate. */
  runner: RunnerMode
  engineOpen: boolean

  /**
   * Current wizard step (0-indexed), reported by WizardShell. Drives the
   * Assure engine lock: the provider is only changeable on step 1 (Upload),
   * so the header disables the engine button once the run is under way.
   */
  currentStep: number
  setCurrentStep: (step: number) => void

  /** Switching mode forces C# in Assure; WizardShell resets its step state. */
  setMode: (mode: Mode) => void
  setTargetLanguage: (lang: TargetLanguage) => void
  setRunner: (runner: RunnerMode) => void
  /** Switching provider resets all per-step overrides to that provider's defaults. */
  setProvider: (provider: ProviderId) => void
  /** Setting an override equal to the provider default clears it. */
  setOverride: (role: Role, modelId: string) => void
  resetOverrides: () => void
  openEngine: () => void
  closeEngine: () => void

  /** Running USD session cost — surfaced in the header. WizardShell reports it. */
  sessionCost: number
  setSessionCost: (cost: number) => void

  /** The params object threaded onto every AI-calling API request. */
  engineParams: EngineParams
}

const EMPTY_OVERRIDES: ModelOverrides = {}

const noop = () => {}

/**
 * Default config used when a component is rendered outside a provider (e.g. a
 * step rendered in isolation by a unit test). The real app always wraps in
 * WizardConfigProvider via App.tsx, so this only affects standalone renders.
 */
const DEFAULT_CONFIG: WizardConfig = {
  mode: 'migrate',
  targetLanguage: 'csharp',
  provider: 'anthropic',
  modelOverrides: EMPTY_OVERRIDES,
  runner: 'linux',
  engineOpen: false,
  currentStep: 0,
  setCurrentStep: noop,
  setMode: noop,
  setTargetLanguage: noop,
  setRunner: noop,
  setProvider: noop,
  setOverride: noop,
  resetOverrides: noop,
  openEngine: noop,
  closeEngine: noop,
  sessionCost: 0,
  setSessionCost: noop,
  engineParams: {
    provider: 'anthropic',
    targetLanguage: 'csharp',
    modelOverrides: EMPTY_OVERRIDES,
    mode: 'migrate',
    runner: 'linux',
  },
}

const WizardConfigContext = createContext<WizardConfig>(DEFAULT_CONFIG)

export function WizardConfigProvider({ children }: { children: ReactNode }) {
  const [mode, setModeState] = useState<Mode>('migrate')
  const [targetLanguage, setTargetLanguageState] = useState<TargetLanguage>('csharp')
  const [provider, setProviderState] = useState<ProviderId>('anthropic')
  const [modelOverrides, setModelOverrides] = useState<ModelOverrides>(EMPTY_OVERRIDES)
  const [runner, setRunnerState] = useState<RunnerMode>('linux')
  const [engineOpen, setEngineOpen] = useState(false)
  const [sessionCost, setSessionCost] = useState(0)
  const [currentStep, setCurrentStep] = useState(0)

  const setMode = useCallback((next: Mode) => {
    setModeState(next)
    // Assure runs against the original VB.NET on the CLR — C# only.
    if (next === 'assure') setTargetLanguageState('csharp')
    // A fresh mode is a fresh run — drop the accumulated session cost and
    // snap the step back to 0 synchronously so the header's engine lock
    // doesn't flash (WizardShell also resets its own step to 0).
    setSessionCost(0)
    setCurrentStep(0)
  }, [])

  const setTargetLanguage = useCallback((lang: TargetLanguage) => {
    setTargetLanguageState(lang)
  }, [])

  const setRunner = useCallback((next: RunnerMode) => {
    setRunnerState(next)
  }, [])

  const setProvider = useCallback((next: ProviderId) => {
    setProviderState(next)
    setModelOverrides(EMPTY_OVERRIDES)
  }, [])

  const setOverride = useCallback(
    (role: Role, modelId: string) => {
      setModelOverrides((prev) => {
        const next = { ...prev }
        // Choosing the provider default clears the override.
        if (modelId === DEFAULTS[provider][role]) {
          delete next[role]
        } else {
          next[role] = modelId
        }
        return next
      })
    },
    [provider],
  )

  const resetOverrides = useCallback(() => setModelOverrides(EMPTY_OVERRIDES), [])
  const openEngine = useCallback(() => setEngineOpen(true), [])
  const closeEngine = useCallback(() => setEngineOpen(false), [])

  const engineParams = useMemo<EngineParams>(
    () => ({ provider, targetLanguage, modelOverrides, mode, runner }),
    [provider, targetLanguage, modelOverrides, mode, runner],
  )

  const value = useMemo<WizardConfig>(
    () => ({
      mode,
      targetLanguage,
      provider,
      modelOverrides,
      runner,
      engineOpen,
      currentStep,
      setCurrentStep,
      setMode,
      setTargetLanguage,
      setRunner,
      setProvider,
      setOverride,
      resetOverrides,
      openEngine,
      closeEngine,
      sessionCost,
      setSessionCost,
      engineParams,
    }),
    [
      mode,
      targetLanguage,
      provider,
      modelOverrides,
      runner,
      engineOpen,
      currentStep,
      setMode,
      setTargetLanguage,
      setRunner,
      setProvider,
      setOverride,
      resetOverrides,
      openEngine,
      closeEngine,
      sessionCost,
      engineParams,
    ],
  )

  return <WizardConfigContext.Provider value={value}>{children}</WizardConfigContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useWizardConfig(): WizardConfig {
  return useContext(WizardConfigContext)
}
