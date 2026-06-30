/**
 * Engine configuration — the single source of truth for the target-language map,
 * the AI-provider abstraction, and the per-role model defaults.
 *
 * Mirrors the backend's canonical model ids (com.vbgone.ai.AiProviderRegistry).
 * Keep ids in sync with the backend; labels are display-only.
 */

export type TargetLanguage = 'csharp' | 'java'
export type ProviderId = 'anthropic' | 'copilot'
export type Role = 'reasoning' | 'mechanical' | 'implementation' | 'escalation'
export type ModelOverrides = Partial<Record<Role, string>>

/**
 * Wizard mode. `migrate` (default) replaces VB.NET with fresh tested code;
 * `protect` leaves the original VB.NET running and pins its behaviour with a
 * characterisation net. Protect is C#-only — see AppHeader's TARGET lock.
 */
export type Mode = 'migrate' | 'protect'

/* ── Target language map ── */

export interface LangSpec {
  lang: string
  ext: string
  testFw: string
  testCmd: string
  runner: string
  ide: string
  notImpl: string
  linter: string
  mutationTool: string
  outputRepo: string
  ifaceName: string
  implName: string
}

export const LANGS: Record<TargetLanguage, LangSpec> = {
  csharp: {
    lang: 'C#',
    ext: '.cs',
    testFw: 'NUnit',
    testCmd: 'dotnet test',
    runner: '.NET SDK 10',
    ide: 'Rider / Visual Studio',
    notImpl: 'NotImplementedException',
    linter: 'Roslynator',
    mutationTool: 'Stryker.NET',
    outputRepo: 'vbgone-output',
    ifaceName: 'IOrderProcessor',
    implName: 'OrderProcessor',
  },
  java: {
    lang: 'Java',
    ext: '.java',
    testFw: 'JUnit 5',
    testCmd: 'mvn test',
    runner: 'JDK 21 / Maven',
    ide: 'IntelliJ IDEA',
    notImpl: 'UnsupportedOperationException',
    linter: 'SpotBugs',
    mutationTool: 'PIT',
    outputRepo: 'vbgone-output',
    ifaceName: 'OrderProcessor',
    implName: 'OrderProcessorImpl',
  },
}

/* ── Providers ── */

export interface ProviderSpec {
  name: string
  vendor: string
  /** CSS custom-property name carrying the provider accent colour. */
  colorVar: string
  blurb: string
}

export const PROVIDERS: Record<ProviderId, ProviderSpec> = {
  anthropic: {
    name: 'Claude',
    vendor: 'Anthropic',
    colorVar: '--provider-claude',
    blurb: 'Anthropic API · Java SDK',
  },
  copilot: {
    name: 'Copilot',
    vendor: 'GitHub',
    colorVar: '--provider-copilot',
    blurb: 'GitHub Models · Copilot API',
  },
}

/** Resolve a provider's accent colour to a usable CSS value. */
export function providerColor(provider: ProviderId): string {
  return `var(${PROVIDERS[provider].colorVar})`
}

/* ── Models ── */

export interface ModelSpec {
  id: string
  label: string
}

export const MODELS: Record<ProviderId, ModelSpec[]> = {
  anthropic: [
    { id: 'claude-opus-4-6', label: 'Claude Opus 4.6' },
    { id: 'claude-sonnet-4-6', label: 'Claude Sonnet 4.6' },
    { id: 'claude-haiku-4-5', label: 'Claude Haiku 4.5' },
  ],
  copilot: [
    { id: 'gpt-5', label: 'GPT-5' },
    { id: 'o3', label: 'o3' },
    { id: 'gpt-4.1', label: 'GPT-4.1' },
    { id: 'gpt-4o', label: 'GPT-4o' },
    { id: 'claude-sonnet-4', label: 'Claude Sonnet 4' },
    { id: 'gemini-2.5-pro', label: 'Gemini 2.5 Pro' },
  ],
}

/** Per-provider, per-role default model ids. modelFor falls back to these. */
export const DEFAULTS: Record<ProviderId, Record<Role, string>> = {
  anthropic: {
    reasoning: 'claude-sonnet-4-6',
    mechanical: 'claude-haiku-4-5',
    implementation: 'claude-sonnet-4-6',
    escalation: 'claude-opus-4-6',
  },
  copilot: {
    reasoning: 'o3',
    mechanical: 'gpt-4.1',
    implementation: 'o3',
    escalation: 'gpt-5',
  },
}

export const ROLES: Role[] = ['reasoning', 'mechanical', 'implementation', 'escalation']

/** The model id that will run a given role: per-step override, else provider default. */
export function modelFor(provider: ProviderId, role: Role, overrides: ModelOverrides): string {
  return overrides[role] ?? DEFAULTS[provider][role]
}

/** Map a model id to its display label (falls back to the id itself). */
export function modelLabel(provider: ProviderId, id: string): string {
  return MODELS[provider].find((m) => m.id === id)?.label ?? id
}

/** The display label of the model that will run a given role. */
export function modelLabelFor(provider: ProviderId, role: Role, overrides: ModelOverrides): string {
  return modelLabel(provider, modelFor(provider, role, overrides))
}

/** Whether any per-step override is active. */
export function hasOverrides(overrides: ModelOverrides): boolean {
  return ROLES.some((r) => overrides[r] != null)
}

/* ── Stepper roles ── */

/**
 * Each wizard step maps to a role (or a non-AI source). Drives the stepper
 * sub-line that shows which model will run that step.
 */
export const STEP_ROLES: (Role | 'source' | 'github')[] = [
  'source', // Upload
  'reasoning', // Analysis
  'mechanical', // Interface
  'reasoning', // Tests
  'implementation', // Implement
  'github', // Raise PR
]

/**
 * Protect mode's four steps map to roles for the stepper sub-line:
 * Upload (source) · Analysis (reasoning) · Baseline (mechanical) · Baseline Tests (reasoning).
 */
export const PROTECT_STEP_ROLES: (Role | 'source' | 'github')[] = [
  'source', // Upload
  'reasoning', // Analysis (characterise)
  'mechanical', // Baseline (pin surface)
  'reasoning', // Baseline Tests
]

/** Protect runs against the original VB.NET on the CLR — always C#/MSTest. */
export const PROTECT_TEST_FW = 'MSTest'
export const PROTECT_RUNTIME = 'CLR'

/** Engine-panel rows — the four user-tunable stages. */
export const ENGINE_ROWS: { label: string; role: Role; desc: string }[] = [
  { label: 'Analysis', role: 'reasoning', desc: 'Reads VB.NET, extracts business logic' },
  { label: 'Interface', role: 'mechanical', desc: 'Extracts method signatures' },
  { label: 'Tests + stub', role: 'reasoning', desc: 'Generates suite; stub uses mechanical model' },
  { label: 'Implementation', role: 'implementation', desc: 'Writes the class; retries escalate' },
]

/** Params sent with every AI-calling migrateApi request. */
export interface EngineParams {
  provider: ProviderId
  targetLanguage: TargetLanguage
  modelOverrides: ModelOverrides
  mode: Mode
}
