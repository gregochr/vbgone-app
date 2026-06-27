import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Step6PR } from './Step6PR'
import type { WizardState } from './WizardShell'
import type { ProjectMode } from './WizardShell'
import * as api from '../../api/migrateApi'

vi.mock('../../api/migrateApi', async () => {
  const actual = await vi.importActual('../../api/migrateApi')
  return { ...actual, raisePR: vi.fn() }
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
}

const mockPR: api.PullRequestResult = {
  sessionId: 'session-1',
  prUrl: 'https://github.com/chrisgregory/vbgone-output/pull/1',
  branchName: 'migrate/foo',
  filesCommitted: ['Foo/IFoo.cs', 'Foo/Foo.cs', 'Foo.Tests/FooTests.cs'],
}

describe('Step6PR — single file mode', () => {
  it('renders correctly with PR data already in state', () => {
    const doneState = { ...baseState, prResult: mockPR }
    render(<Step6PR state={doneState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByRole('button', { name: /PR Raised/ })).toBeInTheDocument()
    expect(screen.getByText(/Migration complete/)).toBeInTheDocument()
  })

  it('shows green PR Raised button on success', () => {
    const doneState = { ...baseState, prResult: mockPR }
    render(<Step6PR state={doneState} update={vi.fn()} onReady={vi.fn()} />)
    const btn = screen.getByRole('button', { name: /PR Raised/ })
    expect(btn).toBeDisabled()
    expect(btn).toHaveClass('btn-pr-success')
  })

  it('displays mocked API response data', () => {
    const doneState = { ...baseState, prResult: mockPR }
    render(<Step6PR state={doneState} update={vi.fn()} onReady={vi.fn()} />)
    const link = screen.getByRole('link')
    expect(link).toHaveAttribute('href', mockPR.prUrl)
    expect(screen.getByText('migrate/foo')).toBeInTheDocument()
    expect(screen.getByText('Foo/IFoo.cs')).toBeInTheDocument()
    expect(screen.getByText('Foo/Foo.cs')).toBeInTheDocument()
    expect(screen.getByText('Foo.Tests/FooTests.cs')).toBeInTheDocument()
  })

  it('shows confirm dialog before raising PR', () => {
    render(<Step6PR state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('Ship it')).toBeInTheDocument()
    expect(screen.getByText('Continue')).toBeInTheDocument()
    expect(api.raisePR).not.toHaveBeenCalled()
  })

  it('shows loading state after confirming', async () => {
    const user = userEvent.setup()
    vi.mocked(api.raisePR).mockReturnValue(new Promise(() => {}))
    render(<Step6PR state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    await user.click(screen.getByText('Continue'))
    expect(screen.getByText('Ship it')).toBeInTheDocument()
    expect(screen.getByText(/Committing files and opening the Pull Request/)).toBeInTheDocument()
  })

  it('shows error state if API call fails', async () => {
    const user = userEvent.setup()
    vi.mocked(api.raisePR).mockRejectedValue(new Error('Auth failed'))
    render(<Step6PR state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    await user.click(screen.getByText('Continue'))
    await waitFor(() => {
      expect(screen.getByText('Ship it')).toBeInTheDocument()
      expect(screen.getByText('Auth failed')).toBeInTheDocument()
    })
  })

  it('calls update and onReady after successful API call', async () => {
    const user = userEvent.setup()
    vi.mocked(api.raisePR).mockResolvedValue(mockPR)
    const update = vi.fn()
    const onReady = vi.fn()
    render(<Step6PR state={baseState} update={update} onReady={onReady} />)
    await user.click(screen.getByText('Continue'))
    await waitFor(() => {
      expect(update).toHaveBeenCalledWith({ prResult: mockPR })
      expect(onReady).toHaveBeenCalled()
    })
  })
})

const multiClassState: WizardState = {
  ...baseState,
  analysis: {
    sessionId: 'session-1',
    classes: [
      { name: 'Alpha', methods: ['DoA'], dependencies: [], complexity: 'LOW' },
      { name: 'Beta', methods: ['DoB'], dependencies: ['Alpha'], complexity: 'MEDIUM' },
    ],
    suggestedMigrationOrder: ['Alpha', 'Beta'],
    summary: 'Two classes',
  },
  currentClassIndex: 1,
  completedClasses: [
    {
      className: 'Alpha',
      interfaceResult: {
        sessionId: 'session-1',
        className: 'Alpha',
        interfaceName: 'IAlpha',
        code: 'public interface IAlpha {}',
      },
      tests: {
        sessionId: 'session-1',
        className: 'Alpha',
        testClassName: 'AlphaTests',
        code: '[TestFixture] public class AlphaTests {}',
        testCount: 8,
      },
      stubResult: { sessionId: 'session-1', className: 'Alpha', code: 'stub' },
      implementResult: {
        sessionId: 'session-1',
        className: 'Alpha',
        code: 'public class Alpha : IAlpha {}',
        mode: 'CLAUDE',
      },
    },
    {
      className: 'Beta',
      interfaceResult: {
        sessionId: 'session-1',
        className: 'Beta',
        interfaceName: 'IBeta',
        code: 'public interface IBeta {}',
      },
      tests: {
        sessionId: 'session-1',
        className: 'Beta',
        testClassName: 'BetaTests',
        code: '[TestFixture] public class BetaTests {}',
        testCount: 5,
      },
      stubResult: { sessionId: 'session-1', className: 'Beta', code: 'stub' },
      implementResult: {
        sessionId: 'session-1',
        className: 'Beta',
        code: 'public class Beta : IBeta {}',
        mode: 'CLAUDE',
      },
    },
  ],
}

describe('Step6PR — multi-class mode', () => {
  beforeEach(() => {
    vi.mocked(api.raisePR).mockClear()
  })

  it('shows confirm dialog with completed classes summary', () => {
    render(<Step6PR state={multiClassState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('Ship it')).toBeInTheDocument()
    expect(screen.getByText(/All 2 classes migrated successfully/)).toBeInTheDocument()
    expect(screen.getByText('Alpha')).toBeInTheDocument()
    expect(screen.getByText('Beta')).toBeInTheDocument()
    expect(screen.getByText(/IAlpha/)).toBeInTheDocument()
    expect(screen.getByText(/IBeta/)).toBeInTheDocument()
  })

  it('does NOT auto-fire raisePR before confirm', () => {
    render(<Step6PR state={multiClassState} update={vi.fn()} onReady={vi.fn()} />)
    expect(api.raisePR).not.toHaveBeenCalled()
  })

  it('raises PR after clicking Continue on confirm dialog', async () => {
    const user = userEvent.setup()
    vi.mocked(api.raisePR).mockResolvedValue(mockPR)
    const update = vi.fn()
    const onReady = vi.fn()
    render(<Step6PR state={multiClassState} update={update} onReady={onReady} />)
    await user.click(screen.getByText('Continue'))
    await waitFor(() => {
      expect(api.raisePR).toHaveBeenCalled()
      expect(update).toHaveBeenCalledWith({ prResult: mockPR })
      expect(onReady).toHaveBeenCalled()
    })
  })

  it('shows retry button after cancelling confirm dialog', async () => {
    const user = userEvent.setup()
    render(<Step6PR state={multiClassState} update={vi.fn()} onReady={vi.fn()} />)
    await user.click(screen.getByText('Cancel'))
    expect(screen.getByRole('button', { name: 'Raise Pull Request' })).toBeInTheDocument()
  })

  it('re-shows confirm dialog after clicking retry button', async () => {
    const user = userEvent.setup()
    render(<Step6PR state={multiClassState} update={vi.fn()} onReady={vi.fn()} />)
    await user.click(screen.getByText('Cancel'))
    await user.click(screen.getByRole('button', { name: 'Raise Pull Request' }))
    await waitFor(() => {
      expect(screen.getByText(/All 2 classes migrated successfully/)).toBeInTheDocument()
      expect(screen.getByText('Continue')).toBeInTheDocument()
    })
  })

  it('shows loading state after confirming', async () => {
    const user = userEvent.setup()
    vi.mocked(api.raisePR).mockReturnValue(new Promise(() => {}))
    render(<Step6PR state={multiClassState} update={vi.fn()} onReady={vi.fn()} />)
    await user.click(screen.getByText('Continue'))
    expect(screen.getByText('Ship it')).toBeInTheDocument()
  })

  it('shows "All N classes committed" in success message', async () => {
    vi.mocked(api.raisePR).mockResolvedValue(mockPR)
    const doneState = { ...multiClassState, prResult: mockPR }
    render(<Step6PR state={doneState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText(/All 2 classes committed/)).toBeInTheDocument()
  })
})

describe('Step6PR — project mode', () => {
  beforeEach(() => {
    vi.mocked(api.raisePR).mockClear()
  })

  const projectMode: ProjectMode = {
    sessionId: 'test-session',
    className: 'ValidationHelper',
    classIndex: 1,
    totalClasses: 4,
    onComplete: vi.fn(),
    onBackToQueue: vi.fn(),
  }

  it('shows completion message instead of PR', () => {
    render(
      <Step6PR state={baseState} update={vi.fn()} onReady={vi.fn()} projectMode={projectMode} />,
    )
    expect(screen.getByText('Migration Complete')).toBeInTheDocument()
    expect(screen.getByText('ValidationHelper')).toBeInTheDocument()
  })

  it('shows "Return to queue" message', () => {
    render(
      <Step6PR state={baseState} update={vi.fn()} onReady={vi.fn()} projectMode={projectMode} />,
    )
    expect(screen.getByText(/Return to queue/)).toBeInTheDocument()
  })

  it('shows Back to Queue button', () => {
    render(
      <Step6PR state={baseState} update={vi.fn()} onReady={vi.fn()} projectMode={projectMode} />,
    )
    expect(screen.getByText(/Back to Queue/)).toBeInTheDocument()
  })

  it('clicking Back to Queue calls onBackToQueue', async () => {
    const onBackToQueue = vi.fn()
    const user = userEvent.setup()
    render(
      <Step6PR
        state={baseState}
        update={vi.fn()}
        onReady={vi.fn()}
        projectMode={{ ...projectMode, onBackToQueue }}
      />,
    )
    await user.click(screen.getByText(/Back to Queue/))
    expect(onBackToQueue).toHaveBeenCalled()
  })

  it('does NOT call raisePR API', () => {
    render(
      <Step6PR state={baseState} update={vi.fn()} onReady={vi.fn()} projectMode={projectMode} />,
    )
    expect(api.raisePR).not.toHaveBeenCalled()
  })

  it('does NOT show PR link or PR Raised button', () => {
    render(
      <Step6PR state={baseState} update={vi.fn()} onReady={vi.fn()} projectMode={projectMode} />,
    )
    expect(screen.queryByText('Pull Request Raised')).not.toBeInTheDocument()
    expect(screen.queryByText(/PR Raised/)).not.toBeInTheDocument()
  })
})
