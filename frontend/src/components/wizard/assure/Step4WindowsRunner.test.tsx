import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useState } from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Step4BaselineTests } from './Step4BaselineTests'
import * as api from '../../../api/migrateApi'
import type { WizardState } from '../WizardShell'
import type { BaselineTestsResult } from '../../../api/migrateApi'

// Force the Windows runner via the config, so runSuite takes the async job path.
vi.mock('../../../config/WizardConfigContext', () => ({
  useWizardConfig: () => ({
    provider: 'anthropic',
    modelOverrides: {},
    engineParams: {
      provider: 'anthropic',
      targetLanguage: 'csharp',
      modelOverrides: {},
      mode: 'assure',
      runner: 'windows',
    },
  }),
}))

// Override just the two baseline entry points; everything else stays the real mock.
vi.mock('../../../api/migrateApi', async () => {
  const actual =
    await vi.importActual<typeof import('../../../api/migrateApi')>('../../../api/migrateApi')
  return { ...actual, startBaselineTestsJob: vi.fn(), runBaselineTests: vi.fn() }
})

const green: BaselineTestsResult = {
  sessionId: 'session-1',
  className: 'OrderProcessor',
  testClassName: 'OrderProcessorBaselineTests',
  code: '[TestClass] public class OrderProcessorBaselineTests {}',
  testCount: 7,
  netFaithful: true,
  build: {
    sessionId: 'session-1',
    buildStatus: 'GREEN',
    total: 7,
    passed: 7,
    failed: 0,
    errors: [],
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
  baselineTests: null,
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

describe('Step4BaselineTests — Windows runner path', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('routes to the async job (not the sync endpoint) when the runner is Windows', async () => {
    const user = userEvent.setup()
    vi.mocked(api.startBaselineTestsJob).mockResolvedValue({
      jobId: 'job-1',
      state: 'DONE',
      result: green,
      error: null,
    })

    render(<Harness />)
    await user.click(screen.getByRole('button', { name: 'Continue' }))

    await waitFor(() => expect(api.startBaselineTestsJob).toHaveBeenCalledTimes(1))
    expect(api.startBaselineTestsJob).toHaveBeenCalledWith(
      'session-1',
      'OrderProcessor',
      expect.objectContaining({ runner: 'windows' }),
    )
    // The sync endpoint must not be used in Windows mode.
    expect(api.runBaselineTests).not.toHaveBeenCalled()
  })
})
