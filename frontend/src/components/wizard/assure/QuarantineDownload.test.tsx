import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useState } from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { WizardConfigProvider } from '../../../config/WizardConfigContext'
import { Step4BaselineTests } from './Step4BaselineTests'
import * as api from '../../../api/migrateApi'
import type { WizardState } from '../WizardShell'
import type { BaselineTestsResult, RepairAttemptResult } from '../../../api/migrateApi'

// Force the auto-repair loop to exhaust all tiers (→ quarantine), and make the follow-up
// quarantine call go green. Everything else stays the real (flag-based) mock.
vi.mock('../../../api/migrateApi', async () => {
  const actual =
    await vi.importActual<typeof import('../../../api/migrateApi')>('../../../api/migrateApi')
  return { ...actual, repairBaselineTest: vi.fn(), quarantineBaseline: vi.fn() }
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

const nonGreenAttempt = (tier: number): RepairAttemptResult => ({
  tier: tier === 1 ? 'Mechanical' : tier === 3 ? 'Escalation' : 'Reasoning',
  role: tier === 1 ? 'mechanical' : tier === 3 ? 'escalation' : 'reasoning',
  model: 'mock',
  rationale: 'no fixed value matches — the code differs every run',
  diff: [],
  gate: { ok: true, note: '' },
  rerun: { green: false, note: 'still red' },
  tag: tier === 3 ? 'nofix' : 'red',
  code: redTests.code,
  netFaithful: false,
})

const greenAfterQuarantine: BaselineTestsResult = {
  ...redTests,
  code: `[TestClass] public class OrderProcessorBaselineTests { [Ignore("quarantined")] [TestMethod] public void ${FAILING}() {} }`,
  netFaithful: true,
  build: {
    ...redTests.build,
    buildStatus: 'GREEN',
    passed: 42,
    failed: 0,
    total: 42,
    failedTests: [],
  },
  failures: [],
}

const baseState: WizardState = {
  filename: 'LegacyEstate.zip',
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
  fromQueue: true,
}

function Harness() {
  const [s, setS] = useState<WizardState>(baseState)
  return (
    <>
      <div data-testid="assured-green">{(s.assuredGreen ?? []).join(',')}</div>
      <Step4BaselineTests
        state={s}
        update={(p) => setS((prev) => ({ ...prev, ...p }))}
        onReady={() => {}}
        fromQueue
        onAssureNext={() => {}}
        onBackToReadiness={() => {}}
      />
    </>
  )
}

describe('Step4 quarantine → downloadable suite', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(api.repairBaselineTest).mockImplementation((_s, _c, _code, _t, tier) =>
      Promise.resolve(nonGreenAttempt(tier)),
    )
    vi.mocked(api.quarantineBaseline).mockResolvedValue(greenAfterQuarantine)
  })

  it('sets the class aside as green-with-the-passing-tests so it becomes downloadable', async () => {
    const user = userEvent.setup()
    render(
      <WizardConfigProvider>
        <Harness />
      </WizardConfigProvider>,
    )

    await user.click(screen.getByText('Auto-repair · up to 3 attempts'))

    // All three tiers fail → the quarantine card appears.
    expect(
      await screen.findByTestId('repair-quarantined', undefined, { timeout: 8000 }),
    ).toBeInTheDocument()

    // The unrepairable test is set aside server-side (marked [Ignore], re-run), and the class is
    // recorded as green-assured so the readiness report will offer its download.
    await waitFor(() => {
      expect(api.quarantineBaseline).toHaveBeenCalledWith(
        'session-1',
        'OrderProcessor',
        expect.any(String),
        [FAILING],
      )
    })
    await waitFor(() => {
      expect(screen.getByTestId('assured-green')).toHaveTextContent('OrderProcessor')
    })
    // All three repair tiers were attempted before quarantining.
    expect(api.repairBaselineTest).toHaveBeenCalledTimes(3)
  }, 15000)
})
