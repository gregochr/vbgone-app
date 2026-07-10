import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Step5Implement } from './Step5Implement'
import type { WizardState } from './WizardShell'
import * as api from '../../api/migrateApi'

vi.mock('../../api/migrateApi', async () => {
  const actual = await vi.importActual('../../api/migrateApi')
  return {
    ...actual,
    implement: vi.fn(),
    buildAfterImplement: vi.fn(),
    retryImplement: vi.fn(),
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

const mockImpl: api.ImplementResult = {
  sessionId: 'session-1',
  className: 'Foo',
  code: 'public class Foo : IFoo { int Bar() => 1; }',
  mode: 'CLAUDE',
}

const mockGreenBuild: api.BuildResult = {
  sessionId: 'session-1',
  buildStatus: 'GREEN',
  total: 10,
  passed: 10,
  failed: 0,
  errors: [],
  failedTests: [],
}

describe('Step5Implement', () => {
  it('renders with Claude Implements pre-selected', () => {
    render(<Step5Implement state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('Choose Implementation')).toBeInTheDocument()
    expect(screen.getByText('Claude implements')).toBeInTheDocument()
    expect(screen.getByText('Manual (stub)')).toBeInTheDocument()
    // Claude card should be pre-selected
    const claudeCard = screen.getByText('Claude implements').closest('.impl-choice')
    expect(claudeCard).toHaveClass('selected')
  })

  it('renders a real middot glyph in impl-choice-meta, not a raw \\u00B7 escape (regression)', () => {
    render(<Step5Implement state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    // Both impl-choice-meta lines must render a genuine "·"; a bare · in JSX
    // text renders as six literal characters. Build the escape at runtime so this
    // assertion itself contains no escape sequence to normalise.
    const rawEscape = String.fromCharCode(92) + 'u00B7' // the 6 chars: backslash u 0 0 B 7
    expect(document.body.textContent).not.toContain(rawEscape)
    expect(screen.getByText('no API call · you own every line')).toBeInTheDocument()
  })

  it('calls onReady on mount when implementation is already in state', () => {
    const doneState = { ...baseState, implementResult: mockImpl, greenBuild: mockGreenBuild }
    const onReady = vi.fn()
    render(<Step5Implement state={doneState} update={vi.fn()} onReady={onReady} />)
    expect(onReady).toHaveBeenCalled()
  })

  it('displays mocked API response data after completion', () => {
    const doneState = { ...baseState, implementResult: mockImpl, greenBuild: mockGreenBuild }
    render(<Step5Implement state={doneState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('Make the tests pass')).toBeInTheDocument()
    expect(screen.getByText('Implementation')).toBeInTheDocument()
    expect(screen.getByText(/Mode:.*AI/)).toBeInTheDocument()
    expect(
      screen.getByText(/10 \/ 10 tests passing — this is the GREEN phase of Red-Green TDD/),
    ).toBeInTheDocument()
    // Code is syntax-highlighted, so the text is split across token spans —
    // match the <pre> element's aggregate content rather than a single text node.
    expect(
      screen.getByText((_, el) => el?.tagName === 'PRE' && el.textContent === mockImpl.code),
    ).toBeInTheDocument()
  })

  it('selecting STUB shows confirm then calls implement with STUB mode', async () => {
    const user = userEvent.setup()
    const mockStubImpl: api.ImplementResult = {
      sessionId: 'session-1',
      className: 'Foo',
      code: 'public class Foo : IFoo { throw new NotImplementedException(); }',
      mode: 'STUB',
    }
    const mockRedBuild: api.BuildResult = {
      sessionId: 'session-1',
      buildStatus: 'RED',
      total: 10,
      passed: 0,
      failed: 10,
      errors: [],
      failedTests: [],
    }
    vi.mocked(api.implement).mockResolvedValue(mockStubImpl)
    vi.mocked(api.buildAfterImplement).mockResolvedValue(mockRedBuild)

    const update = vi.fn()
    render(<Step5Implement state={baseState} update={update} onReady={vi.fn()} />)

    await user.click(screen.getByText('Manual (stub)'))
    expect(screen.getByText(/You have chosen to implement the C# yourself/)).toBeInTheDocument()
    await user.click(screen.getByText('Continue'))
    await waitFor(() => {
      expect(update).toHaveBeenCalledWith({ implementResult: mockStubImpl })
      expect(update).toHaveBeenCalledWith({ greenBuild: mockRedBuild })
    })
  })

  it('displays red build result for STUB mode', () => {
    const stubState = {
      ...baseState,
      implementResult: {
        sessionId: 'session-1',
        className: 'Foo',
        code: 'public class Foo : IFoo { throw new NotImplementedException(); }',
        mode: 'STUB' as const,
      },
      greenBuild: {
        sessionId: 'session-1',
        buildStatus: 'RED' as const,
        total: 10,
        passed: 0,
        failed: 10,
        errors: [],
        failedTests: [],
      },
    }
    render(<Step5Implement state={stubState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText(/10 \/ 10 tests failing/)).toBeInTheDocument()
    expect(screen.getByText(/review the implementation/)).toBeInTheDocument()
    expect(screen.getByText(/Manual/)).toBeInTheDocument()
  })

  it('displays partial failure with review message for CLAUDE mode', () => {
    const partialState = {
      ...baseState,
      implementResult: {
        sessionId: 'session-1',
        className: 'Foo',
        code: 'public class Foo : IFoo { }',
        mode: 'CLAUDE' as const,
      },
      greenBuild: {
        sessionId: 'session-1',
        buildStatus: 'RED' as const,
        total: 10,
        passed: 8,
        failed: 2,
        errors: [],
        failedTests: [],
      },
    }
    render(<Step5Implement state={partialState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText(/2 \/ 10 tests failing/)).toBeInTheDocument()
    expect(screen.getByText(/review the implementation/)).toBeInTheDocument()
  })

  it('displays build error when build status is ERROR', () => {
    const errorState = {
      ...baseState,
      implementResult: {
        sessionId: 'session-1',
        className: 'Foo',
        code: 'public class Foo : IFoo { }',
        mode: 'CLAUDE' as const,
      },
      greenBuild: {
        sessionId: 'session-1',
        buildStatus: 'ERROR' as const,
        total: 0,
        passed: 0,
        failed: 0,
        errors: ['CS0246: type not found'],
        failedTests: [],
      },
    }
    render(<Step5Implement state={errorState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText(/Build error/)).toBeInTheDocument()
    expect(screen.getByText(/did not compile/)).toBeInTheDocument()
  })

  it('shows loading state while API call is in progress', async () => {
    const user = userEvent.setup()
    vi.mocked(api.implement).mockReturnValue(new Promise(() => {})) // never resolves

    render(<Step5Implement state={baseState} update={vi.fn()} onReady={vi.fn()} />)

    await user.click(screen.getByText('Claude implements'))
    await user.click(screen.getByText('Continue'))
    expect(screen.getByText(/Claude is implementing/)).toBeInTheDocument()
  })

  it('shows error state if API call fails', async () => {
    const user = userEvent.setup()
    vi.mocked(api.implement).mockRejectedValue(new Error('Server error'))

    render(<Step5Implement state={baseState} update={vi.fn()} onReady={vi.fn()} />)

    await user.click(screen.getByText('Claude implements'))
    await user.click(screen.getByText('Continue'))
    await waitFor(() => {
      expect(screen.getByText('Make the tests pass')).toBeInTheDocument()
      expect(screen.getByText('Server error')).toBeInTheDocument()
    })
  })

  it('shows retry button when CLAUDE build is RED with failing tests', () => {
    const redState = {
      ...baseState,
      implementResult: {
        sessionId: 'session-1',
        className: 'Foo',
        code: 'public class Foo : IFoo { }',
        mode: 'CLAUDE' as const,
      },
      greenBuild: {
        sessionId: 'session-1',
        buildStatus: 'RED' as const,
        total: 10,
        passed: 8,
        failed: 2,
        errors: [],
        failedTests: ['Add_ReturnsSum', 'Subtract_ReturnsDiff'],
      },
    }
    render(<Step5Implement state={redState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText(/Retry with Claude/)).toBeInTheDocument()
    expect(screen.getByText('Add_ReturnsSum')).toBeInTheDocument()
    expect(screen.getByText('Subtract_ReturnsDiff')).toBeInTheDocument()
  })

  it('implementation code viewer is collapsed by default', () => {
    const doneState = { ...baseState, implementResult: mockImpl, greenBuild: mockGreenBuild }
    render(<Step5Implement state={doneState} update={vi.fn()} onReady={vi.fn()} />)
    const header = screen.getByRole('button', { name: /Implementation/ })
    expect(header).toHaveAttribute('aria-expanded', 'false')
  })

  it('retry button shows confirm dialog with prompt engineering explanation', async () => {
    const user = userEvent.setup()
    const redState = {
      ...baseState,
      implementResult: {
        sessionId: 'session-1',
        className: 'Foo',
        code: 'public class Foo : IFoo { }',
        mode: 'CLAUDE' as const,
      },
      greenBuild: {
        sessionId: 'session-1',
        buildStatus: 'RED' as const,
        total: 10,
        passed: 8,
        failed: 2,
        errors: [],
        failedTests: ['Add_ReturnsSum', 'Subtract_ReturnsDiff'],
      },
    }
    render(<Step5Implement state={redState} update={vi.fn()} onReady={vi.fn()} />)

    await user.click(screen.getByText(/Retry with Claude/))

    // Confirm dialog should appear with prompt engineering explanation
    expect(screen.getByText(/Claude Sonnet/)).toBeInTheDocument()
    expect(screen.getByText(/securely over HTTPS/)).toBeInTheDocument()
    expect(screen.getByText(/2 failing/)).toBeInTheDocument()
    expect(screen.getByText(/extracted from the test suite/)).toBeInTheDocument()
    expect(screen.getByText(/Prompt caching is enabled/)).toBeInTheDocument()
    expect(screen.getByText('Continue')).toBeInTheDocument()
  })

  it('retry confirm dialog calls retryImplement and build on confirm', async () => {
    const user = userEvent.setup()
    const retryImpl: api.ImplementResult = {
      sessionId: 'session-1',
      className: 'Foo',
      code: 'public class Foo : IFoo { int Bar() => 1; }',
      mode: 'CLAUDE',
    }
    vi.mocked(api.retryImplement).mockResolvedValue(retryImpl)
    vi.mocked(api.build).mockResolvedValue(mockGreenBuild)

    const redState = {
      ...baseState,
      implementResult: {
        sessionId: 'session-1',
        className: 'Foo',
        code: 'public class Foo : IFoo { }',
        mode: 'CLAUDE' as const,
      },
      greenBuild: {
        sessionId: 'session-1',
        buildStatus: 'RED' as const,
        total: 10,
        passed: 8,
        failed: 2,
        errors: [],
        failedTests: ['Add_ReturnsSum'],
      },
    }
    const update = vi.fn()
    const onReady = vi.fn()
    render(<Step5Implement state={redState} update={update} onReady={onReady} />)

    await user.click(screen.getByText(/Retry with Claude/))
    await user.click(screen.getByText('Continue'))

    await waitFor(() => {
      expect(api.retryImplement).toHaveBeenCalledWith(
        'session-1',
        'Foo',
        ['Add_ReturnsSum'],
        2,
        expect.anything(),
      )
      expect(api.build).toHaveBeenCalledWith('session-1')
      expect(update).toHaveBeenCalledWith({ implementResult: retryImpl })
      expect(update).toHaveBeenCalledWith({ greenBuild: mockGreenBuild })
      expect(onReady).toHaveBeenCalled()
    })
  })

  it('shows spinner during retry after confirm', async () => {
    const user = userEvent.setup()
    vi.mocked(api.retryImplement).mockReturnValue(new Promise(() => {})) // never resolves

    const redState = {
      ...baseState,
      implementResult: {
        sessionId: 'session-1',
        className: 'Foo',
        code: 'public class Foo : IFoo { }',
        mode: 'CLAUDE' as const,
      },
      greenBuild: {
        sessionId: 'session-1',
        buildStatus: 'RED' as const,
        total: 10,
        passed: 8,
        failed: 2,
        errors: [],
        failedTests: ['Add_ReturnsSum'],
      },
    }
    render(<Step5Implement state={redState} update={vi.fn()} onReady={vi.fn()} />)

    await user.click(screen.getByText(/Retry with Claude/))
    await user.click(screen.getByText('Continue'))

    expect(screen.getByText(/Claude is retrying implementation/)).toBeInTheDocument()
  })

  it('does not show retry button for STUB mode RED build', () => {
    const stubRedState = {
      ...baseState,
      implementResult: {
        sessionId: 'session-1',
        className: 'Foo',
        code: 'public class Foo : IFoo { throw new NotImplementedException(); }',
        mode: 'STUB' as const,
      },
      greenBuild: {
        sessionId: 'session-1',
        buildStatus: 'RED' as const,
        total: 10,
        passed: 0,
        failed: 10,
        errors: [],
        failedTests: [],
      },
    }
    render(<Step5Implement state={stubRedState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.queryByText(/Retry with Claude/)).not.toBeInTheDocument()
  })

  it('calls update and onReady after successful implementation', async () => {
    const user = userEvent.setup()
    vi.mocked(api.implement).mockResolvedValue(mockImpl)
    vi.mocked(api.buildAfterImplement).mockResolvedValue(mockGreenBuild)

    const update = vi.fn()
    const onReady = vi.fn()
    render(<Step5Implement state={baseState} update={update} onReady={onReady} />)

    await user.click(screen.getByText('Claude implements'))
    await user.click(screen.getByText('Continue'))
    await waitFor(() => {
      expect(update).toHaveBeenCalledWith({ implementResult: mockImpl })
      expect(update).toHaveBeenCalledWith({ greenBuild: mockGreenBuild })
      expect(onReady).toHaveBeenCalled()
    })
  })
})
