import axios from 'axios'
import type { Bucket, EngineParams } from '../config/engine'

const api = axios.create({ baseURL: '/api/migrate' })

// Assure-mode endpoints live under a sibling base path (hybrid API: analyse is
// shared via /api/migrate with a `mode` param; baseline + baseline-tests are dedicated).
const assureApi = axios.create({ baseURL: '/api/assure' })

// Surface the backend's graceful error body ({ "error": "..." }, HTTP 422 — e.g. an
// unconfigured Copilot credential or a preview Java target) as the thrown Error message,
// so each wizard step can display it inline rather than a generic status code.
const surfaceErrorBody = (error: { response?: { data?: unknown } }): Promise<never> => {
  const data = error?.response?.data
  if (data && typeof data === 'object' && 'error' in data && typeof data.error === 'string') {
    return Promise.reject(new Error(data.error))
  }
  return Promise.reject(error)
}
api.interceptors.response.use((response) => response, surfaceErrorBody)
assureApi.interceptors.response.use((response) => response, surfaceErrorBody)

/* ── Assure test-suite artifact downloads ──
 * The baseline suites are real server-side artifacts of the baseline-tests step (they ran green
 * against the untouched VB.NET), so the download buttons stream them straight from the backend
 * rather than re-zipping generated text in the browser. These are pure URL builders + a same-origin
 * anchor trigger — identical in mock and real modes (there is no client-side generation to mock).
 */
const ASSURE_BASE = assureApi.defaults.baseURL ?? '/api/assure'

/** URL of one assured class's MSTest `.cs` file. */
export const assureClassTestsUrl = (sessionId: string, className: string): string =>
  `${ASSURE_BASE}/${encodeURIComponent(sessionId)}/tests/${encodeURIComponent(className)}`

/** URL of the assembled MSTest project (`.csproj` + README + every `.cs`) as a zip. */
export const assureTestsBundleUrl = (sessionId: string): string =>
  `${ASSURE_BASE}/${encodeURIComponent(sessionId)}/tests.zip`

/** Stream a same-origin file download via a temporary anchor (no in-browser zip assembly). */
function triggerDownload(url: string, filename: string): void {
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  a.remove()
}

/** Download one assured class's baseline test file (`{Class}Tests.cs`). */
export const downloadClassTests = (sessionId: string, className: string): void =>
  triggerDownload(assureClassTestsUrl(sessionId, className), `${className}Tests.cs`)

/** Download every assured class's tests as a runnable MSTest project zip. */
export const downloadTestsBundle = (sessionId: string): void =>
  triggerDownload(assureTestsBundleUrl(sessionId), 'VBGone-Assure-Tests.zip')

/* ── Types ── */

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

const USE_MOCKS = import.meta.env.VITE_USE_MOCKS === 'true'

/* ── Mock data ── */

const MOCK_SESSION_ID = 'mock-uuid-1234'

const DEMO_VB_CONTENT = `Public Class Form1
    'Code for SUM
    Private Sub Button1_Click(sender As Object, e As EventArgs) Handles Button1.Click
        Label3.Text = "Sum of " + TextBox1.Text + " and " + TextBox2.Text
        TextBox3.Text = Int(TextBox1.Text) + Int(TextBox2.Text)
    End Sub
    'Code for Difference
    Private Sub Button2_Click(sender As Object, e As EventArgs) Handles Button2.Click
        Label3.Text = "Difference of " + TextBox1.Text + " and " + TextBox2.Text
        TextBox3.Text = Int(TextBox1.Text) - Int(TextBox2.Text)
    End Sub
    'Code for Product
    Private Sub Button3_Click(sender As Object, e As EventArgs) Handles Button3.Click
        Label3.Text = "Product of " + TextBox1.Text + " and " + TextBox2.Text
        TextBox3.Text = Int(TextBox1.Text) * Int(TextBox2.Text)
    End Sub
    'Code for Quotient
    Private Sub Button4_Click(sender As Object, e As EventArgs) Handles Button4.Click
        Label3.Text = "Quotient of " + TextBox1.Text + " and " + TextBox2.Text
        TextBox3.Text = Int(TextBox1.Text) / Int(TextBox2.Text)
    End Sub
    'Code for Clear
    Private Sub Button5_Click(sender As Object, e As EventArgs) Handles Button5.Click
        TextBox1.Text = ""
        TextBox2.Text = ""
        TextBox3.Text = ""
        Label3.Text = "Answer"
    End Sub
    'Code for Exit
    Private Sub Button6_Click(sender As Object, e As EventArgs) Handles Button6.Click
        End
    End Sub
End Class`

const DEMO_FILENAME = 'Form1.vb'

const delay = (ms: number) => new Promise((r) => setTimeout(r, ms))

/* Assure-mode mock data — mirrors the design prototype's OrderProcessor demo. */

const MOCK_BASELINE_MEMBERS: BaselineMember[] = [
  { signature: 'decimal CalculateTotal(IReadOnlyList<LineItem> items)' },
  {
    signature: 'decimal ApplyDiscount(decimal subtotal, string code)',
    defect: 'returns subtotal unchanged on unknown code',
  },
  {
    signature: 'decimal SplitPerHead(decimal total, int headcount)',
    defect: 'throws DivideByZeroException when headcount = 0',
  },
  { signature: 'bool ValidateOrder(Order order)' },
]

const MOCK_OBSERVED_BEHAVIOUR: ObservedBehaviour[] = [
  {
    method: 'CalculateTotal',
    cls: 'OrderProcessor',
    rows: [
      { cond: 'normal line items', outcome: 'Σ unitPrice × qty (decimal)', kind: 'returns' },
      { cond: 'empty list', outcome: 'returns 0m', kind: 'returns' },
      {
        cond: 'item with null UnitPrice',
        outcome: 'throws NullReferenceException',
        kind: 'throws',
      },
    ],
  },
  {
    method: 'ApplyDiscount',
    cls: 'OrderProcessor',
    rows: [
      { cond: 'code "SAVE10"', outcome: 'subtotal × 0.90 (decimal)', kind: 'returns' },
      {
        cond: 'unknown code',
        outcome: 'returns subtotal unchanged — no error raised',
        kind: 'fault',
      },
      {
        cond: 'negative subtotal',
        outcome: 'returns a negative total — not guarded',
        kind: 'fault',
      },
    ],
  },
  {
    method: 'SplitPerHead',
    cls: 'OrderProcessor',
    rows: [
      { cond: 'headcount = 0', outcome: 'throws DivideByZeroException', kind: 'throws' },
      {
        cond: 'non-numeric headcount (TextBox)',
        outcome: 'throws InvalidCastException',
        kind: 'throws',
      },
    ],
  },
]

const MOCK_BASELINE_TEST_CODE = `[TestClass]
public class OrderProcessorBaselineTests
{
    // Characterises OrderProcessor.dll exactly as it runs today.
    // GREEN = behaviour unchanged. It does NOT mean correct.

    [TestMethod]
    public void ApplyDiscount_UnknownCode_ReturnsSubtotalUnchanged()
    {
        // Legacy silently ignores unknown codes — pinned as-is.
        var sut = new OrderProcessor();
        Assert.AreEqual(100m, sut.ApplyDiscount(100m, "BOGUS"));
    }

    [TestMethod]
    public void SplitPerHead_ZeroHeadcount_ThrowsDivideByZero()
    {
        var sut = new OrderProcessor();
        Assert.ThrowsException<DivideByZeroException>(
            () => sut.SplitPerHead(100m, headcount: 0));
    }

    [TestMethod]
    public void SplitPerHead_NonNumeric_ThrowsInvalidCast()
    {
        // Headcount comes straight off a TextBox — never parsed.
        var sut = new OrderProcessor();
        Assert.ThrowsException<InvalidCastException>(
            () => sut.SplitPerHead(100m, ParseQty("twelve")));
    }
}`

/* Readiness mock datasets — mirror the design prototype's four demos. */
const mr = (name: string, visibility: string, bucket: Bucket, reason: string): MethodReadiness => ({
  name,
  visibility,
  bucket,
  reason,
})

/* Web API endpoints for the mixed-portfolio demo — 9 across 5 files. Notes are kept plain. */
const MOCK_REST_APIS: RestApiEndpoint[] = [
  {
    verb: 'GET',
    route: '/api/orders',
    handler: 'OrderApiController.Get',
    source: 'Api/OrderApiController.vb',
    kind: 'Web API',
    params: [
      { name: 'status', in: 'query', type: 'string', note: 'optional — open, shipped or closed' },
      { name: 'page', in: 'query', type: 'int', note: 'which page of results (starts at 1)' },
    ],
    reqType: '—',
    req: null,
    resType: 'IEnumerable(Of OrderDto)',
    resStatus: '200 OK',
    res: '[\n  {\n    "id": 4821,\n    "customerId": 187,\n    "status": "open",\n    "total": 249.90,\n    "placedUtc": "2026-06-14T09:12:03Z"\n  }\n]',
  },
  {
    verb: 'POST',
    route: '/api/orders',
    handler: 'OrderApiController.Create',
    source: 'Api/OrderApiController.vb',
    kind: 'Web API',
    params: [],
    reqType: 'CreateOrderRequest',
    req: '{\n  "customerId": 187,\n  "lines": [\n    { "sku": "WB-114", "qty": 2 },\n    { "sku": "PK-009", "qty": 1 }\n  ],\n  "discountCode": "SPRING10"\n}',
    resType: 'OrderDto',
    resStatus: '201 Created',
    res: '{\n  "id": 4822,\n  "customerId": 187,\n  "status": "open",\n  "total": 224.91\n}',
  },
  {
    verb: 'GET',
    route: '/api/orders/{id}',
    handler: 'OrderApiController.GetById',
    source: 'Api/OrderApiController.vb',
    kind: 'Web API',
    params: [{ name: 'id', in: 'path', type: 'int', note: 'which order to fetch' }],
    reqType: '—',
    req: null,
    resType: 'OrderDto',
    resStatus: '200 OK',
    res: '{\n  "id": 4821,\n  "customerId": 187,\n  "status": "shipped",\n  "total": 249.90\n}',
  },
  {
    verb: 'PUT',
    route: '/api/orders/{id}',
    handler: 'OrderApiController.Update',
    source: 'Api/OrderApiController.vb',
    kind: 'Web API',
    params: [{ name: 'id', in: 'path', type: 'int', note: 'which order to change' }],
    reqType: 'UpdateOrderRequest',
    req: '{\n  "status": "shipped",\n  "trackingRef": "RM-8841-QK"\n}',
    resType: 'OrderDto',
    resStatus: '200 OK',
    res: '{\n  "id": 4821,\n  "status": "shipped",\n  "trackingRef": "RM-8841-QK"\n}',
  },
  {
    verb: 'GET',
    route: '/api/pricing/quote',
    handler: 'PricingController.Quote',
    source: 'Api/PricingController.vb',
    kind: 'Web API',
    params: [
      { name: 'sku', in: 'query', type: 'string', note: 'required' },
      { name: 'qty', in: 'query', type: 'int', note: 'required' },
    ],
    reqType: '—',
    req: null,
    resType: 'QuoteDto',
    resStatus: '200 OK',
    res: '{\n  "sku": "WB-114",\n  "unit": 99.95,\n  "qty": 2,\n  "margin": 0.34,\n  "total": 199.90\n}',
  },
  {
    verb: 'POST',
    route: '/api/pricing/recalc',
    handler: 'PricingController.Recalc',
    source: 'Api/PricingController.vb',
    kind: 'Web API',
    params: [],
    reqType: 'RecalcRequest',
    req: '{\n  "orderId": 4821,\n  "tier": "wholesale"\n}',
    resType: 'QuoteDto',
    resStatus: '200 OK',
    res: '{\n  "orderId": 4821,\n  "tier": "wholesale",\n  "total": 212.42\n}',
  },
  {
    verb: 'GET',
    route: '/api/customers/{id}/statement',
    handler: 'StatementController.Get',
    source: 'Api/StatementController.vb',
    kind: 'Web API',
    params: [
      { name: 'id', in: 'path', type: 'int', note: 'which customer' },
      { name: 'from', in: 'query', type: 'date', note: 'start date, like 2026-06-14' },
      { name: 'to', in: 'query', type: 'date', note: 'end date, like 2026-06-14' },
    ],
    reqType: '—',
    req: null,
    resType: 'StatementDto',
    resStatus: '200 OK',
    res: '{\n  "customerId": 187,\n  "opening": 0.00,\n  "closing": 224.91,\n  "lines": 3\n}',
  },
  {
    verb: 'DELETE',
    route: '/api/customers/{id}',
    handler: 'CustomerController.Delete',
    source: 'Api/CustomerController.vb',
    kind: 'Web API',
    params: [{ name: 'id', in: 'path', type: 'int', note: 'which customer to remove' }],
    reqType: '—',
    req: null,
    resType: '—',
    resStatus: '204 No Content',
    res: '(empty body)',
  },
  {
    verb: 'GET',
    route: '/TaxService.asmx/GetVatRate',
    handler: 'TaxService.GetVatRate',
    source: 'Services/TaxService.asmx.vb',
    kind: 'ASMX',
    params: [
      { name: 'countryCode', in: 'query', type: 'string', note: '2-letter country code, like GB' },
    ],
    reqType: 'SOAP request',
    req: '<soap:Body>\n  <GetVatRate xmlns="http://vbgone/tax">\n    <countryCode>GB</countryCode>\n  </GetVatRate>\n</soap:Body>',
    resType: 'decimal',
    resStatus: '200 OK',
    res: '<GetVatRateResult>0.20</GetVatRateResult>',
  },
]

const MOCK_READINESS: Record<
  'singleReady' | 'singleBlocked' | 'portfolioMixed' | 'portfolioBlocked',
  ReadinessReport
> = {
  singleReady: {
    sessionId: MOCK_SESSION_ID,
    confidence: 'static',
    totals: {
      classes: 1,
      methods: 4,
      netReady: 1,
      windowsGated: 0,
      refactorFirst: 0,
      methodNetReady: 4,
      methodWindowsGated: 0,
      methodRefactorFirst: 0,
    },
    classes: [
      {
        name: 'OrderProcessor',
        file: 'OrderProcessor.vb',
        bucket: 'net-ready',
        reason: 'public, no WinForms references — compiles & runs on the CLR today',
        methods: [
          mr('CalculateTotal', 'public', 'net-ready', 'params in, value out; no control access'),
          mr('ApplyDiscount', 'public', 'net-ready', 'pure switch over a string code'),
          mr(
            'SplitPerHead',
            'public',
            'net-ready',
            'arithmetic only; exception behaviour pinnable',
          ),
          mr('ValidateOrder', 'public', 'net-ready', 'reads the Order model, returns bool'),
        ],
      },
    ],
  },
  singleBlocked: {
    sessionId: MOCK_SESSION_ID,
    confidence: 'static',
    totals: {
      classes: 1,
      methods: 5,
      netReady: 0,
      windowsGated: 0,
      refactorFirst: 1,
      methodNetReady: 0,
      methodWindowsGated: 0,
      methodRefactorFirst: 5,
    },
    classes: [
      {
        name: 'Form1',
        file: 'Form1.vb',
        bucket: 'refactor-first',
        reason:
          'every handler reads TextBox.Text and writes Label.Text — logic welded into Button_Click',
        methods: [
          mr(
            'btnAdd_Click',
            'private',
            'refactor-first',
            'reads txtA.Text / txtB.Text, writes lblResult.Text',
          ),
          mr('btnEquals_Click', 'private', 'refactor-first', 'mutates controls; no return value'),
          mr(
            'btnDivide_Click',
            'private',
            'refactor-first',
            'pops MessageBox on error; reads controls',
          ),
          mr('ClearDisplay', 'private', 'refactor-first', 'writes directly to label controls'),
          mr('Form1_Load', 'private', 'refactor-first', 'wires up control state on load'),
        ],
      },
    ],
  },
  portfolioMixed: {
    sessionId: MOCK_SESSION_ID,
    confidence: 'static',
    totals: {
      classes: 142,
      methods: 1180,
      netReady: 68,
      windowsGated: 41,
      refactorFirst: 33,
      methodNetReady: 540,
      methodWindowsGated: 360,
      methodRefactorFirst: 280,
    },
    classes: [
      {
        name: 'OrderService',
        file: 'Services/OrderService.vb',
        bucket: 'net-ready',
        reason: 'public, no WinForms references',
        methods: [
          mr('PlaceOrder', 'public', 'net-ready', 'orchestrates pure domain calls'),
          mr('CalculateTotal', 'public', 'net-ready', 'params in, value out'),
          mr('ApplyDiscount', 'public', 'net-ready', 'pure switch over code'),
        ],
      },
      {
        name: 'PricingEngine',
        file: 'Domain/PricingEngine.vb',
        bucket: 'net-ready',
        reason: 'pure calculation surface; no UI types',
        methods: [
          mr('Quote', 'public', 'net-ready', 'no control access'),
          mr('Margin', 'public', 'net-ready', 'arithmetic only'),
          mr('RoundToTier', 'public', 'net-ready', 'deterministic'),
        ],
      },
      {
        name: 'TaxCalculator',
        file: 'Domain/TaxCalculator.vb',
        bucket: 'net-ready',
        reason: 'static helpers; params in, value out',
        methods: [
          mr('VatFor', 'public', 'net-ready', 'lookup + multiply'),
          mr('NetOf', 'public', 'net-ready', 'pure'),
        ],
      },
      {
        name: 'CsvExporter',
        file: 'IO/CsvExporter.vb',
        bucket: 'net-ready',
        reason: 'stream writer; no control access',
        methods: [
          mr('ExportOrders', 'public', 'net-ready', 'writes to a Stream arg'),
          mr('FormatRow', 'public', 'net-ready', 'string building'),
        ],
      },
      {
        name: 'ReportRenderer',
        file: 'Reporting/ReportRenderer.vb',
        bucket: 'windows-gated',
        reason: 'inherits Form; BuildSummary is pure but private',
        methods: [
          mr(
            'BuildSummary',
            'private',
            'windows-gated',
            'pure, but class inherits Form — needs reflection',
          ),
          mr('AggregateRows', 'private', 'windows-gated', 'pure aggregation, UI-bound class'),
          mr('Render', 'public', 'net-ready', 'tile-free helper'),
        ],
      },
      {
        name: 'LedgerView',
        file: 'Forms/LedgerView.vb',
        bucket: 'windows-gated',
        reason: 'pure posting logic trapped in a WinForms class',
        methods: [
          mr('Post', 'private', 'windows-gated', 'pure double-entry, but in a Form'),
          mr('Reconcile', 'private', 'windows-gated', 'pure, UI-bound class'),
          mr('Total', 'public', 'net-ready', 'sum helper'),
        ],
      },
      {
        name: 'StatementForm',
        file: 'Forms/StatementForm.vb',
        bucket: 'windows-gated',
        reason: 'references System.Windows.Forms; no control access in logic',
        methods: [
          mr('ComputeBalance', 'private', 'windows-gated', 'pure, Form-scoped'),
          mr('PeriodFor', 'private', 'windows-gated', 'date math, UI-bound class'),
        ],
      },
      {
        name: 'CustomerEntryForm',
        file: 'Forms/CustomerEntryForm.vb',
        bucket: 'refactor-first',
        reason: 'reads TextBox.Text and writes Label.Text in Button_Click',
        methods: [
          mr('btnSave_Click', 'private', 'refactor-first', 'reads txtName.Text; writes controls'),
          mr('Validate', 'private', 'refactor-first', 'reads control values directly'),
          mr('NormalisePhone', 'private', 'net-ready', 'pure string op'),
          mr('LoadDefaults', 'private', 'windows-gated', 'pure, but Form-scoped'),
        ],
      },
      {
        name: 'PaymentDialog',
        file: 'Forms/PaymentDialog.vb',
        bucket: 'refactor-first',
        reason: 'pops MessageBox; mutates controls directly',
        methods: [
          mr('btnPay_Click', 'private', 'refactor-first', 'MessageBox + control mutation'),
          mr('Charge', 'private', 'refactor-first', 'reads amount off a control'),
          mr('Format', 'private', 'windows-gated', 'pure, UI-bound class'),
        ],
      },
      {
        name: 'MainForm',
        file: 'MainForm.vb',
        bucket: 'refactor-first',
        reason: 'orchestrates controls; logic inseparable from event handlers',
        methods: [
          mr('btnRun_Click', 'private', 'refactor-first', 'drives the whole UI'),
          mr('RefreshGrid', 'private', 'refactor-first', 'binds DataGridView directly'),
          mr('Recompute', 'private', 'refactor-first', 'reads grid cells'),
        ],
      },
    ],
    restApis: MOCK_REST_APIS,
  },
  portfolioBlocked: {
    sessionId: MOCK_SESSION_ID,
    confidence: 'static',
    totals: {
      classes: 6,
      methods: 28,
      netReady: 0,
      windowsGated: 2,
      refactorFirst: 4,
      methodNetReady: 0,
      methodWindowsGated: 9,
      methodRefactorFirst: 19,
    },
    classes: [
      {
        name: 'CalculatorForm',
        file: 'CalculatorForm.vb',
        bucket: 'refactor-first',
        reason: 'all arithmetic lives in Button_Click reading TextBox.Text',
        methods: [
          mr(
            'btnEquals_Click',
            'private',
            'refactor-first',
            'reads display control, writes result',
          ),
          mr('btnDigit_Click', 'private', 'refactor-first', 'mutates the display directly'),
          mr('Clear', 'private', 'refactor-first', 'writes control state'),
        ],
      },
      {
        name: 'MainForm',
        file: 'MainForm.vb',
        bucket: 'refactor-first',
        reason: 'orchestrates controls; no headless path',
        methods: [
          mr('Form_Load', 'private', 'refactor-first', 'wires up controls'),
          mr('btnGo_Click', 'private', 'refactor-first', 'reads + writes controls'),
        ],
      },
      {
        name: 'SettingsForm',
        file: 'SettingsForm.vb',
        bucket: 'refactor-first',
        reason: 'binds directly to control state',
        methods: [
          mr('Save', 'private', 'refactor-first', 'reads checkbox/textbox state'),
          mr('Load', 'private', 'refactor-first', 'writes control state'),
        ],
      },
      {
        name: 'AboutDialog',
        file: 'AboutDialog.vb',
        bucket: 'refactor-first',
        reason: 'DialogResult-driven; no logic to net',
        methods: [mr('btnOk_Click', 'private', 'refactor-first', 'sets DialogResult')],
      },
      {
        name: 'PrintHelper',
        file: 'PrintHelper.vb',
        bucket: 'windows-gated',
        reason: 'pure layout maths trapped in a Form subclass',
        methods: [
          mr('Paginate', 'private', 'windows-gated', 'pure, but inherits Form'),
          mr('MeasureLine', 'private', 'windows-gated', 'pure, UI-bound class'),
        ],
      },
      {
        name: 'GridView',
        file: 'GridView.vb',
        bucket: 'windows-gated',
        reason: 'pure sort/filter logic inside a WinForms class',
        methods: [
          mr('SortBy', 'private', 'windows-gated', 'pure comparator, Form-scoped'),
          mr('FilterRows', 'private', 'windows-gated', 'pure predicate, UI-bound'),
        ],
      },
    ],
  },
}

const mockGreenNet = (sessionId: string, className: string, code: string): BaselineTestsResult => {
  const total = 43
  return {
    sessionId,
    className,
    testClassName: `${className}BaselineTests`,
    code,
    testCount: total,
    netFaithful: true,
    build: {
      sessionId,
      buildStatus: 'GREEN',
      total,
      passed: total,
      failed: 0,
      errors: [],
      failedTests: [],
      coveragePercent: 87.5,
      branchCoveragePercent: 79.0,
    },
    failures: [],
  }
}

/* ── Mock API calls ── */

// Track state across mock calls so build/PR can return consistent data
let lastMockTestCount = 30
const mockMigratedClasses: string[] = []

const mockApi = {
  async analyse(filename: string, content: string, engine?: EngineParams): Promise<AnalysisResult> {
    void content
    void engine
    await delay(1200)

    // Complex demo — return multi-class God class decomposition
    if (filename === DEMO_COMPLEX_FILENAME) {
      return {
        sessionId: MOCK_SESSION_ID,
        classes: [
          {
            name: 'OrderCalculationService',
            methods: ['CalculateDiscount', 'CalculateShipping', 'CalculateTotal'],
            dependencies: [],
            complexity: 'MEDIUM',
            codeQuality: 'FAIR' as const,
            codeSmells: [
              'Magic numbers — hardcoded tax rates, discount thresholds, shipping costs',
            ],
            refactoringSuggestions: [
              'Extract discount thresholds and shipping tiers into configuration constants',
            ],
            vbAntiPatterns: ['Deep nesting — 5 levels of nested If statements'],
          },
          {
            name: 'OrderValidator',
            methods: ['ValidateOrder', 'GetDiscountTier'],
            dependencies: [],
            complexity: 'LOW',
            codeQuality: 'FAIR' as const,
            codeSmells: ['GoTo statements used for flow control'],
            refactoringSuggestions: ['Replace GoTo with early returns or switch expression'],
            vbAntiPatterns: ['GoTo statements'],
          },
          {
            name: 'RefundService',
            methods: ['ProcessRefund'],
            dependencies: ['OrderCalculationService'],
            complexity: 'HIGH',
            codeQuality: 'POOR' as const,
            codeSmells: [
              'Mixed concerns — business logic, database access, email sending, file I/O',
              'SQL injection — string concatenation for SQL queries',
              'On Error Resume Next — silently swallows all exceptions',
            ],
            refactoringSuggestions: [
              'Accept IOrderRepository and INotificationService via constructor injection',
              'Remove MsgBox calls — return result object instead',
            ],
            vbAntiPatterns: ['On Error Resume Next', 'SQL injection via string concatenation'],
          },
          {
            name: 'OrderProcessor',
            methods: ['SubmitOrder'],
            dependencies: ['OrderCalculationService', 'OrderValidator', 'RefundService'],
            complexity: 'HIGH',
            codeQuality: 'POOR' as const,
            codeSmells: [
              'God class — too many responsibilities in a single file',
              'Copy-paste duplication — discount and shipping logic duplicated',
              'Hardcoded connection strings and file paths',
            ],
            refactoringSuggestions: [
              'Orchestration only — delegate to extracted services',
              'Accept dependencies via constructor injection',
            ],
            vbAntiPatterns: [
              'On Error Resume Next',
              'SQL injection via string concatenation',
              'MsgBox for user feedback in business logic',
            ],
            // Assure mode renders this; Migrate ignores it.
            observedBehaviour: MOCK_OBSERVED_BEHAVIOUR,
          },
        ],
        suggestedMigrationOrder: [
          'OrderCalculationService',
          'OrderValidator',
          'RefundService',
          'OrderProcessor',
        ],
        summary:
          'God class decomposed into 4 classes. OrderCalculationService and OrderValidator are leaf nodes with no dependencies. RefundService depends on OrderCalculationService. OrderProcessor orchestrates all three. Recommended migration order starts with the two independent services.',
      }
    }

    return {
      sessionId: MOCK_SESSION_ID,
      classes: [
        {
          name: 'Form1',
          methods: [
            'Button1_Click',
            'Button2_Click',
            'Button3_Click',
            'Button4_Click',
            'Button5_Click',
            'Button6_Click',
          ],
          dependencies: [],
          complexity: 'LOW',
          codeQuality: 'FAIR' as const,
          codeSmells: ['Mixed concerns — UI logic mixed with business logic'],
          refactoringSuggestions: ['Extract arithmetic operations into a separate service class'],
          vbAntiPatterns: ['Implicit type conversions via Int()'],
        },
      ],
      suggestedMigrationOrder: ['Form1'],
      summary:
        'One WinForms class found with 6 event handlers — sum, difference, product, quotient, clear, exit. No dependencies. Good candidate for migration.',
    }
  },

  async generateInterface(
    sessionId: string,
    className: string,
    engine?: EngineParams,
  ): Promise<InterfaceResult> {
    void sessionId
    void engine
    await delay(800)
    if (!mockMigratedClasses.includes(className)) {
      mockMigratedClasses.push(className)
    }

    const interfaceCode: Record<string, string> = {
      OrderCalculationService: `namespace VBGone.Generated;

public interface IOrderCalculationService
{
    double CalculateDiscount(double unitPrice, int quantity);
    double CalculateShipping(int quantity);
    double CalculateTotal(double unitPrice, int quantity);
}`,
      OrderValidator: `namespace VBGone.Generated;

public interface IOrderValidator
{
    string ValidateOrder(string name, string amount, string quantity);
    string GetDiscountTier(double subtotal);
}`,
      RefundService: `namespace VBGone.Generated;

public interface IRefundService
{
    bool ProcessRefund(int orderId, string reason);
}`,
      OrderProcessor: `namespace VBGone.Generated;

public interface IOrderProcessor
{
    void SubmitOrder(string customerName, double unitPrice, int quantity);
}`,
    }

    return {
      sessionId: MOCK_SESSION_ID,
      className,
      interfaceName: `I${className}`,
      code:
        interfaceCode[className] ??
        `namespace VBGone.Generated;\n\npublic interface I${className}\n{\n    int Add(int a, int b);\n    int Subtract(int a, int b);\n    int Multiply(int a, int b);\n    double Divide(int a, int b);\n    int Modulus(int a, int b);\n}`,
    }
  },

  async generateTests(
    sessionId: string,
    className: string,
    engine?: EngineParams,
  ): Promise<TestsResult> {
    void sessionId
    void engine
    await delay(1000)

    const testCode: Record<string, { code: string; count: number }> = {
      OrderCalculationService: {
        count: 18,
        code: `using NUnit.Framework;

namespace VBGone.Generated.Tests;

[TestFixture]
public class OrderCalculationServiceTests
{
    private IOrderCalculationService _sut;

    [SetUp]
    public void SetUp()
    {
        _sut = new OrderCalculationService();
    }

    // ── CalculateDiscount ──

    [TestCase(10.0, 5, ExpectedResult = 0.0)]
    [TestCase(20.0, 6, ExpectedResult = 0.10)]
    [TestCase(50.0, 11, ExpectedResult = 0.15)]
    [TestCase(100.0, 11, ExpectedResult = 0.20)]
    public double CalculateDiscount_ReturnsCorrectTier(double unitPrice, int quantity)
    {
        return _sut.CalculateDiscount(unitPrice, quantity);
    }

    [Test]
    public void CalculateDiscount_BoundaryAt100_ReturnsZero()
    {
        Assert.That(_sut.CalculateDiscount(10.0, 10), Is.EqualTo(0.0));
    }

    [Test]
    public void CalculateDiscount_JustOver100_ReturnsTier1()
    {
        Assert.That(_sut.CalculateDiscount(10.1, 10), Is.EqualTo(0.10));
    }

    [Test]
    public void CalculateDiscount_ZeroQuantity_ReturnsZero()
    {
        Assert.That(_sut.CalculateDiscount(50.0, 0), Is.EqualTo(0.0));
    }

    [Test]
    public void CalculateDiscount_NegativePrice_ReturnsZero()
    {
        Assert.That(_sut.CalculateDiscount(-10.0, 5), Is.EqualTo(0.0));
    }

    // ── CalculateShipping ──

    [TestCase(1, ExpectedResult = 5.99)]
    [TestCase(5, ExpectedResult = 5.99)]
    [TestCase(6, ExpectedResult = 9.99)]
    [TestCase(20, ExpectedResult = 9.99)]
    [TestCase(21, ExpectedResult = 14.99)]
    [TestCase(100, ExpectedResult = 14.99)]
    public double CalculateShipping_ReturnsCorrectTier(int quantity)
    {
        return _sut.CalculateShipping(quantity);
    }

    [Test]
    public void CalculateShipping_ZeroQuantity_ReturnsZero()
    {
        Assert.That(_sut.CalculateShipping(0), Is.EqualTo(0.0));
    }

    // ── CalculateTotal ──

    [Test]
    public void CalculateTotal_SmallOrder_IncludesTaxAndShipping()
    {
        var total = _sut.CalculateTotal(10.0, 2);
        Assert.That(total, Is.GreaterThan(20.0));
    }

    [Test]
    public void CalculateTotal_LargeOrder_AppliesDiscount()
    {
        var noDiscount = _sut.CalculateTotal(10.0, 5);
        var withDiscount = _sut.CalculateTotal(20.0, 6);
        Assert.That(withDiscount, Is.LessThan(20.0 * 6 * 1.1));
    }

    [Test]
    public void CalculateTotal_ZeroQuantity_ReturnsZero()
    {
        Assert.That(_sut.CalculateTotal(10.0, 0), Is.EqualTo(0.0));
    }
}`,
      },
      OrderValidator: {
        count: 12,
        code: `using NUnit.Framework;

namespace VBGone.Generated.Tests;

[TestFixture]
public class OrderValidatorTests
{
    private IOrderValidator _sut;

    [SetUp]
    public void SetUp()
    {
        _sut = new OrderValidator();
    }

    // ── ValidateOrder ──

    [Test]
    public void ValidateOrder_AllValid_ReturnsEmptyString()
    {
        Assert.That(_sut.ValidateOrder("Alice", "19.99", "3"), Is.EqualTo(""));
    }

    [Test]
    public void ValidateOrder_EmptyName_ReturnsNameRequired()
    {
        Assert.That(_sut.ValidateOrder("", "10", "1"), Does.Contain("Name required"));
    }

    [Test]
    public void ValidateOrder_NonNumericAmount_ReturnsAmountError()
    {
        Assert.That(_sut.ValidateOrder("Alice", "abc", "1"), Does.Contain("Amount must be numeric"));
    }

    [Test]
    public void ValidateOrder_NegativeAmount_ReturnsPositiveError()
    {
        Assert.That(_sut.ValidateOrder("Alice", "-5", "1"), Does.Contain("Amount must be positive"));
    }

    [Test]
    public void ValidateOrder_ZeroAmount_ReturnsPositiveError()
    {
        Assert.That(_sut.ValidateOrder("Alice", "0", "1"), Does.Contain("Amount must be positive"));
    }

    [Test]
    public void ValidateOrder_NonNumericQuantity_ReturnsQuantityError()
    {
        Assert.That(_sut.ValidateOrder("Alice", "10", "xyz"), Does.Contain("Quantity must be numeric"));
    }

    [Test]
    public void ValidateOrder_ZeroQuantity_ReturnsPositiveError()
    {
        Assert.That(_sut.ValidateOrder("Alice", "10", "0"), Does.Contain("Quantity must be positive"));
    }

    [Test]
    public void ValidateOrder_AllInvalid_ReturnsMultipleErrors()
    {
        var result = _sut.ValidateOrder("", "abc", "xyz");
        Assert.That(result, Does.Contain("Name required"));
        Assert.That(result, Does.Contain("Amount must be numeric"));
        Assert.That(result, Does.Contain("Quantity must be numeric"));
    }

    // ── GetDiscountTier ──

    [TestCase(0, ExpectedResult = "NONE")]
    [TestCase(100, ExpectedResult = "NONE")]
    [TestCase(100.01, ExpectedResult = "BRONZE")]
    [TestCase(500, ExpectedResult = "BRONZE")]
    [TestCase(500.01, ExpectedResult = "SILVER")]
    [TestCase(1000, ExpectedResult = "SILVER")]
    [TestCase(1000.01, ExpectedResult = "GOLD")]
    [TestCase(5000, ExpectedResult = "GOLD")]
    public string GetDiscountTier_ReturnsCorrectTier(double subtotal)
    {
        return _sut.GetDiscountTier(subtotal);
    }

    [Test]
    public void GetDiscountTier_NegativeSubtotal_ReturnsNone()
    {
        Assert.That(_sut.GetDiscountTier(-50), Is.EqualTo("NONE"));
    }
}`,
      },
      RefundService: {
        count: 8,
        code: `using NUnit.Framework;

namespace VBGone.Generated.Tests;

[TestFixture]
public class RefundServiceTests
{
    private IRefundService _sut;

    [SetUp]
    public void SetUp()
    {
        _sut = new RefundService();
    }

    [Test]
    public void ProcessRefund_ValidOrderAndReason_ReturnsTrue()
    {
        Assert.That(_sut.ProcessRefund(1001, "Damaged goods"), Is.True);
    }

    [Test]
    public void ProcessRefund_ZeroOrderId_ReturnsFalse()
    {
        Assert.That(_sut.ProcessRefund(0, "Damaged goods"), Is.False);
    }

    [Test]
    public void ProcessRefund_NegativeOrderId_ReturnsFalse()
    {
        Assert.That(_sut.ProcessRefund(-1, "Damaged goods"), Is.False);
    }

    [Test]
    public void ProcessRefund_EmptyReason_ReturnsFalse()
    {
        Assert.That(_sut.ProcessRefund(1001, ""), Is.False);
    }

    [Test]
    public void ProcessRefund_NullReason_ReturnsFalse()
    {
        Assert.That(_sut.ProcessRefund(1001, null!), Is.False);
    }

    [Test]
    public void ProcessRefund_LargeOrderId_ReturnsTrue()
    {
        Assert.That(_sut.ProcessRefund(999999, "Customer request"), Is.True);
    }

    [Test]
    public void ProcessRefund_WhitespaceReason_ReturnsFalse()
    {
        Assert.That(_sut.ProcessRefund(1001, "   "), Is.False);
    }

    [Test]
    public void ProcessRefund_SpecialCharactersInReason_ReturnsTrue()
    {
        Assert.That(_sut.ProcessRefund(1001, "Reason with 'quotes' & <symbols>"), Is.True);
    }
}`,
      },
      OrderProcessor: {
        count: 6,
        code: `using NUnit.Framework;

namespace VBGone.Generated.Tests;

[TestFixture]
public class OrderProcessorTests
{
    private IOrderProcessor _sut;

    [SetUp]
    public void SetUp()
    {
        _sut = new OrderProcessor(
            new OrderCalculationService(),
            new OrderValidator()
        );
    }

    [Test]
    public void SubmitOrder_ValidInput_DoesNotThrow()
    {
        Assert.DoesNotThrow(() => _sut.SubmitOrder("Alice", 19.99, 3));
    }

    [Test]
    public void SubmitOrder_EmptyName_ThrowsArgumentException()
    {
        Assert.Throws<ArgumentException>(() => _sut.SubmitOrder("", 19.99, 3));
    }

    [Test]
    public void SubmitOrder_NegativePrice_ThrowsArgumentException()
    {
        Assert.Throws<ArgumentException>(() => _sut.SubmitOrder("Alice", -10, 3));
    }

    [Test]
    public void SubmitOrder_ZeroQuantity_ThrowsArgumentException()
    {
        Assert.Throws<ArgumentException>(() => _sut.SubmitOrder("Alice", 19.99, 0));
    }

    [Test]
    public void SubmitOrder_LargeOrder_DoesNotThrow()
    {
        Assert.DoesNotThrow(() => _sut.SubmitOrder("Bob", 500.0, 50));
    }

    [Test]
    public void SubmitOrder_MinimumValidInput_DoesNotThrow()
    {
        Assert.DoesNotThrow(() => _sut.SubmitOrder("X", 0.01, 1));
    }
}`,
      },
    }

    const defaultCode = `using NUnit.Framework;

namespace VBGone.Generated.Tests;

[TestFixture]
public class ${className}Tests
{
    private I${className} _sut;

    [SetUp]
    public void SetUp()
    {
        _sut = new ${className}();
    }

    [TestCase(2, 3, ExpectedResult = 5)]
    [TestCase(-1, 1, ExpectedResult = 0)]
    [TestCase(0, 0, ExpectedResult = 0)]
    public int Add_ReturnsCorrectSum(int a, int b)
    {
        return _sut.Add(a, b);
    }

    [TestCase(5, 3, ExpectedResult = 2)]
    [TestCase(0, 5, ExpectedResult = -5)]
    public int Subtract_ReturnsCorrectDifference(int a, int b)
    {
        return _sut.Subtract(a, b);
    }

    [TestCase(3, 4, ExpectedResult = 12)]
    [TestCase(0, 5, ExpectedResult = 0)]
    public int Multiply_ReturnsCorrectProduct(int a, int b)
    {
        return _sut.Multiply(a, b);
    }

    [TestCase(10, 2, ExpectedResult = 5.0)]
    [TestCase(7, 2, ExpectedResult = 3.5)]
    public double Divide_ReturnsCorrectQuotient(int a, int b)
    {
        return _sut.Divide(a, b);
    }

    [Test]
    public void Divide_ByZero_ThrowsDivideByZeroException()
    {
        Assert.Throws<DivideByZeroException>(() => _sut.Divide(1, 0));
    }

    [TestCase(10, 3, ExpectedResult = 1)]
    [TestCase(9, 3, ExpectedResult = 0)]
    public int Modulus_ReturnsCorrectRemainder(int a, int b)
    {
        return _sut.Modulus(a, b);
    }
}`

    const match = testCode[className]
    const count = match?.count ?? 30
    lastMockTestCount = count

    return {
      sessionId: MOCK_SESSION_ID,
      className,
      testClassName: `${className}Tests`,
      code: match?.code ?? defaultCode,
      testCount: count,
    }
  },

  async generateStub(
    sessionId: string,
    className: string,
    engine?: EngineParams,
  ): Promise<StubResult> {
    void sessionId
    void engine
    await delay(600)

    const stubCode: Record<string, string> = {
      OrderCalculationService: `namespace VBGone.Generated;

public class OrderCalculationService : IOrderCalculationService
{
    public double CalculateDiscount(double unitPrice, int quantity) => throw new NotImplementedException();
    public double CalculateShipping(int quantity) => throw new NotImplementedException();
    public double CalculateTotal(double unitPrice, int quantity) => throw new NotImplementedException();
}`,
      OrderValidator: `namespace VBGone.Generated;

public class OrderValidator : IOrderValidator
{
    public string ValidateOrder(string name, string amount, string quantity) => throw new NotImplementedException();
    public string GetDiscountTier(double subtotal) => throw new NotImplementedException();
}`,
      RefundService: `namespace VBGone.Generated;

public class RefundService : IRefundService
{
    public bool ProcessRefund(int orderId, string reason) => throw new NotImplementedException();
}`,
      OrderProcessor: `namespace VBGone.Generated;

public class OrderProcessor : IOrderProcessor
{
    public void SubmitOrder(string customerName, double unitPrice, int quantity) => throw new NotImplementedException();
}`,
    }

    return {
      sessionId: MOCK_SESSION_ID,
      className,
      code:
        stubCode[className] ??
        `namespace VBGone.Generated;\n\npublic class ${className} : I${className}\n{\n    public int Add(int a, int b) => throw new NotImplementedException();\n    public int Subtract(int a, int b) => throw new NotImplementedException();\n    public int Multiply(int a, int b) => throw new NotImplementedException();\n    public double Divide(int a, int b) => throw new NotImplementedException();\n    public int Modulus(int a, int b) => throw new NotImplementedException();\n}`,
    }
  },

  async build(sessionId: string): Promise<BuildResult> {
    void sessionId
    await delay(1500)
    const total = lastMockTestCount
    return {
      sessionId: MOCK_SESSION_ID,
      buildStatus: 'RED',
      total,
      passed: 0,
      failed: total,
      errors: [],
      failedTests: Array.from({ length: total }, (_, i) => `Test_${i + 1}`),
      // RED stub build: nothing is implemented yet, so coverage isn't meaningful/surfaced.
      coveragePercent: null,
      branchCoveragePercent: null,
    }
  },

  async implement(
    sessionId: string,
    className: string,
    mode: 'STUB' | 'CLAUDE',
    engine?: EngineParams,
  ): Promise<ImplementResult> {
    void sessionId
    void engine
    await delay(mode === 'CLAUDE' ? 2000 : 400)

    const implCode: Record<string, string> = {
      OrderCalculationService: `namespace VBGone.Generated;

public class OrderCalculationService : IOrderCalculationService
{
    private const double TaxRate = 0.0825;
    private const double Tier1Discount = 0.10;
    private const double Tier2Discount = 0.15;
    private const double Tier3Discount = 0.20;

    public double CalculateDiscount(double unitPrice, int quantity)
    {
        var subtotal = unitPrice * quantity;
        return subtotal switch
        {
            > 1000 => Tier3Discount,
            > 500 => Tier2Discount,
            > 100 => Tier1Discount,
            _ => 0
        };
    }

    public double CalculateShipping(int quantity) => quantity switch
    {
        <= 0 => 0,
        <= 5 => 5.99,
        <= 20 => 9.99,
        _ => 14.99
    };

    public double CalculateTotal(double unitPrice, int quantity)
    {
        var subtotal = unitPrice * quantity;
        var discount = CalculateDiscount(unitPrice, quantity);
        var shipping = CalculateShipping(quantity);
        var afterDiscount = subtotal - (subtotal * discount) + shipping;
        return afterDiscount + (afterDiscount * TaxRate);
    }
}`,
      OrderValidator: `namespace VBGone.Generated;

public class OrderValidator : IOrderValidator
{
    public string ValidateOrder(string name, string amount, string quantity)
    {
        var errors = "";
        if (string.IsNullOrEmpty(name)) errors += "Name required. ";
        if (!double.TryParse(amount, out var a)) errors += "Amount must be numeric. ";
        else if (a <= 0) errors += "Amount must be positive. ";
        if (!int.TryParse(quantity, out var q)) errors += "Quantity must be numeric. ";
        else if (q <= 0) errors += "Quantity must be positive. ";
        return errors;
    }

    public string GetDiscountTier(double subtotal) => subtotal switch
    {
        <= 100 => "NONE",
        <= 500 => "BRONZE",
        <= 1000 => "SILVER",
        _ => "GOLD"
    };
}`,
      RefundService: `namespace VBGone.Generated;

public class RefundService : IRefundService
{
    private const int MaxRefunds = 3;

    public bool ProcessRefund(int orderId, string reason)
    {
        if (orderId <= 0 || string.IsNullOrEmpty(reason)) return false;
        // Delegates to IOrderRepository and INotificationService
        return true;
    }
}`,
      OrderProcessor: `namespace VBGone.Generated;

public class OrderProcessor : IOrderProcessor
{
    private readonly IOrderCalculationService _calc;
    private readonly IOrderValidator _validator;

    public OrderProcessor(IOrderCalculationService calc, IOrderValidator validator)
    {
        _calc = calc;
        _validator = validator;
    }

    public void SubmitOrder(string customerName, double unitPrice, int quantity)
    {
        var errors = _validator.ValidateOrder(customerName, unitPrice.ToString(), quantity.ToString());
        if (!string.IsNullOrEmpty(errors)) throw new ArgumentException(errors);
        var total = _calc.CalculateTotal(unitPrice, quantity);
        // Persist order via IOrderRepository
    }
}`,
    }

    const code =
      mode === 'CLAUDE'
        ? (implCode[className] ??
          `namespace VBGone.Generated;\n\npublic class ${className} : I${className}\n{\n    public int Add(int a, int b) => a + b;\n    public int Subtract(int a, int b) => a - b;\n    public int Multiply(int a, int b) => a * b;\n    public double Divide(int a, int b)\n    {\n        if (b == 0) throw new DivideByZeroException();\n        return (double)a / b;\n    }\n    public int Modulus(int a, int b) => a % b;\n}`)
        : `namespace VBGone.Generated;\n\npublic class ${className} : I${className}\n{\n    public int Add(int a, int b) => throw new NotImplementedException();\n    public int Subtract(int a, int b) => throw new NotImplementedException();\n    public int Multiply(int a, int b) => throw new NotImplementedException();\n    public double Divide(int a, int b) => throw new NotImplementedException();\n    public int Modulus(int a, int b) => throw new NotImplementedException();\n}`
    return { sessionId: MOCK_SESSION_ID, className, code, mode }
  },

  async buildAfterImplement(sessionId: string, mode: 'STUB' | 'CLAUDE'): Promise<BuildResult> {
    void sessionId
    await delay(1500)
    const total = lastMockTestCount
    return {
      sessionId: MOCK_SESSION_ID,
      buildStatus: mode === 'CLAUDE' ? 'GREEN' : 'RED',
      total,
      passed: mode === 'CLAUDE' ? total : 0,
      failed: mode === 'CLAUDE' ? 0 : total,
      errors: [],
      failedTests:
        mode === 'CLAUDE' ? [] : Array.from({ length: total }, (_, i) => `Test_${i + 1}`),
      coveragePercent: mode === 'CLAUDE' ? 88.2 : null,
      branchCoveragePercent: mode === 'CLAUDE' ? 81.5 : null,
    }
  },

  async retryImplement(
    sessionId: string,
    className: string,
    failingTests: string[],
    attempt?: number,
    engine?: EngineParams,
  ): Promise<ImplementResult> {
    void sessionId
    void failingTests
    void attempt
    void engine
    await delay(2000)
    return {
      sessionId: MOCK_SESSION_ID,
      className,
      code: `namespace VBGone.Generated;\n\npublic class ${className} : I${className}\n{\n    // retry implementation\n}`,
      mode: 'CLAUDE',
    }
  },

  async raisePR(
    sessionId: string,
    repoOwner: string,
    repoName: string,
    branchName: string,
  ): Promise<PullRequestResult> {
    void sessionId
    await delay(1000)
    const classes = mockMigratedClasses.length > 0 ? mockMigratedClasses : ['Form1']
    const filesCommitted = classes.flatMap((cls) => [
      `${cls}/I${cls}.cs`,
      `${cls}/${cls}.cs`,
      `${cls}.Tests/${cls}Tests.cs`,
    ])
    return {
      sessionId: MOCK_SESSION_ID,
      prUrl: `https://github.com/${repoOwner}/${repoName}/pull/1`,
      branchName,
      filesCommitted,
    }
  },

  async uploadProject(file: File): Promise<ProjectAnalysis> {
    void file
    await delay(2000)
    return {
      sessionId: MOCK_SESSION_ID,
      classes: [
        {
          name: 'ValidationHelper',
          methods: ['IsNullOrEmpty', 'IsValidEmail', 'IsInRange'],
          dependencies: [],
          complexity: 'LOW',
        },
        {
          name: 'StringHelper',
          methods: ['Capitalize', 'TruncateWithEllipsis', 'RemoveWhitespace', 'CountWords'],
          dependencies: [],
          complexity: 'LOW',
        },
        {
          name: 'DateHelper',
          methods: ['IsWeekday', 'GetBusinessDaysBetween', 'FormatFriendly'],
          dependencies: ['ValidationHelper'],
          complexity: 'MEDIUM',
        },
        {
          name: 'Calculator',
          methods: ['Add', 'Subtract', 'Multiply', 'Divide', 'Power', 'CalculateCompound'],
          dependencies: ['StringHelper', 'DateHelper'],
          complexity: 'HIGH',
        },
      ],
      suggestedMigrationOrder: ['ValidationHelper', 'StringHelper', 'DateHelper', 'Calculator'],
      dependencyGraph: {
        ValidationHelper: [],
        StringHelper: [],
        DateHelper: ['ValidationHelper'],
        Calculator: ['StringHelper', 'DateHelper'],
      },
      summary:
        'Four classes found across the project. ValidationHelper and StringHelper are leaf nodes with no dependencies. DateHelper depends on ValidationHelper. Calculator depends on StringHelper and DateHelper. Recommended migration order starts with the two independent helpers.',
    }
  },

  async generateBaseline(
    sessionId: string,
    className: string,
    engine?: EngineParams,
  ): Promise<BaselineResult> {
    void engine
    await delay(900)
    return {
      sessionId,
      className,
      surfaceFile: `${className}.dll · public surface`,
      members: MOCK_BASELINE_MEMBERS,
    }
  },

  async runBaselineTests(
    sessionId: string,
    className: string,
    engine?: EngineParams,
  ): Promise<BaselineTestsResult> {
    void engine
    await delay(1400)
    return mockGreenNet(sessionId, className, MOCK_BASELINE_TEST_CODE)
  },

  async rerunBaselineTests(
    sessionId: string,
    className: string,
    code: string,
  ): Promise<BaselineTestsResult> {
    // Demo: a corrected-and-re-run net goes green.
    await delay(1200)
    return mockGreenNet(sessionId, className, code)
  },

  async repairBaselineTest(
    sessionId: string,
    className: string,
    code: string,
    failingTest: string,
    tier: number,
    engine?: EngineParams,
  ): Promise<RepairAttemptResult> {
    void sessionId
    void className
    void engine
    await delay(1100)
    // Demo: a single deterministic-swap tier makes the baseline green again.
    const role = tier === 1 ? 'mechanical' : tier === 3 ? 'escalation' : 'reasoning'
    return {
      tier: tier === 1 ? 'Mechanical' : tier === 3 ? 'Escalation' : 'Reasoning',
      role,
      model: `mock-${role}`,
      rationale:
        'The observed return is 13 — VB.NET CInt banker-rounds 9.9 to 10, so the “truncates” premise was wrong. Correcting the expected value and the misleading name.',
      diff: [
        { op: '-', text: 'Assert.AreEqual(12, result);   // 9.9 truncates to 9' },
        { op: '+', text: 'Assert.AreEqual(13, result);   // CInt rounds 9.9 -> 10' },
      ],
      gate: {
        ok: true,
        note: `Still calls ${failingTest.split('_')[0]} and still checks the return value. Not a meaningless always-pass test.`,
      },
      rerun: { green: true, note: '23 / 23 passing against your untouched VB.NET.' },
      tag: 'green',
      code: code.replace('Assert.AreEqual(12', 'Assert.AreEqual(13'),
      netFaithful: true,
    }
  },

  async assess(filename: string, content: string): Promise<ReadinessReport> {
    void content
    await delay(700) // a fast static scan
    if (filename === 'Form1.vb') return MOCK_READINESS.singleBlocked
    if (filename === 'LegacyEstate.zip') return MOCK_READINESS.portfolioMixed
    if (filename === 'WinFormsApp.zip') return MOCK_READINESS.portfolioBlocked
    return MOCK_READINESS.singleReady
  },

  async assessProject(file: File): Promise<ReadinessReport> {
    await delay(700)
    return file.name.toLowerCase().includes('winforms')
      ? MOCK_READINESS.portfolioBlocked
      : MOCK_READINESS.portfolioMixed
  },

  async fetchCost(sessionId: string): Promise<CostResult> {
    void sessionId
    return { sessionId: MOCK_SESSION_ID, steps: [], totalCost: 0 }
  },
}

/* ── Real API calls ── */

const realApi = {
  async analyse(filename: string, content: string, engine?: EngineParams): Promise<AnalysisResult> {
    const { data } = await api.post<AnalysisResult>('/analyse', { filename, content, ...engine })
    return data
  },

  async generateInterface(
    sessionId: string,
    className: string,
    engine?: EngineParams,
  ): Promise<InterfaceResult> {
    const { data } = await api.post<InterfaceResult>('/interface', {
      sessionId,
      className,
      ...engine,
    })
    return data
  },

  async generateTests(
    sessionId: string,
    className: string,
    engine?: EngineParams,
  ): Promise<TestsResult> {
    const { data } = await api.post<TestsResult>('/tests', { sessionId, className, ...engine })
    return data
  },

  async generateStub(
    sessionId: string,
    className: string,
    engine?: EngineParams,
  ): Promise<StubResult> {
    const { data } = await api.post<StubResult>('/stub', { sessionId, className, ...engine })
    return data
  },

  async build(sessionId: string): Promise<BuildResult> {
    const { data } = await api.post<BuildResult>('/build', { sessionId })
    return data
  },

  async implement(
    sessionId: string,
    className: string,
    mode: 'STUB' | 'CLAUDE',
    engine?: EngineParams,
  ): Promise<ImplementResult> {
    const { data } = await api.post<ImplementResult>('/implement', {
      sessionId,
      className,
      mode,
      ...engine,
    })
    return data
  },

  async buildAfterImplement(sessionId: string, mode: 'STUB' | 'CLAUDE'): Promise<BuildResult> {
    void mode
    const { data } = await api.post<BuildResult>('/build', { sessionId })
    return data
  },

  async retryImplement(
    sessionId: string,
    className: string,
    failingTests: string[],
    attempt?: number,
    engine?: EngineParams,
  ): Promise<ImplementResult> {
    const { data } = await api.post<ImplementResult>('/retry-implement', {
      sessionId,
      className,
      failingTests,
      attempt: attempt ?? 1,
      ...engine,
    })
    return data
  },

  async raisePR(
    sessionId: string,
    repoOwner: string,
    repoName: string,
    branchName: string,
  ): Promise<PullRequestResult> {
    const { data } = await api.post<PullRequestResult>('/pr', {
      sessionId,
      repoOwner,
      repoName,
      branchName,
    })
    return data
  },

  async uploadProject(file: File): Promise<ProjectAnalysis> {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await api.post<ProjectAnalysis>('/upload-project', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  },

  async generateBaseline(
    sessionId: string,
    className: string,
    engine?: EngineParams,
  ): Promise<BaselineResult> {
    const { data } = await assureApi.post<BaselineResult>('/baseline', {
      sessionId,
      className,
      ...engine,
    })
    return data
  },

  async runBaselineTests(
    sessionId: string,
    className: string,
    engine?: EngineParams,
  ): Promise<BaselineTestsResult> {
    const { data } = await assureApi.post<BaselineTestsResult>('/baseline-tests', {
      sessionId,
      className,
      ...engine,
    })
    return data
  },

  async rerunBaselineTests(
    sessionId: string,
    className: string,
    code: string,
  ): Promise<BaselineTestsResult> {
    const { data } = await assureApi.post<BaselineTestsResult>('/rerun-baseline-tests', {
      sessionId,
      className,
      code,
    })
    return data
  },

  async repairBaselineTest(
    sessionId: string,
    className: string,
    code: string,
    failingTest: string,
    tier: number,
    engine?: EngineParams,
  ): Promise<RepairAttemptResult> {
    const { data } = await assureApi.post<RepairAttemptResult>('/repair', {
      sessionId,
      className,
      code,
      failingTest,
      tier,
      ...engine,
    })
    return data
  },

  async assess(filename: string, content: string): Promise<ReadinessReport> {
    const { data } = await assureApi.post<ReadinessReport>('/assess', { filename, content })
    return data
  },

  async assessProject(file: File): Promise<ReadinessReport> {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await assureApi.post<ReadinessReport>('/assess-project', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  },

  async fetchCost(sessionId: string): Promise<CostResult> {
    const { data } = await api.get<CostResult>(`/cost/${sessionId}`)
    return data
  },
}

/* ── Export the active implementation ── */

const active = USE_MOCKS ? mockApi : realApi

export const analyse = active.analyse
export const generateInterface = active.generateInterface
export const generateTests = active.generateTests
export const generateStub = active.generateStub
export const build = active.build
export const implement = active.implement
export const buildAfterImplement = active.buildAfterImplement
export const retryImplement = active.retryImplement
export const raisePR = active.raisePR
export const uploadProject = active.uploadProject
export const generateBaseline = active.generateBaseline
export const runBaselineTests = active.runBaselineTests
export const rerunBaselineTests = active.rerunBaselineTests
export const repairBaselineTest = active.repairBaselineTest
export const assess = active.assess
export const assessProject = active.assessProject
export const fetchCost = active.fetchCost

/* Export the axios instance for when we wire to real backend */
export { api }

export const DEMO_PROJECT_FILES: { path: string; content: string }[] = [
  {
    path: 'ValidationHelper.vb',
    content: `Public Class ValidationHelper
    Public Function IsNullOrEmpty(value As String) As Boolean
        Return String.IsNullOrEmpty(value)
    End Function

    Public Function IsValidEmail(email As String) As Boolean
        If IsNullOrEmpty(email) Then Return False
        Return email.Contains("@") AndAlso email.Contains(".")
    End Function

    Public Function IsInRange(value As Integer, min As Integer, max As Integer) As Boolean
        Return value >= min AndAlso value <= max
    End Function
End Class`,
  },
  {
    path: 'StringHelper.vb',
    content: `Public Class StringHelper
    Public Function Capitalize(input As String) As String
        If String.IsNullOrEmpty(input) Then Return input
        Return input.Substring(0, 1).ToUpper() & input.Substring(1)
    End Function

    Public Function TruncateWithEllipsis(input As String, maxLength As Integer) As String
        If input.Length <= maxLength Then Return input
        Return input.Substring(0, maxLength) & "..."
    End Function

    Public Function RemoveWhitespace(input As String) As String
        Return input.Replace(" ", "").Replace(vbTab, "")
    End Function

    Public Function CountWords(input As String) As Integer
        If String.IsNullOrEmpty(input) Then Return 0
        Return input.Split(" "c).Length
    End Function
End Class`,
  },
  {
    path: 'DateHelper.vb',
    content: `Public Class DateHelper
    Private validator As New ValidationHelper()

    Public Function IsWeekday(d As Date) As Boolean
        Return d.DayOfWeek <> DayOfWeek.Saturday AndAlso d.DayOfWeek <> DayOfWeek.Sunday
    End Function

    Public Function GetBusinessDaysBetween(startDate As Date, endDate As Date) As Integer
        Dim count As Integer = 0
        Dim current As Date = startDate
        While current <= endDate
            If IsWeekday(current) Then count += 1
            current = current.AddDays(1)
        End While
        Return count
    End Function

    Public Function FormatFriendly(d As Date) As String
        Return d.ToString("dddd, dd MMMM yyyy")
    End Function
End Class`,
  },
  {
    path: 'Calculator.vb',
    content: `Public Class Calculator
    Private stringHelper As New StringHelper()
    Private dateHelper As New DateHelper()

    Public Function Add(a As Integer, b As Integer) As Integer
        Return a + b
    End Function

    Public Function Subtract(a As Integer, b As Integer) As Integer
        Return a - b
    End Function

    Public Function Multiply(a As Integer, b As Integer) As Integer
        Return a * b
    End Function

    Public Function Divide(a As Integer, b As Integer) As Double
        If b = 0 Then Throw New DivideByZeroException("Cannot divide by zero.")
        Return CDbl(a) / CDbl(b)
    End Function

    Public Function Power(base As Integer, exponent As Integer) As Long
        Return CLng(Math.Pow(base, exponent))
    End Function

    Public Function CalculateCompound(principal As Double, rate As Double, years As Integer) As Double
        Return principal * Math.Pow(1 + rate, years)
    End Function
End Class`,
  },
]

const DEMO_COMPLEX_CONTENT = `' OrderProcessor.vb — handles everything for order processing
' Written by Dave, 2009. Updated by Steve, 2012. Fixed by nobody since.
Imports System.Data.SqlClient
Imports System.Windows.Forms
Imports System.IO

Public Class OrderProcessor
    Inherits Form

    ' Database connection
    Dim cn As SqlConnection
    Dim cmd As SqlCommand
    Dim dr As SqlDataReader
    Dim da As SqlDataAdapter
    Dim ds As DataSet

    ' Form controls
    Dim txtN As TextBox
    Dim txtA As TextBox
    Dim txtQ As TextBox
    Dim lblT As Label
    Dim dgv As DataGridView
    Dim btnS As Button
    Dim btnP As Button

    ' Constants? What constants?
    Dim t As Double = 0.0825
    Dim d1 As Double = 0.1
    Dim d2 As Double = 0.15
    Dim d3 As Double = 0.2
    Dim s1 As Double = 5.99
    Dim s2 As Double = 9.99
    Dim s3 As Double = 14.99
    Dim maxR As Integer = 3

    Private Sub btnS_Click(sender As Object, e As EventArgs) Handles btnS.Click
        On Error Resume Next
        Dim n As String = txtN.Text
        Dim a As Double = CDbl(txtA.Text)
        Dim q As Integer = CInt(txtQ.Text)
        Dim tot As Double = 0
        Dim disc As Double = 0
        Dim ship As Double = 0
        Dim tx As Double = 0
        Dim msg As String = ""

        ' Calculate discount
        If a > 0 Then
            If q > 0 Then
                If a * q > 100 Then
                    If a * q > 500 Then
                        If a * q > 1000 Then
                            disc = d3
                        Else
                            disc = d2
                        End If
                    Else
                        disc = d1
                    End If
                Else
                    disc = 0
                End If
            End If
        End If

        ' Calculate shipping
        If q > 0 Then
            If q <= 5 Then
                ship = s1
            Else
                If q <= 20 Then
                    ship = s2
                Else
                    ship = s3
                End If
            End If
        End If

        tot = (a * q) - ((a * q) * disc) + ship
        tx = tot * t
        tot = tot + tx

        ' Save to database
        cn = New SqlConnection("Server=PROD-DB-01;Database=Orders;Trusted_Connection=True;")
        cn.Open()
        cmd = New SqlCommand("INSERT INTO Orders (CustomerName, Amount, Quantity, Discount, Shipping, Tax, Total, OrderDate) VALUES ('" & n & "', " & a & ", " & q & ", " & disc & ", " & ship & ", " & tx & ", " & tot & ", '" & DateTime.Now.ToString() & "')", cn)
        cmd.ExecuteNonQuery()
        cn.Close()

        ' Also log to file
        Dim sw As New StreamWriter("C:\\OrderLog\\orders.txt", True)
        sw.WriteLine(DateTime.Now.ToString() & "|" & n & "|" & tot)
        sw.Close()

        ' Update the grid
        da = New SqlDataAdapter("SELECT * FROM Orders WHERE CustomerName = '" & n & "' ORDER BY OrderDate DESC", cn)
        ds = New DataSet()
        da.Fill(ds)
        dgv.DataSource = ds.Tables(0)

        ' Format the total
        lblT.Text = "Total: $" & tot.ToString("0.00") & " (Tax: $" & tx.ToString("0.00") & ", Disc: " & (disc * 100).ToString() & "%, Ship: $" & ship.ToString("0.00") & ")"

        ' Show confirmation
        MsgBox("Order saved for " & n & ". Total: $" & tot.ToString("0.00"))
    End Sub

    Public Function CalculateTotal(a As Double, q As Integer) As Double
        Dim tot As Double = 0
        Dim disc As Double = 0
        Dim ship As Double = 0
        Dim tx As Double = 0

        ' Calculate discount — copy pasted from above
        If a > 0 Then
            If q > 0 Then
                If a * q > 100 Then
                    If a * q > 500 Then
                        If a * q > 1000 Then
                            disc = d3
                        Else
                            disc = d2
                        End If
                    Else
                        disc = d1
                    End If
                Else
                    disc = 0
                End If
            End If
        End If

        ' Calculate shipping — copy pasted from above
        If q > 0 Then
            If q <= 5 Then
                ship = s1
            Else
                If q <= 20 Then
                    ship = s2
                Else
                    ship = s3
                End If
            End If
        End If

        tot = (a * q) - ((a * q) * disc) + ship
        tx = tot * t
        tot = tot + tx
        Return tot
    End Function

    Public Function ProcessRefund(orderId As Integer, reason As String) As Boolean
        On Error Resume Next
        Dim r As Boolean = False
        cn = New SqlConnection("Server=PROD-DB-01;Database=Orders;Trusted_Connection=True;")
        cn.Open()

        cmd = New SqlCommand("SELECT * FROM Orders WHERE OrderId = " & orderId, cn)
        dr = cmd.ExecuteReader()

        If dr.Read() Then
            Dim tot As Double = CDbl(dr("Total"))
            Dim cnt As Integer = 0

            ' Check how many refunds already
            dr.Close()
            cmd = New SqlCommand("SELECT COUNT(*) FROM Refunds WHERE OrderId = " & orderId, cn)
            cnt = CInt(cmd.ExecuteScalar())

            If cnt < maxR Then
                If tot > 0 Then
                    If reason <> "" Then
                        cmd = New SqlCommand("INSERT INTO Refunds (OrderId, Amount, Reason, RefundDate) VALUES (" & orderId & ", " & tot & ", '" & reason & "', '" & DateTime.Now.ToString() & "')", cn)
                        cmd.ExecuteNonQuery()

                        cmd = New SqlCommand("UPDATE Orders SET Total = 0, Refunded = 1 WHERE OrderId = " & orderId, cn)
                        cmd.ExecuteNonQuery()

                        ' Send email — hardcoded SMTP
                        Dim smtp As New System.Net.Mail.SmtpClient("mail.company.local")
                        Dim mail As New System.Net.Mail.MailMessage()
                        mail.From = New System.Net.Mail.MailAddress("orders@company.local")
                        mail.To.Add("refunds@company.local")
                        mail.Subject = "Refund Processed #" & orderId
                        mail.Body = "Refund of $" & tot.ToString("0.00") & " processed for order " & orderId & ". Reason: " & reason
                        smtp.Send(mail)

                        ' Also log to file
                        Dim sw As New StreamWriter("C:\\OrderLog\\refunds.txt", True)
                        sw.WriteLine(DateTime.Now.ToString() & "|" & orderId & "|" & tot & "|" & reason)
                        sw.Close()

                        MsgBox("Refund processed.")
                        r = True
                    End If
                End If
            Else
                MsgBox("Maximum refunds reached for this order.")
            End If
        End If

        cn.Close()
        Return r
    End Function

    Private Sub btnP_Click(sender As Object, e As EventArgs) Handles btnP.Click
        On Error Resume Next
        ' Print report — also does way too much
        cn = New SqlConnection("Server=PROD-DB-01;Database=Orders;Trusted_Connection=True;")
        cn.Open()
        cmd = New SqlCommand("SELECT * FROM Orders WHERE OrderDate >= '" & DateTime.Today.AddDays(-30).ToString() & "' ORDER BY Total DESC", cn)
        dr = cmd.ExecuteReader()
        Dim sw As New StreamWriter("C:\\OrderLog\\report_" & DateTime.Now.ToString("yyyyMMdd") & ".txt")
        Dim gt As Double = 0
        Dim gc As Integer = 0
        While dr.Read()
            Dim n As String = dr("CustomerName").ToString()
            Dim tot As Double = CDbl(dr("Total"))
            sw.WriteLine(n & " | $" & tot.ToString("0.00"))
            gt = gt + tot
            gc = gc + 1
        End While
        sw.WriteLine("---")
        sw.WriteLine("Total Orders: " & gc)
        sw.WriteLine("Grand Total: $" & gt.ToString("0.00"))
        sw.WriteLine("Average: $" & (gt / gc).ToString("0.00"))
        sw.Close()
        dr.Close()
        cn.Close()
        MsgBox("Report generated: " & gc & " orders, $" & gt.ToString("0.00") & " total.")
    End Sub

    Public Function ValidateOrder(n As String, a As String, q As String) As String
        Dim err As String = ""
        If n = "" Then
            err = err & "Name required. "
        End If
        If Not IsNumeric(a) Then
            err = err & "Amount must be numeric. "
        Else
            If CDbl(a) <= 0 Then
                err = err & "Amount must be positive. "
            End If
        End If
        If Not IsNumeric(q) Then
            err = err & "Quantity must be numeric. "
        Else
            If CInt(q) <= 0 Then
                err = err & "Quantity must be positive. "
            End If
        End If
        Return err
    End Function

    Public Function GetDiscountTier(subtotal As Double) As String
        ' GoTo for flow control — a classic
        If subtotal <= 0 Then GoTo NoDiscount
        If subtotal <= 100 Then GoTo NoDiscount
        If subtotal <= 500 Then GoTo Tier1
        If subtotal <= 1000 Then GoTo Tier2
        GoTo Tier3

NoDiscount:
        Return "NONE"
Tier1:
        Return "BRONZE"
Tier2:
        Return "SILVER"
Tier3:
        Return "GOLD"
    End Function
End Class`

const DEMO_COMPLEX_FILENAME = 'OrderProcessor.vb'

// Assure demo. Unlike the Migrate complex demo (a WinForms God class that inherits Form
// and can't run headless), this is the SAME OrderProcessor business logic with the UI
// severed — pure, self-contained, and compilable standalone on the Linux CLR sidecar, so
// the real characterisation run reaches GREEN instead of the WinForms ERROR path. It keeps
// the supporting types (LineItem, Order) the suite needs, and preserves the observed faults
// (silent unknown-code discount, divide-by-zero, non-numeric cast).
const DEMO_ASSURE_CONTENT = `' OrderProcessor.vb — order-processing business logic (no UI; runs headless on the CLR)
Imports System
Imports System.Collections.Generic

Public Class LineItem
    Public Property UnitPrice As Decimal
    Public Property Quantity As Integer
    Public Sub New(unitPrice As Decimal, quantity As Integer)
        Me.UnitPrice = unitPrice
        Me.Quantity = quantity
    End Sub
End Class

Public Class Order
    Public Property Items As List(Of LineItem) = New List(Of LineItem)()
    Public Property Total As Decimal
End Class

Public Class OrderProcessor
    ' Sum of unitPrice * quantity. A null line item throws NullReferenceException.
    Public Function CalculateTotal(items As IReadOnlyList(Of LineItem)) As Decimal
        Dim total As Decimal = 0D
        For Each item In items
            total += item.UnitPrice * item.Quantity
        Next
        Return total
    End Function

    ' Known codes discount; an unknown code silently returns the subtotal unchanged.
    Public Function ApplyDiscount(subtotal As Decimal, code As String) As Decimal
        Select Case code
            Case "SAVE10"
                Return subtotal * 0.9D
            Case "HALF"
                Return subtotal * 0.5D
            Case Else
                Return subtotal
        End Select
    End Function

    ' Integer division — a headcount of 0 throws DivideByZeroException.
    Public Function SplitPerHead(total As Decimal, headcount As Integer) As Decimal
        Return CDec(CLng(total) \\ headcount)
    End Function

    ' Quantities come straight off a text field; a non-numeric value throws InvalidCastException.
    Public Function ParseQty(text As String) As Integer
        Return CInt(text)
    End Function

    Public Function ValidateOrder(order As Order) As Boolean
        Return order.Items.Count > 0 AndAlso order.Total >= 0D
    End Function
End Class`

// Portfolio demos — REAL multi-class VB.NET so the live /assess classifier (which parses
// the source, not a zip) produces a genuine mixed report. Shapes are chosen to hit each
// bucket: plain classes → net-ready; pure methods inside a Form → windows-gated; handlers /
// control access / MsgBox → refactor-first.
const DEMO_ESTATE_MIXED = `' LegacyEstate — a mixed VB.NET estate (business logic + WinForms)

Public Class OrderService
    Public Function PlaceOrder(customerId As Integer, total As Decimal) As Integer
        Return customerId + CInt(total)
    End Function
    Public Function CalculateTotal(qty As Integer, price As Decimal) As Decimal
        Return qty * price
    End Function
    Public Function ApplyDiscount(subtotal As Decimal, code As String) As Decimal
        If code = "SAVE10" Then Return subtotal * 0.9D
        Return subtotal
    End Function
End Class

Public Class PricingEngine
    Public Function Quote(basePrice As Decimal, margin As Decimal) As Decimal
        Return basePrice * (1D + margin)
    End Function
    Public Function RoundToTier(amount As Decimal) As Decimal
        Return Math.Ceiling(amount)
    End Function
End Class

Public Class TaxCalculator
    Public Function VatFor(net As Decimal) As Decimal
        Return net * 0.2D
    End Function
    Public Function NetOf(gross As Decimal) As Decimal
        Return gross / 1.2D
    End Function
End Class

Public Class LedgerView
    Inherits Form
    Private Function Post(amount As Decimal) As Decimal
        Return amount * -1D
    End Function
    Private Function Reconcile(a As Decimal, b As Decimal) As Decimal
        Return a - b
    End Function
End Class

Public Class ReportRenderer
    Inherits Form
    Private Function BuildSummary(count As Integer, total As Decimal) As String
        Return count.ToString() & " orders"
    End Function
End Class

Public Class CustomerEntryForm
    Inherits Form
    Private WithEvents btnSave As Button
    Private txtName As TextBox
    Private Sub btnSave_Click(sender As Object, e As EventArgs) Handles btnSave.Click
        txtName.Text = txtName.Text.Trim()
    End Sub
End Class

Public Class MainForm
    Inherits Form
    Private WithEvents btnRun As Button
    Private Sub btnRun_Click(sender As Object, e As EventArgs) Handles btnRun.Click
        MsgBox("Running")
    End Sub
End Class`

const DEMO_ESTATE_BLOCKED = `' WinFormsApp — a WinForms app with no headless surface

Public Class CalculatorForm
    Inherits Form
    Private WithEvents btnEquals As Button
    Private txtDisplay As TextBox
    Private Sub btnEquals_Click(sender As Object, e As EventArgs) Handles btnEquals.Click
        txtDisplay.Text = "0"
    End Sub
End Class

Public Class SettingsForm
    Inherits Form
    Private chkAuto As CheckBox
    Private Sub Save()
        Dim auto As Boolean = chkAuto.Checked
    End Sub
End Class

Public Class PrintHelper
    Inherits Form
    Private Function Paginate(lines As Integer, perPage As Integer) As Integer
        Return lines \\ perPage
    End Function
End Class

Public Class GridView
    Inherits Form
    Private Function SortKey(a As Integer, b As Integer) As Integer
        Return a - b
    End Function
End Class`

export {
  DEMO_VB_CONTENT,
  DEMO_FILENAME,
  DEMO_COMPLEX_CONTENT,
  DEMO_COMPLEX_FILENAME,
  DEMO_ASSURE_CONTENT,
  DEMO_ESTATE_MIXED,
  DEMO_ESTATE_BLOCKED,
}
