import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { WizardShell } from './WizardShell'

// Mock all API calls to resolve instantly
vi.mock('../../api/migrateApi', async () => {
  const actual = await vi.importActual('../../api/migrateApi')
  return {
    ...actual,
    analyse: vi.fn().mockImplementation((_filename: string, content: string) => {
      // Return multi-class analysis for "multi" content, single-class otherwise
      if (content === 'multi') {
        return Promise.resolve({
          sessionId: 'test-session',
          classes: [
            { name: 'Alpha', methods: ['DoA'], dependencies: [], complexity: 'LOW' },
            { name: 'Beta', methods: ['DoB'], dependencies: ['Alpha'], complexity: 'MEDIUM' },
          ],
          suggestedMigrationOrder: ['Alpha', 'Beta'],
          summary: 'Two classes found',
        })
      }
      return Promise.resolve({
        sessionId: 'test-session',
        classes: [{ name: 'Foo', methods: ['Bar'], dependencies: [], complexity: 'LOW' }],
        suggestedMigrationOrder: ['Foo'],
        summary: 'Test summary',
      })
    }),
    generateInterface: vi.fn().mockResolvedValue({
      sessionId: 'test-session',
      className: 'Foo',
      interfaceName: 'IFoo',
      code: 'public interface IFoo {}',
    }),
    generateTests: vi.fn().mockResolvedValue({
      sessionId: 'test-session',
      className: 'Foo',
      testClassName: 'FooTests',
      code: '[TestFixture] public class FooTests {}',
      testCount: 10,
    }),
    generateStub: vi.fn().mockResolvedValue({
      sessionId: 'test-session',
      className: 'Foo',
      code: 'public class Foo : IFoo {}',
    }),
    build: vi.fn().mockResolvedValue({
      sessionId: 'test-session',
      buildStatus: 'RED',
      total: 10,
      passed: 0,
      failed: 10,
      errors: [],
      failedTests: [],
    }),
    implement: vi.fn().mockResolvedValue({
      sessionId: 'test-session',
      className: 'Foo',
      code: 'public class Foo : IFoo { int Bar() => 1; }',
      mode: 'CLAUDE',
    }),
    buildAfterImplement: vi.fn().mockResolvedValue({
      sessionId: 'test-session',
      buildStatus: 'GREEN',
      total: 10,
      passed: 10,
      failed: 0,
      errors: [],
      failedTests: [],
    }),
    raisePR: vi.fn().mockResolvedValue({
      sessionId: 'test-session',
      prUrl: 'https://github.com/test/repo/pull/1',
      branchName: 'migrate/foo',
      filesCommitted: ['Foo/IFoo.cs', 'Foo/Foo.cs'],
    }),
    fetchCost: vi.fn().mockResolvedValue({
      sessionId: 'test-session',
      steps: [],
      totalCost: 0.0088,
    }),
  }
})

describe('WizardShell', () => {
  it('renders Step 1 on load', () => {
    render(<WizardShell />)
    expect(screen.getByText('Upload VB.NET Source')).toBeInTheDocument()
  })

  it('renders all 6 step labels', () => {
    render(<WizardShell />)
    expect(screen.getByText('Upload')).toBeInTheDocument()
    expect(screen.getByText('Analysis')).toBeInTheDocument()
    expect(screen.getByText('Interface')).toBeInTheDocument()
    expect(screen.getByText('Tests')).toBeInTheDocument()
    expect(screen.getByText('Implement')).toBeInTheDocument()
    expect(screen.getByText('Raise PR')).toBeInTheDocument()
  })

  it('Back button is disabled on Step 1', () => {
    render(<WizardShell />)
    expect(screen.getByText('Back')).toBeDisabled()
  })

  it('Next button is disabled until step is ready', () => {
    render(<WizardShell />)
    expect(screen.getByText('Next')).toBeDisabled()
  })

  it('shows step 1 as active initially', () => {
    render(<WizardShell />)
    const dots = document.querySelectorAll('.wizard-step-dot')
    expect(dots[0]).toHaveClass('active')
    expect(dots[1]).not.toHaveClass('active')
  })
})

describe('WizardShell project mode banner', () => {
  const projectMode = {
    sessionId: 'test-session',
    className: 'DateHelper',
    classIndex: 2,
    totalClasses: 4,
    onComplete: vi.fn(),
    onBackToQueue: vi.fn(),
  }

  it('shows the project banner in project mode', () => {
    render(<WizardShell projectMode={projectMode} />)
    expect(screen.getByTestId('project-banner')).toBeInTheDocument()
  })

  it('displays class position and name', () => {
    render(<WizardShell projectMode={projectMode} />)
    expect(screen.getByText(/Migrating class 2 of 4/)).toBeInTheDocument()
    expect(screen.getByText('DateHelper')).toBeInTheDocument()
  })

  it('shows Back to Queue button', () => {
    render(<WizardShell projectMode={projectMode} />)
    expect(screen.getByText(/Back to Queue/)).toBeInTheDocument()
  })

  it('Back to Queue calls onBackToQueue', async () => {
    const onBackToQueue = vi.fn()
    const user = userEvent.setup()
    render(<WizardShell projectMode={{ ...projectMode, onBackToQueue }} />)

    await user.click(screen.getByText(/Back to Queue/))
    expect(onBackToQueue).toHaveBeenCalled()
  })

  it('does not show the banner in single file mode', () => {
    render(<WizardShell />)
    expect(screen.queryByTestId('project-banner')).not.toBeInTheDocument()
  })

  it('starts at Interface step in project mode', () => {
    render(<WizardShell projectMode={projectMode} />)
    const dots = document.querySelectorAll('.wizard-step-dot')
    // Step 3 (index 2) should be active
    expect(dots[2]).toHaveClass('active')
  })

  it('Back button is disabled at Interface step in project mode', () => {
    render(<WizardShell projectMode={projectMode} />)
    expect(screen.getByText('Back')).toBeDisabled()
  })

  it('banner text is centred', () => {
    render(<WizardShell projectMode={projectMode} />)
    const bannerText = document.querySelector('.project-banner-text') as HTMLElement
    expect(bannerText).toBeTruthy()
    expect(bannerText.className).toContain('project-banner-text')
  })
})

describe('WizardShell navigation', () => {
  it('advances to Step 2 after loading demo file', async () => {
    const user = userEvent.setup()
    render(<WizardShell />)

    await user.click(screen.getByText('Load Demo File (Simple)'))
    await user.click(screen.getByText('Next'))
    // Step 2 shows confirm dialog first
    await user.click(screen.getByText('Continue'))
    await waitFor(() => expect(screen.getByText('Analysis Complete')).toBeInTheDocument())
  })

  it('Next button advances to the next step', async () => {
    const user = userEvent.setup()
    render(<WizardShell />)

    // Load demo to enable Next
    await user.click(screen.getByText('Load Demo File (Simple)'))
    expect(screen.getByText('Next')).toBeEnabled()

    // Advance to step 2 — confirm then wait for analysis
    await user.click(screen.getByText('Next'))
    await user.click(screen.getByText('Continue'))
    await waitFor(() => expect(screen.getByText('Analysis Complete')).toBeInTheDocument())

    // Advance to step 3 — confirm then wait for interface
    await user.click(screen.getByText('Next'))
    await user.click(screen.getByText('Continue'))
    await waitFor(() => expect(screen.getByText('IFoo')).toBeInTheDocument())
  })

  it('Back button returns to the previous step', async () => {
    const user = userEvent.setup()
    render(<WizardShell />)

    // Advance to step 2
    await user.click(screen.getByText('Load Demo File (Simple)'))
    await user.click(screen.getByText('Next'))
    await user.click(screen.getByText('Continue'))
    await waitFor(() => expect(screen.getByText('Analysis Complete')).toBeInTheDocument())

    // Go back to step 1
    await user.click(screen.getByText('Back'))
    expect(screen.getByText('Upload VB.NET Source')).toBeInTheDocument()
  })

  it('shows cost in USD and GBP after analysis completes', async () => {
    const user = userEvent.setup()
    render(<WizardShell />)

    await user.click(screen.getByText('Load Demo File (Simple)'))
    await user.click(screen.getByText('Next'))
    await user.click(screen.getByText('Continue'))
    await waitFor(() => expect(screen.getByText('Analysis Complete')).toBeInTheDocument())

    const costDisplay = screen.getByTestId('cost-display')
    expect(costDisplay).toHaveTextContent('$0.0088')
    expect(costDisplay).toHaveTextContent('£0.0070')
  })

  it('cost display is not visible before analysis', () => {
    render(<WizardShell />)
    expect(screen.queryByTestId('cost-display')).not.toBeInTheDocument()
  })

  it('Back button becomes enabled after advancing past Step 1', async () => {
    const user = userEvent.setup()
    render(<WizardShell />)

    expect(screen.getByText('Back')).toBeDisabled()

    await user.click(screen.getByText('Load Demo File (Simple)'))
    await user.click(screen.getByText('Next'))

    await waitFor(() => expect(screen.getByText('Back')).toBeEnabled())
  })

  it('cancel on confirm dialog does not trap user at Step 2', async () => {
    const user = userEvent.setup()
    render(<WizardShell />)

    // Advance to Step 2
    await user.click(screen.getByText('Load Demo File (Simple)'))
    await user.click(screen.getByText('Next'))
    // Cancel the confirm
    await user.click(screen.getByText('Cancel'))
    // Should show retry button
    expect(screen.getByText('Analyse with Claude')).toBeInTheDocument()
    // Click retry, then confirm should re-appear
    await user.click(screen.getByText('Analyse with Claude'))
    expect(screen.getByText('Continue')).toBeInTheDocument()
  })

  it('Next button is not visible on Step 6', async () => {
    const user = userEvent.setup()
    render(<WizardShell />)

    // Step 1 → load demo
    await user.click(screen.getByText('Load Demo File (Simple)'))
    await user.click(screen.getByText('Next'))

    // Step 2 → confirm then wait for analysis
    await user.click(screen.getByText('Continue'))
    await waitFor(() => expect(screen.getByText('Analysis Complete')).toBeInTheDocument())
    await user.click(screen.getByText('Next'))

    // Step 3 → confirm then wait for interface
    await user.click(screen.getByText('Continue'))
    await waitFor(() => expect(screen.getByText('IFoo')).toBeInTheDocument())
    await user.click(screen.getByText('Next'))

    // Step 4 → confirm then wait for tests + build
    await user.click(screen.getByText('Continue'))
    await waitFor(() => expect(screen.getByText(/tests failing/)).toBeInTheDocument())
    await user.click(screen.getByText('Next'))

    // Step 5 → click Claude Implements, confirm, wait for result
    await user.click(screen.getByText('Claude Implements'))
    await user.click(screen.getByText('Continue'))
    await waitFor(() => expect(screen.getByText(/tests passing/)).toBeInTheDocument())
    await user.click(screen.getByText('Next'))

    // Step 6 — confirm dialog, then PR raised
    await waitFor(() => expect(screen.getByText('Raise Pull Request')).toBeInTheDocument())
    await user.click(screen.getByText('Continue'))
    await waitFor(() => expect(screen.getByText('Pull Request Raised')).toBeInTheDocument())
    expect(screen.queryByText('Next')).not.toBeInTheDocument()
  })
})

describe('WizardShell multi-class iteration', () => {
  it('does not show loop-back arc for single-class analysis', async () => {
    const user = userEvent.setup()
    render(<WizardShell />)

    await user.click(screen.getByText('Load Demo File (Simple)'))
    await user.click(screen.getByText('Next'))
    await user.click(screen.getByText('Continue'))
    await waitFor(() => expect(screen.getByText('Analysis Complete')).toBeInTheDocument())

    // Single class — no progress banner or loop arc
    expect(screen.queryByTestId('class-progress-banner')).not.toBeInTheDocument()
    expect(screen.queryByTestId('loop-back-arc')).not.toBeInTheDocument()
  })

  it('shows loop-back arc in grey on first arrival at Step 3', async () => {
    const user = userEvent.setup()
    const { container } = render(<WizardShell />)

    const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement
    const file = new File(['multi'], 'Test.vb', { type: 'text/plain' })
    await user.upload(fileInput, file)

    await user.click(screen.getByText('Next'))
    await user.click(screen.getByText('Continue'))
    await waitFor(() => expect(screen.getByText('Analysis Complete')).toBeInTheDocument())

    // Advance to Step 3 — arc should appear in grey (not yet started)
    await user.click(screen.getByText('Next'))
    await waitFor(() => {
      const arc = screen.getByTestId('loop-back-arc')
      expect(arc).toBeInTheDocument()
      expect(arc).toHaveAttribute('data-arc-colour', 'grey')
    })
  })

  it('arc turns amber once iteration progresses past Step 3', async () => {
    const user = userEvent.setup()
    const { container } = render(<WizardShell />)

    const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement
    const file = new File(['multi'], 'Test.vb', { type: 'text/plain' })
    await user.upload(fileInput, file)

    await user.click(screen.getByText('Next'))
    await user.click(screen.getByText('Continue'))
    await waitFor(() => expect(screen.getByText('Analysis Complete')).toBeInTheDocument())

    // Step 2 → Step 3 → confirm and generate interface
    await user.click(screen.getByText('Next'))
    await user.click(screen.getByText('Continue'))
    await waitFor(() => expect(screen.getByText('IFoo')).toBeInTheDocument())

    // Step 3 → Step 4 — arc should now be amber
    await user.click(screen.getByText('Next'))
    await waitFor(() =>
      expect(screen.getByTestId('loop-back-arc')).toHaveAttribute('data-arc-colour', 'amber'),
    )
  })

  it(
    'shows Next Class button after completing first class in multi-class mode',
    { timeout: 15000 },
    async () => {
      const user = userEvent.setup()
      const { container } = render(<WizardShell />)

      // Upload with 'multi' content to trigger multi-class mock
      const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement
      const file = new File(['multi'], 'Test.vb', { type: 'text/plain' })
      await user.upload(fileInput, file)

      // Step 1 → Step 2
      await user.click(screen.getByText('Next'))
      await user.click(screen.getByText('Continue'))
      await waitFor(() => expect(screen.getByText('Analysis Complete')).toBeInTheDocument())

      // Should show both classes in analysis (appear in class card + migration order)
      expect(screen.getAllByText('Alpha').length).toBeGreaterThanOrEqual(1)
      expect(screen.getAllByText('Beta').length).toBeGreaterThanOrEqual(1)

      // Step 2 → Step 3 (Interface for Alpha)
      await user.click(screen.getByText('Next'))
      // Progress banner should appear
      await waitFor(() => expect(screen.getByTestId('class-progress-banner')).toBeInTheDocument())
      expect(screen.getByText(/Migrating class 1 of 2/)).toBeInTheDocument()
      expect(screen.getByText('Alpha')).toBeInTheDocument()

      // Complete Step 3
      await user.click(screen.getByText('Continue'))
      await waitFor(() => expect(screen.getByText('IFoo')).toBeInTheDocument())

      // Step 3 → Step 4
      await user.click(screen.getByText('Next'))
      await user.click(screen.getByText('Continue'))
      await waitFor(() => expect(screen.getByText(/tests failing/)).toBeInTheDocument())

      // Step 4 → Step 5
      await user.click(screen.getByText('Next'))
      await user.click(screen.getByText('Claude Implements'))
      await user.click(screen.getByText('Continue'))
      await waitFor(() => expect(screen.getByText(/tests passing/)).toBeInTheDocument())

      // Should show "Next Class" instead of "Next"
      expect(screen.getByText('Next Class')).toBeInTheDocument()
    },
  )

  it(
    'loops back to Interface step for second class after clicking Next Class',
    { timeout: 15000 },
    async () => {
      const user = userEvent.setup()
      const { container } = render(<WizardShell />)

      // Upload with 'multi' content
      const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement
      const file = new File(['multi'], 'Test.vb', { type: 'text/plain' })
      await user.upload(fileInput, file)

      // Step 1 → Step 2 → Analysis
      await user.click(screen.getByText('Next'))
      await user.click(screen.getByText('Continue'))
      await waitFor(() => expect(screen.getByText('Analysis Complete')).toBeInTheDocument())

      // Step 2 → Step 3 → Interface
      await user.click(screen.getByText('Next'))
      await user.click(screen.getByText('Continue'))
      await waitFor(() => expect(screen.getByText('IFoo')).toBeInTheDocument())

      // Step 3 → Step 4 → Tests
      await user.click(screen.getByText('Next'))
      await user.click(screen.getByText('Continue'))
      await waitFor(() => expect(screen.getByText(/tests failing/)).toBeInTheDocument())

      // Step 4 → Step 5 → Implement
      await user.click(screen.getByText('Next'))
      await user.click(screen.getByText('Claude Implements'))
      await user.click(screen.getByText('Continue'))
      await waitFor(() => expect(screen.getByText(/tests passing/)).toBeInTheDocument())

      // Click "Next Class" — should loop back to Interface for Beta
      await user.click(screen.getByText('Next Class'))

      // Should now show class 2 of 2 in progress banner
      await waitFor(() => expect(screen.getByText(/Migrating class 2 of 2/)).toBeInTheDocument())
      expect(screen.getByText('Beta')).toBeInTheDocument()
      // Should show confirm dialog for Interface step again
      expect(screen.getByText('Continue')).toBeInTheDocument()
    },
  )

  it('arc turns green after all classes are complete', async () => {
    const user = userEvent.setup()
    const { container } = render(<WizardShell />)

    // Upload with 'multi' content (2 classes: Alpha, Beta)
    const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement
    const file = new File(['multi'], 'Test.vb', { type: 'text/plain' })
    await user.upload(fileInput, file)

    // Step 1 → Step 2 → Analysis
    await user.click(screen.getByText('Next'))
    await user.click(screen.getByText('Continue'))
    await waitFor(() => expect(screen.getByText('Analysis Complete')).toBeInTheDocument())

    // --- Class 1 (Alpha) ---
    // Step 2 → Step 3 → Interface
    await user.click(screen.getByText('Next'))
    await user.click(screen.getByText('Continue'))
    await waitFor(() => expect(screen.getByText('IFoo')).toBeInTheDocument())

    // Arc should be grey while iterating
    expect(screen.getByTestId('loop-back-arc')).toHaveAttribute('data-arc-colour', 'grey')

    // Step 3 → Step 4 → Tests
    await user.click(screen.getByText('Next'))
    await user.click(screen.getByText('Continue'))
    await waitFor(() => expect(screen.getByText(/tests failing/)).toBeInTheDocument())

    // Step 4 → Step 5 → Implement
    await user.click(screen.getByText('Next'))
    await user.click(screen.getByText('Claude Implements'))
    await user.click(screen.getByText('Continue'))
    await waitFor(() => expect(screen.getByText(/tests passing/)).toBeInTheDocument())

    // Click "Next Class" → loops back to Interface for Beta
    await user.click(screen.getByText('Next Class'))
    await waitFor(() => expect(screen.getByText(/Migrating class 2 of 2/)).toBeInTheDocument())

    // --- Class 2 (Beta) ---
    // Step 3 → Interface
    await user.click(screen.getByText('Continue'))
    await waitFor(() => expect(screen.getByText('IFoo')).toBeInTheDocument())

    // Step 3 → Step 4 → Tests
    await user.click(screen.getByText('Next'))
    await user.click(screen.getByText('Continue'))
    await waitFor(() => expect(screen.getByText(/tests failing/)).toBeInTheDocument())

    // Step 4 → Step 5 → Implement
    await user.click(screen.getByText('Next'))
    await user.click(screen.getByText('Claude Implements'))
    await user.click(screen.getByText('Continue'))
    await waitFor(() => expect(screen.getByText(/tests passing/)).toBeInTheDocument())

    // Still on Step 5 (Implement) for last class — arc should be amber
    expect(screen.getByTestId('loop-back-arc')).toHaveAttribute('data-arc-colour', 'amber')

    // Click Next — saves last class and advances to Step 6 (Raise PR)
    await user.click(screen.getByText('Next'))

    // Now all classes are saved — arc should be green
    await waitFor(() =>
      expect(screen.getByTestId('loop-back-arc')).toHaveAttribute('data-arc-colour', 'green'),
    )
  }, 15000)
})
