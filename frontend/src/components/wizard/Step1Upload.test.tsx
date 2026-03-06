import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Step1Upload } from './Step1Upload'
import type { WizardState } from './WizardShell'

const emptyState: WizardState = {
  filename: '',
  content: '',
  analysis: null,
  interfaceResult: null,
  tests: null,
  stubResult: null,
  redBuild: null,
  implementResult: null,
  greenBuild: null,
  prResult: null,
}

describe('Step1Upload', () => {
  it('renders correctly with upload prompt', () => {
    render(<Step1Upload state={emptyState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('Upload VB.NET Source')).toBeInTheDocument()
    expect(screen.getByText(/drop a .vb file/i)).toBeInTheDocument()
    expect(screen.getByText('Load demo file')).toBeInTheDocument()
  })

  it('displays mocked data after loading demo file', async () => {
    const user = userEvent.setup()
    const update = vi.fn()
    const onReady = vi.fn()

    render(<Step1Upload state={emptyState} update={update} onReady={onReady} />)

    await user.click(screen.getByText('Load demo file'))
    expect(update).toHaveBeenCalledWith(
      expect.objectContaining({
        filename: 'Form1.vb',
        content: expect.stringContaining('Public Class Form1'),
      }),
    )
    expect(onReady).toHaveBeenCalled()
  })

  it('shows file name and preview when file is already loaded', () => {
    const stateWithFile: WizardState = {
      ...emptyState,
      filename: 'Test.vb',
      content: 'Public Class Test\nEnd Class',
    }
    render(<Step1Upload state={stateWithFile} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('Test.vb')).toBeInTheDocument()
    expect(screen.getByText('Preview')).toBeInTheDocument()
    expect(screen.getByText(/Public Class Test/)).toBeInTheDocument()
    expect(screen.queryByText('Load demo file')).not.toBeInTheDocument()
  })

  it('calls onReady on mount when file is already in state', () => {
    const stateWithFile: WizardState = {
      ...emptyState,
      filename: 'Test.vb',
      content: 'Public Class Test\nEnd Class',
    }
    const onReady = vi.fn()
    render(<Step1Upload state={stateWithFile} update={vi.fn()} onReady={onReady} />)
    expect(onReady).toHaveBeenCalled()
  })

  it('does not call onReady on mount when no file in state', () => {
    const onReady = vi.fn()
    render(<Step1Upload state={emptyState} update={vi.fn()} onReady={onReady} />)
    expect(onReady).not.toHaveBeenCalled()
  })

  it('shows "Choose a different file" only when a file is loaded', () => {
    const { rerender } = render(
      <Step1Upload state={emptyState} update={vi.fn()} onReady={vi.fn()} />,
    )
    expect(screen.queryByText('Choose a different file')).not.toBeInTheDocument()

    const stateWithFile: WizardState = {
      ...emptyState,
      filename: 'Test.vb',
      content: 'Public Class Test\nEnd Class',
    }
    rerender(<Step1Upload state={stateWithFile} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('Choose a different file')).toBeInTheDocument()
  })

  it('"Choose a different file" clears all state', async () => {
    const user = userEvent.setup()
    const update = vi.fn()
    const stateWithFile: WizardState = {
      ...emptyState,
      filename: 'Test.vb',
      content: 'Public Class Test\nEnd Class',
    }
    render(<Step1Upload state={stateWithFile} update={update} onReady={vi.fn()} />)

    await user.click(screen.getByText('Choose a different file'))
    expect(update).toHaveBeenCalledWith(
      expect.objectContaining({ filename: '', content: '', analysis: null }),
    )
  })

  it('has a hidden file input that accepts .vb', () => {
    render(<Step1Upload state={emptyState} update={vi.fn()} onReady={vi.fn()} />)
    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    expect(input).toBeTruthy()
    expect(input.accept).toBe('.vb')
    expect(input.hidden).toBe(true)
  })
})
