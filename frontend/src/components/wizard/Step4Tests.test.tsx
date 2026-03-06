import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Step4Tests } from './Step4Tests'
import type { WizardState } from './WizardShell'
import * as api from '../../api/migrateApi'

vi.mock('../../api/migrateApi', async () => {
  const actual = await vi.importActual('../../api/migrateApi')
  return {
    ...actual,
    generateTests: vi.fn(),
    generateStub: vi.fn(),
    build: vi.fn(),
  }
})

const baseState: WizardState = {
  filename: 'Test.vb',
  content: '',
  analysis: {
    sessionId: 'session-1',
    classes: [{ name: 'Foo', methods: ['Bar'], dependencies: [], complexity: 'LOW' }],
    suggestedMigrationOrder: ['Foo'],
    summary: 'Test',
  },
  interfaceResult: null,
  tests: null,
  stubResult: null,
  redBuild: null,
  implementResult: null,
  greenBuild: null,
  prResult: null,
}

const mockTests: api.TestsResult = {
  sessionId: 'session-1',
  className: 'Foo',
  testClassName: 'FooTests',
  code: '[TestFixture] public class FooTests { }',
  testCount: 10,
}

const mockBuild: api.BuildResult = {
  sessionId: 'session-1',
  buildStatus: 'RED',
  total: 10,
  passed: 0,
  failed: 10,
  errors: [],
}

describe('Step4Tests', () => {
  it('renders correctly with build data already in state', () => {
    const doneState = { ...baseState, tests: mockTests, redBuild: mockBuild }
    render(<Step4Tests state={doneState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('Tests + Red Build')).toBeInTheDocument()
    expect(screen.getByText(/10 NUnit tests generated/)).toBeInTheDocument()
  })

  it('displays mocked API response data', () => {
    const doneState = { ...baseState, tests: mockTests, redBuild: mockBuild }
    render(<Step4Tests state={doneState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText(/10 \/ 10 tests failing/)).toBeInTheDocument()
    expect(screen.getByText('Generated Tests')).toBeInTheDocument()
    expect(screen.getByText('[TestFixture] public class FooTests { }')).toBeInTheDocument()
  })

  it('shows expected failure subtitle message', () => {
    const doneState = { ...baseState, tests: mockTests, redBuild: mockBuild }
    render(<Step4Tests state={doneState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText(/All failing against the stub/)).toBeInTheDocument()
    expect(screen.getByText(/exactly what we expect/)).toBeInTheDocument()
  })

  it('shows compilation errors when build status is ERROR', () => {
    const errorBuild: api.BuildResult = {
      sessionId: 'session-1',
      buildStatus: 'ERROR',
      total: 0,
      passed: 0,
      failed: 0,
      errors: ['CS1002: ; expected', 'CS0246: type not found'],
    }
    const errorState = { ...baseState, tests: mockTests, redBuild: errorBuild }
    render(<Step4Tests state={errorState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText(/Build error/)).toBeInTheDocument()
    expect(screen.getByText(/did not compile/)).toBeInTheDocument()
    expect(screen.getByText('CS1002: ; expected')).toBeInTheDocument()
    expect(screen.getByText('CS0246: type not found')).toBeInTheDocument()
  })

  it('shows confirm dialog before making API calls', () => {
    render(<Step4Tests state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('Tests + Red Build')).toBeInTheDocument()
    expect(screen.getByText(/claude-sonnet-4-6/)).toBeInTheDocument()
    expect(screen.getByText(/claude-haiku-4-5/)).toBeInTheDocument()
    expect(screen.getByText('Continue')).toBeInTheDocument()
  })

  it('shows loading state after clicking Continue', async () => {
    const user = userEvent.setup()
    vi.mocked(api.generateTests).mockReturnValue(new Promise(() => {}))
    render(<Step4Tests state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    await user.click(screen.getByText('Continue'))
    expect(screen.getByText(/Generating NUnit tests for Foo/)).toBeInTheDocument()
  })

  it('shows error state if API call fails', async () => {
    const user = userEvent.setup()
    vi.mocked(api.generateTests).mockRejectedValue(new Error('Generation failed'))
    render(<Step4Tests state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    await user.click(screen.getByText('Continue'))
    await waitFor(() => {
      expect(screen.getByText('Generation failed')).toBeInTheDocument()
    })
  })

  it('calls update and onReady after all phases complete', async () => {
    const user = userEvent.setup()
    vi.mocked(api.generateTests).mockResolvedValue(mockTests)
    vi.mocked(api.generateStub).mockResolvedValue({
      sessionId: 'session-1',
      className: 'Foo',
      code: 'stub',
    })
    vi.mocked(api.build).mockResolvedValue(mockBuild)

    const update = vi.fn()
    const onReady = vi.fn()
    render(<Step4Tests state={baseState} update={update} onReady={onReady} />)
    await user.click(screen.getByText('Continue'))
    await waitFor(() => {
      expect(update).toHaveBeenCalledWith({ tests: mockTests })
      expect(update).toHaveBeenCalledWith({ redBuild: mockBuild })
      expect(onReady).toHaveBeenCalled()
    })
  })
})
