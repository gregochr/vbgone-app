import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Step2Analysis } from './Step2Analysis'
import type { WizardState } from './WizardShell'
import * as api from '../../api/migrateApi'

vi.mock('../../api/migrateApi', async () => {
  const actual = await vi.importActual('../../api/migrateApi')
  return { ...actual, analyse: vi.fn() }
})

const baseState: WizardState = {
  filename: 'Test.vb',
  content: 'Public Class Test\nEnd Class',
  analysis: null,
  interfaceResult: null,
  tests: null,
  stubResult: null,
  redBuild: null,
  implementResult: null,
  greenBuild: null,
  prResult: null,
}

const mockAnalysis: api.AnalysisResult = {
  sessionId: 'session-1',
  classes: [
    {
      name: 'ArithmeticOperations',
      methods: ['Add', 'Subtract'],
      dependencies: [],
      complexity: 'LOW',
    },
  ],
  suggestedMigrationOrder: ['ArithmeticOperations'],
  summary: 'One class found with 2 methods.',
}

describe('Step2Analysis', () => {
  it('renders correctly with analysis data already in state', () => {
    const stateWithAnalysis = { ...baseState, analysis: mockAnalysis }
    render(<Step2Analysis state={stateWithAnalysis} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('Analysis Complete')).toBeInTheDocument()
  })

  it('displays mocked API response data', () => {
    const stateWithAnalysis = { ...baseState, analysis: mockAnalysis }
    render(<Step2Analysis state={stateWithAnalysis} update={vi.fn()} onReady={vi.fn()} />)
    // Class name appears in both heading and migration order
    expect(screen.getAllByText('ArithmeticOperations')).toHaveLength(2)
    expect(screen.getByText('One class found with 2 methods.')).toBeInTheDocument()
    expect(screen.getByText('LOW')).toBeInTheDocument()
    expect(screen.getByText('Add')).toBeInTheDocument()
    expect(screen.getByText('Subtract')).toBeInTheDocument()
    expect(screen.getByText('Migration Order')).toBeInTheDocument()
  })

  it('shows confirm dialog before making API call', () => {
    render(<Step2Analysis state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('Analysing VB.NET Source')).toBeInTheDocument()
    expect(screen.getAllByText(/claude-sonnet-4-6/).length).toBeGreaterThan(0)
    expect(screen.getByText('Continue')).toBeInTheDocument()
  })

  it('shows loading state after clicking Continue', async () => {
    const user = userEvent.setup()
    vi.mocked(api.analyse).mockReturnValue(new Promise(() => {})) // never resolves
    render(<Step2Analysis state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    await user.click(screen.getByText('Continue'))
    expect(screen.getByText(/Claude is analysing/)).toBeInTheDocument()
  })

  it('shows error state if API call fails', async () => {
    const user = userEvent.setup()
    vi.mocked(api.analyse).mockRejectedValue(new Error('Network error'))
    render(<Step2Analysis state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    await user.click(screen.getByText('Continue'))
    await waitFor(() => {
      expect(screen.getByText('Analysis Failed')).toBeInTheDocument()
      expect(screen.getByText('Network error')).toBeInTheDocument()
    })
  })

  it('complexity badge has a tooltip', () => {
    const stateWithAnalysis = { ...baseState, analysis: mockAnalysis }
    render(<Step2Analysis state={stateWithAnalysis} update={vi.fn()} onReady={vi.fn()} />)
    const badge = screen.getByText('LOW')
    expect(badge).toHaveAttribute('title', expect.stringContaining('Complexity'))
  })

  it('migration order has descriptive subtext', () => {
    const stateWithAnalysis = { ...baseState, analysis: mockAnalysis }
    render(<Step2Analysis state={stateWithAnalysis} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText(/Simplest and least dependent first/)).toBeInTheDocument()
  })

  it('calls update and onReady after successful API call', async () => {
    const user = userEvent.setup()
    vi.mocked(api.analyse).mockResolvedValue(mockAnalysis)
    const update = vi.fn()
    const onReady = vi.fn()
    render(<Step2Analysis state={baseState} update={update} onReady={onReady} />)
    await user.click(screen.getByText('Continue'))
    await waitFor(() => {
      expect(update).toHaveBeenCalledWith({ analysis: mockAnalysis })
      expect(onReady).toHaveBeenCalled()
    })
  })
})
