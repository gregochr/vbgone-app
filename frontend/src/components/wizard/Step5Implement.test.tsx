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
}

describe('Step5Implement', () => {
  it('renders correctly with implementation choices', () => {
    render(<Step5Implement state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('Choose Implementation')).toBeInTheDocument()
    expect(screen.getByText('Claude Implements')).toBeInTheDocument()
    expect(screen.getByText('Manual (Stub)')).toBeInTheDocument()
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
    expect(screen.getAllByText('Implementation')).toHaveLength(2)
    expect(screen.getByText(/Mode:.*AI/)).toBeInTheDocument()
    expect(screen.getByText(/10 \/ 10 tests passing/)).toBeInTheDocument()
    expect(screen.getByText(mockImpl.code)).toBeInTheDocument()
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
    }
    vi.mocked(api.implement).mockResolvedValue(mockStubImpl)
    vi.mocked(api.buildAfterImplement).mockResolvedValue(mockRedBuild)

    const update = vi.fn()
    render(<Step5Implement state={baseState} update={update} onReady={vi.fn()} />)

    await user.click(screen.getByText('Manual (Stub)'))
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
      },
    }
    render(<Step5Implement state={stubState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText(/10 \/ 10 tests failing/)).toBeInTheDocument()
    expect(screen.getByText(/Manual/)).toBeInTheDocument()
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

    await user.click(screen.getByText('Claude Implements'))
    await user.click(screen.getByText('Continue'))
    expect(screen.getByText(/Claude is implementing/)).toBeInTheDocument()
  })

  it('shows error state if API call fails', async () => {
    const user = userEvent.setup()
    vi.mocked(api.implement).mockRejectedValue(new Error('Server error'))

    render(<Step5Implement state={baseState} update={vi.fn()} onReady={vi.fn()} />)

    await user.click(screen.getByText('Claude Implements'))
    await user.click(screen.getByText('Continue'))
    await waitFor(() => {
      expect(screen.getByText('Implementation Failed')).toBeInTheDocument()
      expect(screen.getByText('Server error')).toBeInTheDocument()
    })
  })

  it('calls update and onReady after successful implementation', async () => {
    const user = userEvent.setup()
    vi.mocked(api.implement).mockResolvedValue(mockImpl)
    vi.mocked(api.buildAfterImplement).mockResolvedValue(mockGreenBuild)

    const update = vi.fn()
    const onReady = vi.fn()
    render(<Step5Implement state={baseState} update={update} onReady={onReady} />)

    await user.click(screen.getByText('Claude Implements'))
    await user.click(screen.getByText('Continue'))
    await waitFor(() => {
      expect(update).toHaveBeenCalledWith({ implementResult: mockImpl })
      expect(update).toHaveBeenCalledWith({ greenBuild: mockGreenBuild })
      expect(onReady).toHaveBeenCalled()
    })
  })
})
