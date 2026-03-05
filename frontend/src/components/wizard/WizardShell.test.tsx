import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { WizardShell } from './WizardShell'

// Mock all API calls to resolve instantly
vi.mock('../../api/migrateApi', async () => {
  const actual = await vi.importActual('../../api/migrateApi')
  return {
    ...actual,
    analyse: vi.fn().mockResolvedValue({
      sessionId: 'test-session',
      classes: [{ name: 'Foo', methods: ['Bar'], dependencies: [], complexity: 'LOW' }],
      suggestedMigrationOrder: ['Foo'],
      summary: 'Test summary',
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
      totalCost: 0,
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

describe('WizardShell navigation', () => {
  it('advances to Step 2 after loading demo file', async () => {
    const user = userEvent.setup()
    render(<WizardShell />)

    await user.click(screen.getByText('Load demo file'))
    await user.click(screen.getByText('Next'))
    await waitFor(() => expect(screen.getByText('Analysis Complete')).toBeInTheDocument())
  })

  it('Next button advances to the next step', async () => {
    const user = userEvent.setup()
    render(<WizardShell />)

    // Load demo to enable Next
    await user.click(screen.getByText('Load demo file'))
    expect(screen.getByText('Next')).toBeEnabled()

    // Advance to step 2 — mock resolves instantly so we see completed state
    await user.click(screen.getByText('Next'))
    await waitFor(() => expect(screen.getByText('Analysis Complete')).toBeInTheDocument())

    // Advance to step 3
    await user.click(screen.getByText('Next'))
    await waitFor(() => expect(screen.getByText('IFoo')).toBeInTheDocument())
  })

  it('Back button returns to the previous step', async () => {
    const user = userEvent.setup()
    render(<WizardShell />)

    // Advance to step 2
    await user.click(screen.getByText('Load demo file'))
    await user.click(screen.getByText('Next'))
    await waitFor(() => expect(screen.getByText('Analysis Complete')).toBeInTheDocument())

    // Go back to step 1
    await user.click(screen.getByText('Back'))
    expect(screen.getByText('Upload VB.NET Source')).toBeInTheDocument()
  })

  it('Back button becomes enabled after advancing past Step 1', async () => {
    const user = userEvent.setup()
    render(<WizardShell />)

    expect(screen.getByText('Back')).toBeDisabled()

    await user.click(screen.getByText('Load demo file'))
    await user.click(screen.getByText('Next'))

    expect(screen.getByText('Back')).toBeEnabled()
  })

  it('Next button is not visible on Step 6', async () => {
    const user = userEvent.setup()
    render(<WizardShell />)

    // Step 1 → load demo
    await user.click(screen.getByText('Load demo file'))
    await user.click(screen.getByText('Next'))

    // Step 2 → wait for analysis
    await waitFor(() => expect(screen.getByText('Analysis Complete')).toBeInTheDocument())
    await user.click(screen.getByText('Next'))

    // Step 3 → wait for interface
    await waitFor(() => expect(screen.getByText('IFoo')).toBeInTheDocument())
    await user.click(screen.getByText('Next'))

    // Step 4 → wait for tests + build
    await waitFor(() => expect(screen.getByText(/tests failing/)).toBeInTheDocument())
    await user.click(screen.getByText('Next'))

    // Step 5 → click Claude Implements, wait for result
    await user.click(screen.getByText('Claude Implements'))
    await waitFor(() => expect(screen.getByText(/tests passing/)).toBeInTheDocument())
    await user.click(screen.getByText('Next'))

    // Step 6 — Next button should not be in the document
    await waitFor(() => expect(screen.getByText('Pull Request Raised')).toBeInTheDocument())
    expect(screen.queryByText('Next')).not.toBeInTheDocument()
  })
})
