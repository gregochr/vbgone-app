import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor, act } from '@testing-library/react'
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
  failedTests: [],
}

describe('Step4Tests', () => {
  it('renders correctly with build data already in state', () => {
    const doneState = { ...baseState, tests: mockTests, redBuild: mockBuild }
    render(<Step4Tests state={doneState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('Write the tests first')).toBeInTheDocument()
    expect(screen.getByText(/10 NUnit tests generated/)).toBeInTheDocument()
  })

  it('displays mocked API response data', () => {
    const doneState = { ...baseState, tests: mockTests, redBuild: mockBuild }
    render(<Step4Tests state={doneState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText(/10 \/ 10 tests failing/)).toBeInTheDocument()
    expect(screen.getByText('Generated Tests (10 tests)')).toBeInTheDocument()
    // Code is syntax-highlighted, so the text is split across token spans —
    // match the <pre> element's aggregate content rather than a single text node.
    expect(
      screen.getByText(
        (_, el) =>
          el?.tagName === 'PRE' && el.textContent === '[TestFixture] public class FooTests { }',
      ),
    ).toBeInTheDocument()
  })

  it('code viewers are collapsed by default', () => {
    const doneState = {
      ...baseState,
      tests: mockTests,
      stubResult: { sessionId: 'session-1', className: 'Foo', code: 'stub code' },
      redBuild: mockBuild,
    }
    render(<Step4Tests state={doneState} update={vi.fn()} onReady={vi.fn()} />)
    const testsHeader = screen.getByRole('button', { name: /Generated Tests/ })
    const stubHeader = screen.getByRole('button', { name: /Generated Stub/ })
    expect(testsHeader).toHaveAttribute('aria-expanded', 'false')
    expect(stubHeader).toHaveAttribute('aria-expanded', 'false')
  })

  it('shows expected failure subtitle message', () => {
    const doneState = { ...baseState, tests: mockTests, redBuild: mockBuild }
    render(<Step4Tests state={doneState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText(/All failing against the stub/)).toBeInTheDocument()
    expect(screen.getByText(/RED phase of Red-Green TDD/)).toBeInTheDocument()
  })

  it('shows compilation errors when build status is ERROR', () => {
    const errorBuild: api.BuildResult = {
      sessionId: 'session-1',
      buildStatus: 'ERROR',
      total: 0,
      passed: 0,
      failed: 0,
      errors: ['CS1002: ; expected', 'CS0246: type not found'],
      failedTests: [],
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
    expect(screen.getByText('Write the tests first')).toBeInTheDocument()
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

  it('shows retry button after cancelling confirm dialog', async () => {
    const user = userEvent.setup()
    render(<Step4Tests state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('Continue')).toBeInTheDocument()
    await user.click(screen.getByText('Cancel'))
    expect(screen.getByText('Generate tests & run red build')).toBeInTheDocument()
  })

  it('re-shows confirm dialog after clicking retry button', async () => {
    const user = userEvent.setup()
    render(<Step4Tests state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    await user.click(screen.getByText('Cancel'))
    await user.click(screen.getByText('Generate tests & run red build'))
    expect(screen.getByText('Continue')).toBeInTheDocument()
    expect(screen.getByText(/claude-sonnet-4-6/)).toBeInTheDocument()
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
      expect(update).toHaveBeenCalledWith({
        stubResult: { sessionId: 'session-1', className: 'Foo', code: 'stub' },
      })
      expect(update).toHaveBeenCalledWith({ redBuild: mockBuild })
      expect(onReady).toHaveBeenCalled()
    })
  })

  it('fires onReady on mount when the red build is already in state', () => {
    const onReady = vi.fn()
    render(
      <Step4Tests
        state={{ ...baseState, tests: mockTests, redBuild: mockBuild }}
        update={vi.fn()}
        onReady={onReady}
      />,
    )
    expect(onReady).toHaveBeenCalledTimes(1)
  })

  it('advances the loading message to the stub phase after tests generate', async () => {
    const user = userEvent.setup()
    vi.mocked(api.generateTests).mockResolvedValue(mockTests)
    vi.mocked(api.generateStub).mockReturnValue(new Promise(() => {})) // hold on the stub phase
    render(<Step4Tests state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    await user.click(screen.getByText('Continue'))
    await waitFor(() =>
      expect(screen.getByText(/Generating stub implementation/)).toBeInTheDocument(),
    )
  })

  it('advances the loading message to the build phase after the stub generates', async () => {
    const user = userEvent.setup()
    vi.mocked(api.generateTests).mockResolvedValue(mockTests)
    vi.mocked(api.generateStub).mockResolvedValue({
      sessionId: 'session-1',
      className: 'Foo',
      code: 'stub',
    })
    vi.mocked(api.build).mockReturnValue(new Promise(() => {})) // hold on the build phase
    render(<Step4Tests state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    await user.click(screen.getByText('Continue'))
    await waitFor(() => expect(screen.getByText(/Running dotnet test/)).toBeInTheDocument())
  })

  it('surfaces an error when a later pipeline stage (stub) rejects', async () => {
    const user = userEvent.setup()
    vi.mocked(api.generateTests).mockResolvedValue(mockTests)
    vi.mocked(api.generateStub).mockRejectedValue(new Error('Stub generation failed'))
    render(<Step4Tests state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    await user.click(screen.getByText('Continue'))
    await waitFor(() => expect(screen.getByText('Stub generation failed')).toBeInTheDocument())
  })

  it('does not update state when unmounted mid-pipeline (mounted-ref guard)', async () => {
    const user = userEvent.setup()
    vi.mocked(api.generateTests).mockResolvedValue(mockTests)
    let resolveStub: (v: api.StubResult) => void = () => {}
    vi.mocked(api.generateStub).mockReturnValue(
      new Promise<api.StubResult>((res) => {
        resolveStub = res
      }),
    )
    const update = vi.fn()
    const { unmount } = render(<Step4Tests state={baseState} update={update} onReady={vi.fn()} />)
    await user.click(screen.getByText('Continue'))
    // Wait until the pipeline has applied the tests result and is paused on the stub call.
    await waitFor(() => expect(update).toHaveBeenCalledWith({ tests: mockTests }))
    update.mockClear()

    unmount()
    // Resolve the in-flight stub call after unmount; the guard must skip the onResult write.
    await act(async () => {
      resolveStub({ sessionId: 'session-1', className: 'Foo', code: 'stub' })
      await Promise.resolve()
      await Promise.resolve()
    })
    expect(update).not.toHaveBeenCalled()
  })
})
