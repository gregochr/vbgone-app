import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useState } from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { WizardConfigProvider } from '../../../config/WizardConfigContext'
import { Step4BaselineTests } from './Step4BaselineTests'
import * as api from '../../../api/migrateApi'
import type { WizardState } from '../WizardShell'
import type { BaselineTestsResult } from '../../../api/migrateApi'

// The repair loop and the run-only re-run are the two untested async paths. Override just those
// two calls; everything else stays the real (flag-based) mock.
vi.mock('../../../api/migrateApi', async () => {
  const actual =
    await vi.importActual<typeof import('../../../api/migrateApi')>('../../../api/migrateApi')
  return { ...actual, repairBaselineTest: vi.fn(), rerunBaselineTests: vi.fn() }
})

const FAILING = 'ApplyDiscount_UnknownCode_ReturnsSubtotalUnchanged'

const redTests: BaselineTestsResult = {
  sessionId: 'session-1',
  className: 'OrderProcessor',
  testClassName: 'OrderProcessorBaselineTests',
  code: `[TestClass] public class OrderProcessorBaselineTests { [TestMethod] public void ${FAILING}() {} }`,
  testCount: 43,
  netFaithful: false,
  build: {
    sessionId: 'session-1',
    buildStatus: 'RED',
    total: 43,
    passed: 42,
    failed: 1,
    errors: [],
    failedTests: [FAILING],
  },
  failures: [{ name: FAILING, message: 'Expected: 100 but was: 90' }],
}

const greenAfterRerun: BaselineTestsResult = {
  ...redTests,
  netFaithful: true,
  build: {
    ...redTests.build,
    buildStatus: 'GREEN',
    total: 43,
    passed: 43,
    failed: 0,
    failedTests: [],
  },
  failures: [],
}

const baseState: WizardState = {
  filename: 'OrderProcessor.vb',
  content: '',
  analysis: {
    sessionId: 'session-1',
    classes: [
      { name: 'OrderProcessor', methods: ['ApplyDiscount'], dependencies: [], complexity: 'LOW' },
    ],
    suggestedMigrationOrder: ['OrderProcessor'],
    summary: '',
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
  baselineTests: redTests,
  netFaithful: false,
  netted: [],
  assuredGreen: [],
  fromQueue: false,
}

function Harness() {
  const [s, setS] = useState<WizardState>(baseState)
  return (
    <Step4BaselineTests
      state={s}
      update={(p) => setS((prev) => ({ ...prev, ...p }))}
      onReady={() => {}}
    />
  )
}

const renderHarness = () =>
  render(
    <WizardConfigProvider>
      <Harness />
    </WizardConfigProvider>,
  )

describe('Step4BaselineTests — error + re-run paths', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('surfaces the error view when the auto-repair loop throws', async () => {
    const user = userEvent.setup()
    vi.mocked(api.repairBaselineTest).mockRejectedValue(new Error('Repair service unavailable'))
    renderHarness()

    // Genuine red → auto-repair is offered.
    await user.click(screen.getByText('Auto-repair · up to 3 attempts'))

    // The loop's catch surfaces the message in the terminal error view.
    await waitFor(() => expect(screen.getByText('Repair service unavailable')).toBeInTheDocument())
    // It's the error view, not a repaired/quarantined outcome.
    expect(screen.queryByTestId('repair-succeeded')).not.toBeInTheDocument()
    expect(screen.queryByTestId('repair-quarantined')).not.toBeInTheDocument()
  })

  it('re-runs the (edited) suite via the run-only endpoint and reports green', async () => {
    const user = userEvent.setup()
    vi.mocked(api.rerunBaselineTests).mockResolvedValue(greenAfterRerun)
    renderHarness()

    // The manual fallback on a red net re-runs the suite against the untouched VB.
    await user.click(screen.getByText('Edit manually & re-run'))

    await waitFor(() =>
      expect(api.rerunBaselineTests).toHaveBeenCalledWith(
        'session-1',
        'OrderProcessor',
        redTests.code,
      ),
    )
    // A green re-run flips the net to the assured/closing state.
    await waitFor(() => expect(screen.getByTestId('baseline-closing')).toBeInTheDocument())
  })

  it('surfaces the error view when a manual re-run fails', async () => {
    const user = userEvent.setup()
    vi.mocked(api.rerunBaselineTests).mockRejectedValue(new Error('Re-run failed'))
    renderHarness()

    await user.click(screen.getByText('Edit manually & re-run'))

    await waitFor(() => expect(screen.getByText('Re-run failed')).toBeInTheDocument())
  })
})
