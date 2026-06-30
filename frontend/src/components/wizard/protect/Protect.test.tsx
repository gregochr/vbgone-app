import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { WizardConfigProvider } from '../../../config/WizardConfigContext'
import { AppHeader } from '../../AppHeader'
import { WizardShell } from '../WizardShell'
import { Step3Baseline } from './Step3Baseline'
import { Step4BaselineTests } from './Step4BaselineTests'
import type { WizardState } from '../WizardShell'
import type { BaselineResult, BaselineTestsResult } from '../../../api/migrateApi'

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
  baselineResult: null,
  baselineTests: null,
  netFaithful: true,
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

    expect(screen.getByText('VB.NET · behavioural net')).toBeInTheDocument()
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

  it('lists the drifted assertions and offers Edit net & re-run when the net is not faithful', () => {
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
    expect(screen.getByText('Edit net & re-run')).toBeInTheDocument()
    expect(screen.queryByText('Re-run suite')).not.toBeInTheDocument()
    expect(screen.queryByTestId('baseline-closing')).not.toBeInTheDocument()
  })

  it('surfaces compile errors (the WinForms/degraded path) distinctly from drifted assertions', () => {
    const errored: BaselineTestsResult = {
      ...mockBaselineTests,
      netFaithful: false,
      testCount: 0,
      build: {
        ...mockBaselineTests.build,
        buildStatus: 'ERROR',
        total: 0,
        passed: 0,
        failed: 0,
        errors: ['OrderProcessor.vb(8): error BC30002: Type Form is not defined'],
      },
      failures: [],
    }
    const state = { ...baseState, baselineTests: errored, netFaithful: false }
    renderWithConfig(<Step4BaselineTests state={state} update={() => {}} onReady={() => {}} />)

    expect(screen.getByText(/didn't compile against the CLR/)).toBeInTheDocument()
    expect(screen.getByText(/error BC30002/)).toBeInTheDocument()
  })
})
