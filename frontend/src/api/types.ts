import type { Bucket } from '../config/engine'

/* Request/response contracts shared by the real client and the mock implementation. */

/** One edge-case row in Assure's Observed Behaviour block. */
export interface ObservedRow {
  cond: string
  outcome: string
  /** Colours the outcome: an exception, a silent fault, or a benign return. */
  kind: 'throws' | 'fault' | 'returns'
}

/** Per-method observed behaviour — populated only in Assure-mode analysis. */
export interface ObservedBehaviour {
  method: string
  cls: string
  rows: ObservedRow[]
}

export interface ClassInfo {
  name: string
  methods: string[]
  dependencies: string[]
  complexity: 'LOW' | 'MEDIUM' | 'HIGH'
  codeQuality?: 'POOR' | 'FAIR' | 'GOOD'
  codeSmells?: string[]
  refactoringSuggestions?: string[]
  vbAntiPatterns?: string[]
  /** Assure mode only — what each method does today, faults included. */
  observedBehaviour?: ObservedBehaviour[]
}

export interface AnalysisResult {
  sessionId: string
  classes: ClassInfo[]
  suggestedMigrationOrder: string[]
  summary: string
}

export interface InterfaceResult {
  sessionId: string
  className: string
  interfaceName: string
  code: string
}

export interface SurvivingMutant {
  line: number
  operator: string
  before: string
  after: string
  description: string
}

export interface MutationResult {
  total: number
  killed: number
  survived: number
  skipped: number
  /** killed / (killed + survived), 0–100, or null when there were no compilable mutants to judge. */
  score: number | null
  survivors: SurvivingMutant[]
}

export interface MutationJobStatus {
  jobId: string
  state: 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'
  done: number
  total: number
  result: MutationResult | null
  error: string | null
}

export interface TestsResult {
  sessionId: string
  className: string
  testClassName: string
  code: string
  testCount: number
}

export interface StubResult {
  sessionId: string
  className: string
  code: string
}

export interface BuildResult {
  sessionId: string
  buildStatus: 'RED' | 'GREEN' | 'ERROR'
  total: number
  passed: number
  failed: number
  errors: string[]
  failedTests: string[]
  /**
   * Line-coverage percentage (0–100) of the code under test, or null/undefined when not
   * collected (e.g. an ERROR build). Informational only — never gates the RED/GREEN outcome.
   */
  coveragePercent?: number | null
  /**
   * Branch-coverage percentage (0–100) of the code under test — the stronger confidence signal
   * ("each decision exercised both ways"). Null/undefined when not collected. Both are measured
   * by Coverlet.
   */
  branchCoveragePercent?: number | null
}

export interface ImplementResult {
  sessionId: string
  className: string
  code: string
  mode: 'STUB' | 'CLAUDE'
}

/* ── Readiness assessment (Assure's front gate) ── */

export interface MethodReadiness {
  name: string
  visibility: string
  bucket: Bucket
  reason: string
}

export interface ClassReadiness {
  name: string
  file: string
  /** Worst-case rollup of the class's methods. */
  bucket: Bucket
  reason: string
  methods: MethodReadiness[]
}

export interface ReadinessTotals {
  classes: number
  methods: number
  netReady: number
  windowsGated: number
  refactorFirst: number
  methodNetReady: number
  methodWindowsGated: number
  methodRefactorFirst: number
}

/** One input to a detected web API endpoint (a route or query-string value). */
export interface RestApiParam {
  name: string
  /** Where the value comes from — part of the path, or the query string. */
  in: 'path' | 'query'
  type: string
  /** Plain-English hint about the value. */
  note: string
}

/**
 * A web API endpoint the scan spotted in the source (an ASP.NET Web API action or an
 * ASMX web method). Assure can't wrap these yet — they're shown as a separate list, not
 * counted in the readiness buckets.
 */
export interface RestApiEndpoint {
  verb: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  /** Route template, e.g. "/api/orders/{id}". */
  route: string
  /** Class.method that handles the call. */
  handler: string
  /** VB.NET file the endpoint was found in. */
  source: string
  /** How it was spotted. */
  kind: 'Web API' | 'ASMX'
  params: RestApiParam[]
  /** Type name of the request body, or "—" when there isn't one. */
  reqType: string
  /** Sample request body, or null when the endpoint takes no body (hides the request column). */
  req: string | null
  /** Type name of the response. */
  resType: string
  /** HTTP status the handler returns, e.g. "200 OK". */
  resStatus: string
  /** Sample response body. */
  res: string
}

/** Static (no-AI) classification of an uploaded estate into the three readiness buckets. */
export interface ReadinessReport {
  sessionId: string
  totals: ReadinessTotals
  confidence: 'static' | 'llm-refined'
  classes: ClassReadiness[]
  /**
   * Web API endpoints found alongside the classes. Populated for portfolio scans that turn
   * up controllers/services; absent or empty otherwise (the panel is then hidden).
   */
  restApis?: RestApiEndpoint[]
}

/** A single public member of the pinned baseline surface (Assure step 3). */
export interface BaselineMember {
  signature: string
  /** Amber defect tag shown when the analysis flagged this member. */
  defect?: string
}

export interface BaselineResult {
  sessionId: string
  className: string
  /** e.g. "OrderProcessor.dll · public surface" */
  surfaceFile: string
  members: BaselineMember[]
}

/** One failing characterisation assertion in the "net not faithful" state. */
export interface TestFailure {
  name: string
  message: string
}

/** Result of generating the MSTest characterisation suite and running it (Assure step 4). */
export interface BaselineTestsResult {
  sessionId: string
  className: string
  testClassName: string
  code: string
  testCount: number
  /** true = the net passes (green/required); false = "net not faithful" (red error). */
  netFaithful: boolean
  build: BuildResult
  /** Failing assertions to correct when the net isn't faithful. Empty when green. */
  failures: TestFailure[]
}

/** One line of a repair diff: op is "+", "-" or " " (context). */
export interface RepairDiffLine {
  op: '+' | '-' | ' '
  text: string
}

/**
 * One escalating auto-repair attempt (Assure step 4). Because the suite runs against the
 * untouched original, a red test means the test is wrong — this rewrites just that test to
 * match the real observed behaviour, gates the rewrite, and re-runs it. `tag`: green (fixed),
 * red (still failing → escalate), escalated (no valid edit at this tier), flag (value differs
 * every run), nofix (no valid fix → quarantine).
 */
export interface RepairAttemptResult {
  tier: string
  role: 'mechanical' | 'reasoning' | 'escalation'
  model: string
  rationale: string
  diff: RepairDiffLine[]
  gate: { ok: boolean; note: string }
  rerun: { green: boolean; note: string } | null
  tag: 'green' | 'red' | 'escalated' | 'flag' | 'nofix'
  /** The suite after this attempt (carries the rewrite forward to the next tier). */
  code: string
  netFaithful: boolean
}

export interface PullRequestResult {
  sessionId: string
  prUrl: string
  branchName: string
  filesCommitted: string[]
}

export interface VbSourceFile {
  relativePath: string
  filename: string
  content: string
}

export interface ZipManifest {
  sessionId: string
  files: VbSourceFile[]
  totalFiles: number
}

export interface ProjectAnalysis {
  sessionId: string
  classes: ClassInfo[]
  suggestedMigrationOrder: string[]
  dependencyGraph: Record<string, string[]>
  summary: string
}

export interface TokenUsage {
  step: string
  model: string
  inputTokens: number
  outputTokens: number
  cost: number
}

export interface CostResult {
  sessionId: string
  steps: TokenUsage[]
  totalCost: number
}

/* ── Mock toggle ── */
