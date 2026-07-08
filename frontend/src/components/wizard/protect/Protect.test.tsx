import { describe, it, expect, vi } from 'vitest'
import { useState } from 'react'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { WizardConfigProvider } from '../../../config/WizardConfigContext'
import { AppHeader } from '../../AppHeader'
import { WizardShell } from '../WizardShell'
import { Step3Baseline } from './Step3Baseline'
import { Step4BaselineTests } from './Step4BaselineTests'
import { StepReadiness } from './StepReadiness'
import type { WizardState } from '../WizardShell'
import type {
  BaselineResult,
  BaselineTestsResult,
  ReadinessReport,
  RestApiEndpoint,
} from '../../../api/migrateApi'
import { looksUiCoupled } from '../../../config/engine'

const baseState: WizardState = {
  filename: 'OrderProcessor.vb',
  content: '',
  analysis: {
    sessionId: 'session-1',
    classes: [
      { name: 'OrderProcessor', methods: ['SplitPerHead'], dependencies: [], complexity: 'MEDIUM' },
    ],
    suggestedMigrationOrder: ['OrderProcessor'],
    summary: 'One class',
  },
  currentClassIndex: 0,
  completedClasses: [],
  interfaceResult: null,
  tests: null,
  stubResult: null,
  redBuild: null,
  implementResult: null,
  greenBuild: null,
  prResult: null,
  zipFile: null,
  readiness: null,
  baselineResult: null,
  baselineTests: null,
  netFaithful: true,
  netted: [],
  fromQueue: false,
}

const readyReport: ReadinessReport = {
  sessionId: 'session-1',
  confidence: 'static',
  totals: {
    classes: 1,
    methods: 1,
    netReady: 1,
    windowsGated: 0,
    refactorFirst: 0,
    methodNetReady: 1,
    methodWindowsGated: 0,
    methodRefactorFirst: 0,
  },
  classes: [
    {
      name: 'OrderProcessor',
      file: 'OrderProcessor.vb',
      bucket: 'net-ready',
      reason: 'public, no WinForms references',
      methods: [
        { name: 'CalculateTotal', visibility: 'public', bucket: 'net-ready', reason: 'pure' },
      ],
    },
  ],
}

const blockedReport: ReadinessReport = {
  sessionId: 'session-1',
  confidence: 'static',
  totals: {
    classes: 1,
    methods: 1,
    netReady: 0,
    windowsGated: 0,
    refactorFirst: 1,
    methodNetReady: 0,
    methodWindowsGated: 0,
    methodRefactorFirst: 1,
  },
  classes: [
    {
      name: 'Form1',
      file: 'Form1.vb',
      bucket: 'refactor-first',
      reason: 'logic welded into Button_Click',
      methods: [
        {
          name: 'btnAdd_Click',
          visibility: 'private',
          bucket: 'refactor-first',
          reason: 'reads TextBox.Text',
        },
      ],
    },
  ],
}

const mockBaseline: BaselineResult = {
  sessionId: 'session-1',
  className: 'OrderProcessor',
  surfaceFile: 'OrderProcessor.dll · public surface',
  members: [
    { signature: 'decimal CalculateTotal(IReadOnlyList<LineItem> items)' },
    {
      signature: 'decimal SplitPerHead(decimal total, int headcount)',
      defect: 'throws DivideByZeroException when headcount = 0',
    },
  ],
}

const mockBaselineTests: BaselineTestsResult = {
  sessionId: 'session-1',
  className: 'OrderProcessor',
  testClassName: 'OrderProcessorBaseline',
  code: '[TestClass] public class OrderProcessorBaseline {}',
  testCount: 43,
  netFaithful: true,
  build: {
    sessionId: 'session-1',
    buildStatus: 'GREEN',
    total: 43,
    passed: 43,
    failed: 0,
    errors: [],
    failedTests: [],
  },
  failures: [],
}

const renderWithConfig = (ui: React.ReactNode) =>
  render(<WizardConfigProvider>{ui}</WizardConfigProvider>)

// Stateful harness so `update` actually merges back into state (the real WizardShell does).
function ReadinessHarness({ initial }: { initial: WizardState }) {
  const [s, setS] = useState(initial)
  return (
    <StepReadiness
      state={s}
      update={(p) => setS((prev) => ({ ...prev, ...p }))}
      onReady={() => {}}
    />
  )
}

describe('AppHeader — MODE toggle', () => {
  it('shows the MODE control and a migrate caption by default', () => {
    renderWithConfig(<AppHeader />)
    expect(screen.getByText('MODE')).toBeInTheDocument()
    expect(screen.getByText('Migrate')).toBeInTheDocument()
    expect(screen.getByText('VB.NET → C#')).toBeInTheDocument()
  })

  it('locks Java and flips the caption when switching to Protect', async () => {
    const user = userEvent.setup()
    renderWithConfig(<AppHeader />)
    await user.click(screen.getByText('Protect'))

    expect(screen.getByText('VB.NET · behaviour safety net')).toBeInTheDocument()
    const java = screen.getByText('Java')
    expect(java).toHaveClass('locked')
    expect(java).toHaveAttribute('title', expect.stringContaining('C# only'))
    // C# stays active.
    expect(screen.getByText('C#')).toHaveClass('active')
  })
})

describe('WizardShell — mode-aware stepper', () => {
  it('renders four relabelled Protect steps and STEP n / 4', async () => {
    const user = userEvent.setup()
    renderWithConfig(
      <>
        <AppHeader />
        <WizardShell />
      </>,
    )
    await user.click(screen.getByText('Protect'))

    expect(screen.getByText('Baseline', { exact: true })).toBeInTheDocument()
    expect(screen.getByText('Baseline Tests')).toBeInTheDocument()
    expect(screen.queryByText('Interface')).not.toBeInTheDocument()
    expect(screen.queryByText('Raise PR')).not.toBeInTheDocument()
    expect(screen.getByTestId('step-counter')).toHaveTextContent('STEP 1 / 4')
  })

  it('restores the six Migrate steps when switching back', async () => {
    const user = userEvent.setup()
    renderWithConfig(
      <>
        <AppHeader />
        <WizardShell />
      </>,
    )
    await user.click(screen.getByText('Protect'))
    await user.click(screen.getByText('Migrate'))

    expect(screen.getByText('Interface')).toBeInTheDocument()
    expect(screen.getByText('Raise PR')).toBeInTheDocument()
    expect(screen.getByTestId('step-counter')).toHaveTextContent('STEP 1 / 6')
  })
})

describe('Step3Baseline', () => {
  it('renders the inverted notice, sub-bar and pinned surface with defect tags', () => {
    const state = { ...baseState, baselineResult: mockBaseline }
    renderWithConfig(<Step3Baseline state={state} update={() => {}} onReady={() => {}} />)

    expect(screen.getByText('Behaviour recorded as-is')).toBeInTheDocument()
    expect(screen.getByText('Green means unchanged, not correct.')).toBeInTheDocument()
    expect(screen.getByText('OrderProcessor.dll · public surface')).toBeInTheDocument()
    expect(
      screen.getByText('decimal SplitPerHead(decimal total, int headcount)'),
    ).toBeInTheDocument()
    expect(screen.getByText(/throws DivideByZeroException when headcount = 0/)).toBeInTheDocument()
  })

  it('asks to confirm before pinning when no baseline yet', () => {
    renderWithConfig(<Step3Baseline state={baseState} update={() => {}} onReady={() => {}} />)
    expect(screen.getByText(/capture the current behaviour of/i)).toBeInTheDocument()
  })
})

describe('Step4BaselineTests', () => {
  it('shows the GREEN banner and closing panel when the run is green', () => {
    const state = { ...baseState, baselineTests: mockBaselineTests, netFaithful: true }
    renderWithConfig(<Step4BaselineTests state={state} update={() => {}} onReady={() => {}} />)

    expect(screen.getByTestId('net-banner-green')).toBeInTheDocument()
    expect(screen.getByText(/43 \/ 43 passing/)).toBeInTheDocument()
    expect(screen.getByText('You now have a safety net of tests')).toBeInTheDocument()
  })

  it('lists the failing tests and offers Edit tests & re-run when a run is red', () => {
    const drifted: BaselineTestsResult = {
      ...mockBaselineTests,
      netFaithful: false,
      build: {
        ...mockBaselineTests.build,
        buildStatus: 'RED',
        passed: 41,
        failed: 2,
        failedTests: ['ApplyDiscount_UnknownCode_ReturnsSubtotalUnchanged'],
      },
      failures: [
        {
          name: 'ApplyDiscount_UnknownCode_ReturnsSubtotalUnchanged',
          message: 'Expected: 100 but was: 90',
        },
      ],
    }
    const state = { ...baseState, baselineTests: drifted, netFaithful: false }
    renderWithConfig(<Step4BaselineTests state={state} update={() => {}} onReady={() => {}} />)

    expect(screen.getByTestId('net-banner-red')).toBeInTheDocument()
    // The failing test name + message are surfaced — not just the count.
    expect(screen.getByTestId('failed-tests')).toBeInTheDocument()
    expect(
      screen.getByText('ApplyDiscount_UnknownCode_ReturnsSubtotalUnchanged'),
    ).toBeInTheDocument()
    expect(screen.getByText('Expected: 100 but was: 90')).toBeInTheDocument()
    // The action makes the intent clear (edit, not just re-run) and the closing panel is hidden.
    expect(screen.getByText('Edit tests & re-run')).toBeInTheDocument()
    expect(screen.queryByText('Re-run suite')).not.toBeInTheDocument()
    expect(screen.queryByTestId('baseline-closing')).not.toBeInTheDocument()
  })

  it('shows a distinct "0 runnable tests" state (not drift) and offers re-generation', () => {
    // The suite compiled and ran (no errors), but MSTest discovered 0 tests — total 0/0.
    const empty: BaselineTestsResult = {
      ...mockBaselineTests,
      netFaithful: false,
      testCount: 0,
      build: {
        ...mockBaselineTests.build,
        buildStatus: 'GREEN',
        total: 0,
        passed: 0,
        failed: 0,
        errors: [],
        failedTests: [],
      },
      failures: [],
    }
    const state = { ...baseState, baselineTests: empty, netFaithful: false }
    renderWithConfig(<Step4BaselineTests state={state} update={() => {}} onReady={() => {}} />)

    expect(screen.getByTestId('net-banner-red')).toBeInTheDocument()
    expect(screen.getByTestId('net-no-tests')).toBeInTheDocument()
    expect(screen.getByText(/0 runnable tests/)).toBeInTheDocument()
    // Re-generation, not edit-and-re-run of an empty suite; and no "wrong test" framing.
    expect(screen.getByText('Re-generate suite & run')).toBeInTheDocument()
    expect(screen.queryByText('Edit tests & re-run')).not.toBeInTheDocument()
    expect(screen.queryByTestId('failed-tests')).not.toBeInTheDocument()
  })

  const erroredResult = (errors: string[]): BaselineTestsResult => ({
    ...mockBaselineTests,
    netFaithful: false,
    testCount: 0,
    build: {
      ...mockBaselineTests.build,
      buildStatus: 'ERROR',
      total: 0,
      passed: 0,
      failed: 0,
      errors,
    },
    failures: [],
  })

  it('surfaces compile errors (the WinForms/degraded path) distinctly from a failing test', () => {
    const errored = erroredResult(['OrderProcessor.vb(8): error BC30002: Type Form is not defined'])
    const state = { ...baseState, baselineTests: errored, netFaithful: false }
    renderWithConfig(<Step4BaselineTests state={state} update={() => {}} onReady={() => {}} />)

    expect(screen.getByText(/didn't compile against the CLR/)).toBeInTheDocument()
    expect(screen.getByText(/error BC30002/)).toBeInTheDocument()
  })

  it('explains the UI-coupled precondition when the original is WinForms', () => {
    const errored = erroredResult(['Form1.vb(3): error BC30451: TextBox1 is not declared'])
    const state = {
      ...baseState,
      content: 'Imports System.Windows.Forms\nPublic Class Form1\n  Inherits Form\nEnd Class',
      baselineTests: errored,
      netFaithful: false,
    }
    renderWithConfig(<Step4BaselineTests state={state} update={() => {}} onReady={() => {}} />)

    const hint = screen.getByTestId('net-precondition')
    expect(hint).toHaveTextContent(/UI-coupled/)
    expect(hint).toHaveTextContent(/business-logic class that runs without the screen/)
  })

  it('gives a generic compile hint when the source is not obviously UI-coupled', () => {
    const errored = erroredResult([
      'OrderProcessor.vb(5): error BC30456: SomeHelper is not a member',
    ])
    const state = {
      ...baseState,
      content:
        'Public Class OrderProcessor\n  Public Function F() As Integer\n    Return 1\n  End Function\nEnd Class',
      baselineTests: errored,
      netFaithful: false,
    }
    renderWithConfig(<Step4BaselineTests state={state} update={() => {}} onReady={() => {}} />)

    const hint = screen.getByTestId('net-precondition')
    expect(hint).toHaveTextContent(/self-contained with no UI or platform dependencies/)
    expect(hint).not.toHaveTextContent(/UI-coupled/)
  })

  // ── Auto-repair loop (client-driven demo, reveals attempts on timers) ──
  it('enters the pre-repair state and switches demo scenarios from the green banner', async () => {
    const user = userEvent.setup()
    const state = { ...baseState, baselineTests: mockBaselineTests, netFaithful: true }
    renderWithConfig(<Step4BaselineTests state={state} update={() => {}} onReady={() => {}} />)

    await user.click(screen.getByTestId('simulate-fail'))
    // Red drift banner + failed-tests panel + the demo switcher appear.
    expect(screen.getByTestId('net-banner-red')).toBeInTheDocument()
    expect(screen.getByTestId('failed-tests')).toBeInTheDocument()
    expect(screen.getByText('PlaceOrder_TotalWithFraction_TruncatesToInt')).toBeInTheDocument()
    expect(screen.getByText('Auto-repair · up to 3 attempts')).toBeInTheDocument()

    // Switching to the "Unfixable" arc swaps the failing test on show.
    await user.click(screen.getByText('Unfixable'))
    expect(screen.getByText('PlaceOrder_StampsSequenceNumber')).toBeInTheDocument()
  })

  // Attempts reveal on real timers (~1.15s each + ~0.48s gap), so these poll with a
  // generous timeout rather than fake timers (which deadlock against async userEvent here).
  it('runs the simple-fix arc to a repaired baseline', async () => {
    const user = userEvent.setup()
    const state = { ...baseState, baselineTests: mockBaselineTests, netFaithful: true }
    renderWithConfig(<Step4BaselineTests state={state} update={() => {}} onReady={() => {}} />)

    await user.click(screen.getByTestId('simulate-fail'))
    await user.click(screen.getByText('Auto-repair · up to 3 attempts'))

    const banner = await screen.findByTestId('repair-succeeded', undefined, { timeout: 4000 })
    expect(banner).toHaveTextContent(/BASELINE REPAIRED/)
    expect(screen.getByTestId('baseline-closing')).toBeInTheDocument()
  }, 8000)

  it('quarantines the unfixable (different-answer-every-run) test after 3 attempts', async () => {
    const user = userEvent.setup()
    const state = { ...baseState, baselineTests: mockBaselineTests, netFaithful: true }
    renderWithConfig(<Step4BaselineTests state={state} update={() => {}} onReady={() => {}} />)

    await user.click(screen.getByTestId('simulate-fail'))
    await user.click(screen.getByText('Unfixable'))
    await user.click(screen.getByText('Auto-repair · up to 3 attempts'))

    const card = await screen.findByTestId('repair-quarantined', undefined, { timeout: 9000 })
    expect(card).toHaveTextContent(/set aside for review/)
    expect(card).toHaveTextContent(/different answer every run/)
    // A quarantine leaves the baseline red — no closing panel.
    expect(screen.queryByTestId('baseline-closing')).not.toBeInTheDocument()
  }, 14000)
})

describe('StepReadiness (single-file verdict)', () => {
  it('shows a Ready-to-protect verdict and signals ready when the class is net-ready', () => {
    const onReady = vi.fn()
    const state = { ...baseState, filename: 'OrderProcessor.vb', readiness: readyReport }
    renderWithConfig(<StepReadiness state={state} update={() => {}} onReady={onReady} />)

    const card = screen.getByTestId('verdict-card')
    expect(card).toHaveClass('verdict-ready')
    expect(card).toHaveTextContent('Ready to protect')
    expect(card).toHaveTextContent('business logic that runs on its own')
    expect(card).toHaveTextContent('CalculateTotal')
    // Next is gated on readiness — a net-ready class signals ready.
    expect(onReady).toHaveBeenCalled()
  })

  it('shows a blocked verdict and does NOT signal ready when tangled in the UI', () => {
    const onReady = vi.fn()
    const state = { ...baseState, filename: 'Form1.vb', readiness: blockedReport }
    renderWithConfig(<StepReadiness state={state} update={() => {}} onReady={onReady} />)

    const card = screen.getByTestId('verdict-card')
    expect(card).toHaveClass('verdict-blocked')
    expect(card).toHaveTextContent('Tangled in the UI')
    expect(card).toHaveTextContent("Can't protect this as-is")
    // Blocked → Next stays disabled (onReady never fires).
    expect(onReady).not.toHaveBeenCalled()
  })
})

const mixedPortfolio: ReadinessReport = {
  sessionId: 'p1',
  confidence: 'static',
  totals: {
    classes: 3,
    methods: 6,
    netReady: 1,
    windowsGated: 1,
    refactorFirst: 1,
    methodNetReady: 2,
    methodWindowsGated: 2,
    methodRefactorFirst: 2,
  },
  classes: [
    {
      name: 'OrderService',
      file: 'Services/OrderService.vb',
      bucket: 'net-ready',
      reason: 'public, no WinForms references',
      methods: [
        { name: 'PlaceOrder', visibility: 'public', bucket: 'net-ready', reason: 'pure' },
        { name: 'CalcTotal', visibility: 'public', bucket: 'net-ready', reason: 'pure' },
      ],
    },
    {
      name: 'LedgerView',
      file: 'Forms/LedgerView.vb',
      bucket: 'windows-gated',
      reason: 'pure posting logic in a WinForms class',
      methods: [
        { name: 'Post', visibility: 'private', bucket: 'windows-gated', reason: 'pure, UI-bound' },
      ],
    },
    {
      name: 'MainForm',
      file: 'MainForm.vb',
      bucket: 'refactor-first',
      reason: 'orchestrates controls',
      methods: [
        {
          name: 'btnRun_Click',
          visibility: 'private',
          bucket: 'refactor-first',
          reason: 'drives UI',
        },
      ],
    },
  ],
}

describe('StepReadiness (portfolio report)', () => {
  const portfolioState = { ...baseState, filename: 'LegacyEstate.zip', readiness: mixedPortfolio }

  it('renders the breakdown, per-class table and per-bucket actions', () => {
    renderWithConfig(<StepReadiness state={portfolioState} update={() => {}} onReady={() => {}} />)
    expect(screen.getByTestId('readiness-report')).toBeInTheDocument()
    // net-ready row is actionable; the others are disabled with the right labels.
    expect(screen.getByText('Protect this class →')).toBeInTheDocument()
    expect(screen.getByText('Untangle first')).toBeInTheDocument()
    // "Needs Windows runner" appears as both a filter chip and the disabled action.
    expect(screen.getAllByText('Needs Windows runner').length).toBeGreaterThan(1)
    expect(screen.getByTestId('proceed-panel')).toBeInTheDocument()
    // stacked-bar legend adds up.
    expect(screen.getAllByText('33%').length).toBeGreaterThan(0)
  })

  it('drills into the per-class flow when Protect this class is clicked', async () => {
    const onProtectClass = vi.fn()
    const user = userEvent.setup()
    renderWithConfig(
      <StepReadiness
        state={portfolioState}
        update={() => {}}
        onReady={() => {}}
        onProtectClass={onProtectClass}
      />,
    )
    await user.click(screen.getByText('Protect this class →'))
    expect(onProtectClass).toHaveBeenCalledWith('OrderService')
  })

  it('filters the table to a single bucket', async () => {
    const user = userEvent.setup()
    renderWithConfig(<StepReadiness state={portfolioState} update={() => {}} onReady={() => {}} />)
    expect(screen.getByText('MainForm')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Ready to protect/ }))
    expect(screen.getByText('OrderService')).toBeInTheDocument()
    expect(screen.queryByText('MainForm')).not.toBeInTheDocument()
    expect(screen.queryByText('LedgerView')).not.toBeInTheDocument()
  })

  it('scans a real uploaded .zip via assess-project and shows the report', async () => {
    const user = userEvent.setup()
    // A zip File + no report yet → scan-idle; "Assess readiness" hits assessProject (mock).
    const initial = {
      ...baseState,
      filename: 'LegacyEstate.zip',
      content: '',
      readiness: null,
      zipFile: new File(['x'], 'LegacyEstate.zip'),
    }
    renderWithConfig(<ReadinessHarness initial={initial} />)
    await user.click(screen.getByText('Assess readiness'))
    await waitFor(() => expect(screen.getByTestId('readiness-report')).toBeInTheDocument(), {
      timeout: 3000,
    })
  })

  it('shows an explicit empty state (not a loop) when a scan finds no classes', () => {
    const empty: ReadinessReport = {
      sessionId: 'e1',
      confidence: 'static',
      totals: {
        classes: 0,
        methods: 0,
        netReady: 0,
        windowsGated: 0,
        refactorFirst: 0,
        methodNetReady: 0,
        methodWindowsGated: 0,
        methodRefactorFirst: 0,
      },
      classes: [],
    }
    renderWithConfig(
      <StepReadiness
        state={{ ...portfolioState, filename: 'Empty.zip', readiness: empty }}
        update={() => {}}
        onReady={() => {}}
      />,
    )
    expect(screen.getByTestId('scan-empty')).toBeInTheDocument()
    expect(screen.getByText('No classes found')).toBeInTheDocument()
    expect(screen.queryByTestId('readiness-report')).not.toBeInTheDocument()
  })

  it('shows the blocked gate and no proceed panel when nothing is ready', () => {
    const blocked: ReadinessReport = {
      ...mixedPortfolio,
      totals: { ...mixedPortfolio.totals, netReady: 0, windowsGated: 1, refactorFirst: 2 },
      classes: mixedPortfolio.classes.map((c) =>
        c.bucket === 'net-ready' ? { ...c, bucket: 'refactor-first' as const } : c,
      ),
    }
    renderWithConfig(
      <StepReadiness
        state={{ ...portfolioState, readiness: blocked }}
        update={() => {}}
        onReady={() => {}}
      />,
    )
    expect(screen.getByTestId('portfolio-gate')).toBeInTheDocument()
    expect(screen.getByText('Nothing to protect yet')).toBeInTheDocument()
    expect(screen.queryByTestId('proceed-panel')).not.toBeInTheDocument()
  })
})

const restApis: RestApiEndpoint[] = [
  {
    verb: 'GET',
    route: '/api/orders',
    handler: 'OrderApiController.Get',
    source: 'Api/OrderApiController.vb',
    kind: 'Web API',
    params: [{ name: 'status', in: 'query', type: 'string', note: 'optional — open or closed' }],
    reqType: '—',
    req: null,
    resType: 'OrderDto',
    resStatus: '200 OK',
    res: '[]',
  },
  {
    verb: 'POST',
    route: '/api/orders',
    handler: 'OrderApiController.Create',
    source: 'Api/OrderApiController.vb',
    kind: 'Web API',
    params: [],
    reqType: 'CreateOrderRequest',
    req: '{ "customerId": 187 }',
    resType: 'OrderDto',
    resStatus: '201 Created',
    res: '{ "id": 4822 }',
  },
]

describe('StepReadiness — REST API endpoints panel', () => {
  const withApis: ReadinessReport = { ...mixedPortfolio, restApis }
  const apiState = { ...baseState, filename: 'LegacyEstate.zip', readiness: withApis }

  it('renders the panel with plain-English copy (and none of the design jargon)', () => {
    renderWithConfig(<StepReadiness state={apiState} update={() => {}} onReady={() => {}} />)
    const panel = screen.getByTestId('rest-panel')

    expect(panel).toHaveTextContent('REST API ENDPOINTS FOUND')
    expect(panel).toHaveTextContent('SUPPORT COMING LATER')
    // 2 endpoints, both from the same file → "1 file" (singular).
    expect(panel).toHaveTextContent('2 web API endpoints')
    expect(panel).toHaveTextContent('across 1 file')
    expect(panel).toHaveTextContent('nothing was called and no request was sent')
    // The dumbed-down copy must not reproduce the design's jargon.
    expect(panel).not.toHaveTextContent(/surface the surface/i)
    expect(panel).not.toHaveTextContent(/ApiController inheritance detected statically/i)
    // Count readout.
    expect(within(panel).getByText('endpoints')).toBeInTheDocument()
  })

  it('expands an endpoint to reveal its request/response payloads', async () => {
    const user = userEvent.setup()
    renderWithConfig(<StepReadiness state={apiState} update={() => {}} onReady={() => {}} />)
    const panel = screen.getByTestId('rest-panel')

    // Collapsed: payload labels are not shown yet.
    expect(within(panel).queryByText('REQUEST BODY')).not.toBeInTheDocument()

    // Clicking the POST row (via its handler) expands it.
    await user.click(within(panel).getByText('OrderApiController.Create'))

    expect(within(panel).getByText('REQUEST BODY')).toBeInTheDocument()
    expect(within(panel).getByText('CreateOrderRequest')).toBeInTheDocument()
    expect(within(panel).getByText('RESPONSE')).toBeInTheDocument()
    expect(within(panel).getByText('201 Created')).toBeInTheDocument()
  })

  it('hides the panel when the scan found no endpoints', () => {
    renderWithConfig(
      <StepReadiness
        state={{ ...baseState, filename: 'LegacyEstate.zip', readiness: mixedPortfolio }}
        update={() => {}}
        onReady={() => {}}
      />,
    )
    expect(screen.queryByTestId('rest-panel')).not.toBeInTheDocument()
  })
})

describe('looksUiCoupled', () => {
  it('flags WinForms-coupled source', () => {
    expect(looksUiCoupled('Imports System.Windows.Forms')).toBe(true)
    expect(looksUiCoupled('Public Class Form1\n  Inherits Form')).toBe(true)
    expect(looksUiCoupled('Private Sub Button1_Click() Handles Button1.Click')).toBe(true)
    expect(looksUiCoupled('Dim txt As TextBox')).toBe(true)
    expect(looksUiCoupled('MsgBox("hi")')).toBe(true)
  })

  it('passes clean business-logic source', () => {
    expect(
      looksUiCoupled(
        'Public Class OrderProcessor\n  Public Function CalculateTotal() As Decimal\n  End Function\nEnd Class',
      ),
    ).toBe(false)
  })
})
