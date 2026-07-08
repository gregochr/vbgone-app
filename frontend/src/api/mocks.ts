import type { Bucket, EngineParams } from '../config/engine'
import type { MigrateApi } from './client'
import type {
  ObservedBehaviour,
  AnalysisResult,
  InterfaceResult,
  MutationResult,
  MutationJobStatus,
  TestsResult,
  StubResult,
  BuildResult,
  ImplementResult,
  MethodReadiness,
  RestApiEndpoint,
  ReadinessReport,
  BaselineMember,
  BaselineResult,
  BaselineTestsResult,
  RepairAttemptResult,
  PullRequestResult,
  ProjectAnalysis,
  CostResult,
} from './types'
import { DEMO_COMPLEX_FILENAME } from './demos'

/* ── Mock data ── */

const MOCK_SESSION_ID = 'mock-uuid-1234'

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

// Mock mutation-testing state: a job that advances a few mutants per poll, then settles.
const MOCK_MUTATION_TOTAL = 12
let mockMutationDone = 0
const MOCK_MUTATION_RESULT: MutationResult = {
  total: 12,
  killed: 9,
  survived: 2,
  skipped: 1,
  score: 82,
  survivors: [
    {
      line: 34,
      operator: 'boundary-literal',
      before: '1000',
      after: '1001',
      description: 'off-by-one: 1000 → 1001 (SILVER threshold) — no test pins subtotal = 1000',
    },
    {
      line: 41,
      operator: 'relational',
      before: '<=',
      after: '<',
      description: 'boundary <= → < on the shipping cap — the boundary quantity is unasserted',
    },
  ],
}

const mockApi: MigrateApi = {
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

  async augmentBaselineTests(
    sessionId: string,
    className: string,
    code: string,
    coveragePercent: number | null,
    engine?: EngineParams,
  ): Promise<BaselineTestsResult> {
    void coveragePercent
    void engine
    await delay(1500)
    // Demo: Claude adds targeted tests, so the net stays green and coverage climbs.
    const base = mockGreenNet(
      sessionId,
      className,
      code + '\n\n    [TestMethod]\n    public void GetDiscountTier_At1000_IsSilver() { }',
    )
    return {
      ...base,
      testCount: base.testCount + 7,
      build: {
        ...base.build,
        total: base.build.total + 7,
        passed: base.build.passed + 7,
        coveragePercent: 94.0,
        branchCoveragePercent: 100.0,
      },
    }
  },

  async quarantineBaseline(
    sessionId: string,
    className: string,
    code: string,
    tests: string[],
  ): Promise<BaselineTestsResult> {
    void tests
    // Demo: setting the unrepairable test(s) aside leaves a green baseline of the rest.
    await delay(900)
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

  async startMutationTest(
    sessionId: string,
    className: string,
    suiteCode: string,
  ): Promise<MutationJobStatus> {
    void sessionId
    void className
    void suiteCode
    await delay(600)
    mockMutationDone = 0
    return {
      jobId: 'mock-mutation',
      state: 'RUNNING',
      done: 0,
      total: MOCK_MUTATION_TOTAL,
      result: null,
      error: null,
    }
  },

  async getMutationJob(jobId: string): Promise<MutationJobStatus> {
    void jobId
    await delay(500)
    // Advance a few mutants per poll so the UI animates, then settle on a final score.
    mockMutationDone = Math.min(MOCK_MUTATION_TOTAL, mockMutationDone + 4)
    if (mockMutationDone < MOCK_MUTATION_TOTAL) {
      return {
        jobId,
        state: 'RUNNING',
        done: mockMutationDone,
        total: MOCK_MUTATION_TOTAL,
        result: null,
        error: null,
      }
    }
    return {
      jobId,
      state: 'DONE',
      done: MOCK_MUTATION_TOTAL,
      total: MOCK_MUTATION_TOTAL,
      result: MOCK_MUTATION_RESULT,
      error: null,
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

export { mockApi }
