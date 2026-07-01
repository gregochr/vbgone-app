import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { WizardConfigProvider } from '../../../config/WizardConfigContext'
import { AppHeader } from '../../AppHeader'
import { WizardShell } from '../WizardShell'
import { Step3Baseline } from './Step3Baseline'
import { Step4BaselineTests } from './Step4BaselineTests'
import { StepReadiness } from './StepReadiness'
import type { WizardState } from '../WizardShell'
import type { BaselineResult, BaselineTestsResult, ReadinessReport } from '../../../api/migrateApi'
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
  readiness: null,
  baselineResult: null,
  baselineTests: null,
  netFaithful: true,
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

    expect(screen.getByText('VB.NET · behavioural baseline')).toBeInTheDocument()
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

    expect(screen.getByText('Behaviour pinned as-is')).toBeInTheDocument()
    expect(screen.getByText('Green means unchanged, not correct.')).toBeInTheDocument()
    expect(screen.getByText('OrderProcessor.dll · public surface')).toBeInTheDocument()
    expect(
      screen.getByText('decimal SplitPerHead(decimal total, int headcount)'),
    ).toBeInTheDocument()
    expect(screen.getByText(/throws DivideByZeroException when headcount = 0/)).toBeInTheDocument()
  })

  it('asks to confirm before pinning when no baseline yet', () => {
    renderWithConfig(<Step3Baseline state={baseState} update={() => {}} onReady={() => {}} />)
    expect(screen.getByText(/characterise the public surface/i)).toBeInTheDocument()
  })
})

describe('Step4BaselineTests', () => {
  it('shows the GREEN banner and closing panel when the net is faithful', () => {
    const state = { ...baseState, baselineTests: mockBaselineTests, netFaithful: true }
    renderWithConfig(<Step4BaselineTests state={state} update={() => {}} onReady={() => {}} />)

    expect(screen.getByTestId('net-banner-green')).toBeInTheDocument()
    expect(screen.getByText(/43 \/ 43 passing/)).toBeInTheDocument()
    expect(screen.getByText('You now have a behavioural baseline')).toBeInTheDocument()
  })

  it('lists the drifted assertions and offers Edit baseline & re-run when not faithful', () => {
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
    // The failing assertion name + message are surfaced — not just the count.
    expect(screen.getByTestId('net-failures')).toBeInTheDocument()
    expect(
      screen.getByText('ApplyDiscount_UnknownCode_ReturnsSubtotalUnchanged'),
    ).toBeInTheDocument()
    expect(screen.getByText('Expected: 100 but was: 90')).toBeInTheDocument()
    // The action makes the intent clear (edit, not just re-run) and the closing panel is hidden.
    expect(screen.getByText('Edit baseline & re-run')).toBeInTheDocument()
    expect(screen.queryByText('Re-run suite')).not.toBeInTheDocument()
    expect(screen.queryByTestId('baseline-closing')).not.toBeInTheDocument()
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

  it('surfaces compile errors (the WinForms/degraded path) distinctly from drifted assertions', () => {
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
    expect(hint).toHaveTextContent(/business-logic surface/)
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
})

describe('StepReadiness (single-file verdict)', () => {
  it('shows a Ready-to-protect verdict and signals ready when the class is net-ready', () => {
    const onReady = vi.fn()
    const state = { ...baseState, filename: 'OrderProcessor.vb', readiness: readyReport }
    renderWithConfig(<StepReadiness state={state} update={() => {}} onReady={onReady} />)

    const card = screen.getByTestId('verdict-card')
    expect(card).toHaveClass('verdict-ready')
    expect(card).toHaveTextContent('Ready to protect')
    expect(card).toHaveTextContent('headless business-logic surface')
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
