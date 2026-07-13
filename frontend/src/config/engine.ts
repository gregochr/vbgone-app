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
 * `assure` leaves the original VB.NET running and pins its behaviour with a
 * characterisation net. Assure is C#-only — see AppHeader's TARGET lock.
 */
export type Mode = 'migrate' | 'assure'

/**
 * Which runner characterises a class in Assure: the Linux/net8.0 sidecar (net-ready classes only)
 * or the Windows/net48 runner (unlocks the framework-gated classes). Meaningful in Assure only —
 * Transform always builds its generated C# on modern .NET, so it is Linux regardless.
 */
export type RunnerMode = 'linux' | 'windows'

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
  /**
   * Shown in the engine panel but not selectable. Used for GitHub Models
   * reasoning-tier models (o3, GPT-5) that the inference endpoint does not
   * expose to personal GitHub accounts — even with paid usage enabled.
   */
  unavailable?: boolean
  /** Why the model is disabled — surfaced in the engine panel. */
  unavailableReason?: string
}

export const MODELS: Record<ProviderId, ModelSpec[]> = {
  anthropic: [
    { id: 'claude-opus-4-6', label: 'Claude Opus 4.6' },
    { id: 'claude-sonnet-4-6', label: 'Claude Sonnet 4.6' },
    { id: 'claude-haiku-4-5', label: 'Claude Haiku 4.5' },
  ],
  // GitHub Models inference catalog ids are publisher-namespaced (models.github.ai).
  // Anthropic/Gemini are NOT exposed on this endpoint — only OpenAI et al.
  // The GPT-4.1/4o family works on the free tier (verified HTTP 200). The
  // reasoning-tier models (o3, GPT-5) return 403 / are unavailable to personal
  // GitHub accounts, which cannot enable Models paid usage (Settings ▸ Models
  // 404s for individuals) — so they are shown but disabled.
  copilot: [
    { id: 'openai/gpt-4.1', label: 'GPT-4.1' },
    { id: 'openai/gpt-4o', label: 'GPT-4o' },
    { id: 'openai/gpt-4.1-mini', label: 'GPT-4.1 mini' },
    { id: 'openai/gpt-4o-mini', label: 'GPT-4o mini' },
    {
      id: 'openai/o3',
      label: 'o3',
      unavailable: true,
      unavailableReason: 'Not available to personal GitHub accounts (even with paid usage)',
    },
    {
      id: 'openai/gpt-5',
      label: 'GPT-5',
      unavailable: true,
      unavailableReason: 'Not available to personal GitHub accounts (even with paid usage)',
    },
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
  // Personal GitHub accounts can't reach o3/GPT-5 on GitHub Models, so the
  // Copilot roles default to the GPT-4.1/4o family, which works on the free tier.
  copilot: {
    reasoning: 'openai/gpt-4.1',
    mechanical: 'openai/gpt-4o-mini',
    implementation: 'openai/gpt-4.1',
    escalation: 'openai/gpt-4o',
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
 * Assure mode's four steps map to roles for the stepper sub-line:
 * Upload (source) · Readiness (static, no model) · Baseline (mechanical) · Baseline Tests (reasoning).
 */
export const ASSURE_STEP_ROLES: (Role | 'source' | 'github' | 'static')[] = [
  'source', // Upload
  'static', // Readiness (static scan, no model)
  'mechanical', // Baseline (pin surface)
  'reasoning', // Baseline Tests
]

/** Assure runs against the original VB.NET on the CLR — always C#/MSTest. */
export const ASSURE_TEST_FW = 'MSTest'
export const ASSURE_RUNTIME = 'CLR'

/* ── Readiness buckets (Assure's front-gate classification) ── */

/** Stable wire value from the static classifier. */
export type Bucket = 'net-ready' | 'windows-gated' | 'refactor-first'

export interface BucketMeta {
  /** Plain-language label — a first-time user shouldn't need the "net" metaphor. */
  label: string
  color: string
  /** Tag/icon background fill. */
  fill: string
  tip: string
}

/** Display metadata per bucket. Internal enum values stay; only labels are plain-language. */
export const BUCKETS: Record<Bucket, BucketMeta> = {
  'net-ready': {
    label: 'Ready to assure',
    color: '#34d399',
    fill: 'rgba(52,211,153,0.14)',
    tip: 'Public, UI-free — runs on the CLR today.',
  },
  'windows-gated': {
    label: 'Needs Windows runner',
    color: '#e3a83c',
    fill: 'rgba(227,168,60,0.16)',
    tip: 'Pure logic inside a WinForms class — needs a Windows runner.',
  },
  'refactor-first': {
    label: 'Tangled in the UI',
    color: '#fb6f73',
    fill: 'rgba(251,111,115,0.16)',
    tip: 'Touches controls or dialogs — separate the logic first.',
  },
}

/**
 * Heuristic: does this VB.NET source depend on the WinForms UI? Assure compiles and runs
 * the original headless on the Linux CLR sidecar, which can't host WinForms — so UI-coupled
 * source can't be netted. Used to explain the compile-failure (ERROR) state.
 */
export function looksUiCoupled(vb: string): boolean {
  return /\bImports\s+System\.Windows\.Forms\b|\bInherits\s+(System\.Windows\.Forms\.)?Form\b|\bHandles\s+\w+\.\w+|\bAs\s+(TextBox|Button|Label|Form|ComboBox|CheckBox|ListBox|DataGridView)\b|\bMsgBox\b|\bMessageBox\b/i.test(
    vb,
  )
}

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
  /** Assure characterisation runner. Optional/absent → backend defaults to 'linux'. */
  runner?: RunnerMode
}
