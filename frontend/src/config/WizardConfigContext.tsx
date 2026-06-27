import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { DEFAULTS } from './engine'
import type { EngineParams, ModelOverrides, ProviderId, Role, TargetLanguage } from './engine'

export interface WizardConfig {
  targetLanguage: TargetLanguage
  provider: ProviderId
  modelOverrides: ModelOverrides
  engineOpen: boolean

  setTargetLanguage: (lang: TargetLanguage) => void
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
  targetLanguage: 'csharp',
  provider: 'anthropic',
  modelOverrides: EMPTY_OVERRIDES,
  engineOpen: false,
  setTargetLanguage: noop,
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
  },
}

const WizardConfigContext = createContext<WizardConfig>(DEFAULT_CONFIG)

export function WizardConfigProvider({ children }: { children: ReactNode }) {
  const [targetLanguage, setTargetLanguageState] = useState<TargetLanguage>('csharp')
  const [provider, setProviderState] = useState<ProviderId>('anthropic')
  const [modelOverrides, setModelOverrides] = useState<ModelOverrides>(EMPTY_OVERRIDES)
  const [engineOpen, setEngineOpen] = useState(false)
  const [sessionCost, setSessionCost] = useState(0)

  const setTargetLanguage = useCallback((lang: TargetLanguage) => {
    setTargetLanguageState(lang)
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
    () => ({ provider, targetLanguage, modelOverrides }),
    [provider, targetLanguage, modelOverrides],
  )

  const value = useMemo<WizardConfig>(
    () => ({
      targetLanguage,
      provider,
      modelOverrides,
      engineOpen,
      setTargetLanguage,
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
      targetLanguage,
      provider,
      modelOverrides,
      engineOpen,
      setTargetLanguage,
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
