import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { Step6PR } from './Step6PR'
import type { WizardState } from './WizardShell'
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

describe('Step6PR', () => {
  it('renders correctly with PR data already in state', () => {
    const doneState = { ...baseState, prResult: mockPR }
    render(<Step6PR state={doneState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('Pull Request Raised')).toBeInTheDocument()
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

  it('shows loading state while API call is in progress', () => {
    vi.mocked(api.raisePR).mockReturnValue(new Promise(() => {}))
    render(<Step6PR state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('Raising Pull Request')).toBeInTheDocument()
    expect(screen.getByText(/Committing files and raising PR/)).toBeInTheDocument()
  })

  it('shows error state if API call fails', async () => {
    vi.mocked(api.raisePR).mockRejectedValue(new Error('Auth failed'))
    render(<Step6PR state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    await waitFor(() => {
      expect(screen.getByText('Pull Request Failed')).toBeInTheDocument()
      expect(screen.getByText('Auth failed')).toBeInTheDocument()
    })
  })

  it('calls update and onReady after successful API call', async () => {
    vi.mocked(api.raisePR).mockResolvedValue(mockPR)
    const update = vi.fn()
    const onReady = vi.fn()
    render(<Step6PR state={baseState} update={update} onReady={onReady} />)
    await waitFor(() => {
      expect(update).toHaveBeenCalledWith({ prResult: mockPR })
      expect(onReady).toHaveBeenCalled()
    })
  })
})
