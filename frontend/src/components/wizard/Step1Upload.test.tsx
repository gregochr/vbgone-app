import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Step1Upload } from './Step1Upload'
import type { WizardState } from './WizardShell'

vi.mock('../../api/migrateApi', async () => {
  const actual = await vi.importActual('../../api/migrateApi')
  return {
    ...actual,
    uploadProject: vi.fn().mockResolvedValue({
      sessionId: 'mock-uuid-1234',
      classes: [
        {
          name: 'ValidationHelper',
          methods: ['IsNullOrEmpty'],
          dependencies: [],
          complexity: 'LOW',
        },
      ],
      suggestedMigrationOrder: ['ValidationHelper'],
      dependencyGraph: { ValidationHelper: [] },
      summary: 'One class found.',
    }),
  }
})

const emptyState: WizardState = {
  filename: '',
  content: '',
  analysis: null,
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

describe('Step1Upload', () => {
  it('renders correctly with upload prompt', () => {
    render(<Step1Upload state={emptyState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('Upload legacy VB.NET')).toBeInTheDocument()
    expect(screen.getByText(/drop a .vb file here or click to browse/i)).toBeInTheDocument()
    expect(screen.getByText('Load Demo File (Simple)')).toBeInTheDocument()
  })

  it('displays mocked data after loading demo file', async () => {
    const user = userEvent.setup()
    const update = vi.fn()
    const onReady = vi.fn()

    render(<Step1Upload state={emptyState} update={update} onReady={onReady} />)

    await user.click(screen.getByText('Load Demo File (Simple)'))
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
    expect(screen.queryByText('Load Demo File (Simple)')).not.toBeInTheDocument()
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

describe('Step1Upload — mode toggle', () => {
  it('shows Single File and Project / Solution tabs', () => {
    render(<Step1Upload state={emptyState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('Single File')).toBeInTheDocument()
    expect(screen.getByText('Project / Solution')).toBeInTheDocument()
  })

  it('Single File is active by default', () => {
    render(<Step1Upload state={emptyState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('Single File')).toHaveClass('active')
    expect(screen.getByText('Project / Solution')).not.toHaveClass('active')
  })

  it('shows single file subtitle by default', () => {
    render(<Step1Upload state={emptyState} update={vi.fn()} onReady={vi.fn()} />)
    expect(
      screen.getByText(/Drop a .vb file or a .zip project. VBGone extracts the business logic/),
    ).toBeInTheDocument()
  })

  it('shows project subtitle when project mode selected', async () => {
    const user = userEvent.setup()
    render(<Step1Upload state={emptyState} update={vi.fn()} onReady={vi.fn()} />)

    await user.click(screen.getByText('Project / Solution'))
    expect(screen.getByText(/Upload a .zip containing your VB.NET solution/)).toBeInTheDocument()
    expect(
      screen.queryByText(/Drop a .vb file or a .zip project. VBGone extracts the business logic/),
    ).not.toBeInTheDocument()
  })

  it('switches to project mode on tab click', async () => {
    const user = userEvent.setup()
    render(<Step1Upload state={emptyState} update={vi.fn()} onReady={vi.fn()} />)

    await user.click(screen.getByText('Project / Solution'))
    expect(screen.getByText('Project / Solution')).toHaveClass('active')
    expect(screen.getByText(/drop a .zip file/i)).toBeInTheDocument()
  })

  it('shows Load demo project button in project mode', async () => {
    const user = userEvent.setup()
    render(<Step1Upload state={emptyState} update={vi.fn()} onReady={vi.fn()} />)

    await user.click(screen.getByText('Project / Solution'))
    expect(screen.getByText('Load demo project')).toBeInTheDocument()
  })

  it('project mode has hidden file input that accepts .zip', async () => {
    const user = userEvent.setup()
    render(<Step1Upload state={emptyState} update={vi.fn()} onReady={vi.fn()} />)

    await user.click(screen.getByText('Project / Solution'))
    const inputs = document.querySelectorAll('input[type="file"]')
    const zipInput = Array.from(inputs).find((i) => (i as HTMLInputElement).accept === '.zip')
    expect(zipInput).toBeTruthy()
  })

  it('Load demo project calls onProjectAnalysed', async () => {
    const user = userEvent.setup()
    const onProjectAnalysed = vi.fn()
    render(
      <Step1Upload
        state={emptyState}
        update={vi.fn()}
        onReady={vi.fn()}
        onProjectAnalysed={onProjectAnalysed}
      />,
    )

    await user.click(screen.getByText('Project / Solution'))
    await user.click(screen.getByText('Load demo project'))

    await waitFor(() => {
      expect(onProjectAnalysed).toHaveBeenCalledWith(
        expect.objectContaining({
          sessionId: 'mock-uuid-1234',
          classes: expect.any(Array),
        }),
      )
    })
  })

  it('shows Load Demo File (Complex) button in single file mode', () => {
    render(<Step1Upload state={emptyState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('Load Demo File (Complex)')).toBeInTheDocument()
  })

  it('Load Demo File (Complex) loads OrderProcessor.vb', async () => {
    const user = userEvent.setup()
    const update = vi.fn()
    const onReady = vi.fn()

    render(<Step1Upload state={emptyState} update={update} onReady={onReady} />)

    await user.click(screen.getByText('Load Demo File (Complex)'))
    expect(update).toHaveBeenCalledWith(
      expect.objectContaining({
        filename: 'OrderProcessor.vb',
        content: expect.stringContaining('Public Class OrderProcessor'),
      }),
    )
    expect(onReady).toHaveBeenCalled()
  })

  it('complex demo content includes typical code smells', async () => {
    const user = userEvent.setup()
    const update = vi.fn()

    render(<Step1Upload state={emptyState} update={update} onReady={vi.fn()} />)

    await user.click(screen.getByText('Load Demo File (Complex)'))
    const content = update.mock.calls[0][0].content as string
    expect(content).toContain('On Error Resume Next')
    expect(content).toContain('GoTo')
    expect(content).toContain('SqlConnection')
    expect(content).toContain('MsgBox')
  })

  it('both demo buttons have InfoTip panels', () => {
    render(<Step1Upload state={emptyState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('About the simple demo')).toBeInTheDocument()
    expect(screen.getByText('About the complex demo')).toBeInTheDocument()
  })

  it('complex demo InfoTip lists code smells', async () => {
    const user = userEvent.setup()
    render(<Step1Upload state={emptyState} update={vi.fn()} onReady={vi.fn()} />)

    await user.click(screen.getByText('About the complex demo'))
    expect(screen.getAllByText(/God class/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/Magic numbers/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/Deep nesting/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/SQL injection/).length).toBeGreaterThan(0)
  })

  it('hides mode toggle when file is already loaded', () => {
    const stateWithFile: WizardState = {
      ...emptyState,
      filename: 'Test.vb',
      content: 'Public Class Test\nEnd Class',
    }
    render(<Step1Upload state={stateWithFile} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.queryByText('Single File')).not.toBeInTheDocument()
    expect(screen.queryByText('Project / Solution')).not.toBeInTheDocument()
  })
})
