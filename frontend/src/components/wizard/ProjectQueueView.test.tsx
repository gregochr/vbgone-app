import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ProjectQueueView } from './ProjectQueueView'
import { isClassEnabled, firstUnmigratedDep } from './classStatus'
import type { ClassStatus } from './classStatus'
import type { ProjectAnalysis } from '../../api/migrateApi'

vi.mock('../../api/migrateApi', async () => {
  const actual = await vi.importActual('../../api/migrateApi')
  return {
    ...actual,
    fetchCost: vi.fn().mockResolvedValue({
      sessionId: 'test-session',
      steps: [],
      totalCost: 0,
    }),
    generateInterface: vi.fn().mockResolvedValue({
      sessionId: 'test-session',
      className: 'ValidationHelper',
      interfaceName: 'IValidationHelper',
      code: 'public interface IValidationHelper {}',
    }),
    raisePR: vi.fn().mockResolvedValue({
      sessionId: 'test-session',
      prUrl: 'https://github.com/test/repo/pull/1',
      branchName: 'migrate/project',
      filesCommitted: ['IValidationHelper.cs', 'ValidationHelper.cs'],
    }),
  }
})

const mockAnalysis: ProjectAnalysis = {
  sessionId: 'test-session-123',
  classes: [
    {
      name: 'ValidationHelper',
      methods: ['IsNullOrEmpty', 'IsValidEmail', 'IsInRange'],
      dependencies: [],
      complexity: 'LOW',
    },
    {
      name: 'StringHelper',
      methods: ['Capitalize', 'TruncateWithEllipsis'],
      dependencies: [],
      complexity: 'LOW',
    },
    {
      name: 'DateHelper',
      methods: ['IsWeekday', 'GetBusinessDaysBetween'],
      dependencies: ['ValidationHelper'],
      complexity: 'MEDIUM',
    },
    {
      name: 'Calculator',
      methods: ['Add', 'Subtract', 'Multiply', 'Divide'],
      dependencies: ['StringHelper', 'DateHelper'],
      complexity: 'HIGH',
    },
  ],
  suggestedMigrationOrder: ['ValidationHelper', 'StringHelper', 'DateHelper', 'Calculator'],
  dependencyGraph: {
    ValidationHelper: [],
    StringHelper: [],
    DateHelper: ['ValidationHelper'],
    Calculator: ['StringHelper', 'DateHelper'],
  },
  summary: 'Four classes found.',
}

describe('ProjectQueueView', () => {
  it('renders the migration queue title', () => {
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)
    expect(screen.getByText('Migration Queue')).toBeInTheDocument()
  })

  it('displays the project summary', () => {
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)
    expect(screen.getByText('Four classes found.')).toBeInTheDocument()
  })

  it('shows class count in summary stats', () => {
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)
    expect(screen.getByText('4')).toBeInTheDocument()
    expect(screen.getByText('Classes')).toBeInTheDocument()
  })

  it('shows high complexity count', () => {
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)
    expect(screen.getByText('High Complexity')).toBeInTheDocument()
    expect(screen.getByText('1')).toBeInTheDocument()
  })

  it('renders all class cards in migration order', () => {
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)
    const cardNames = document.querySelectorAll('.queue-card-name')
    const names = Array.from(cardNames).map((h) => h.textContent)
    expect(names).toEqual(['ValidationHelper', 'StringHelper', 'DateHelper', 'Calculator'])
  })

  it('shows complexity badges on class cards', () => {
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)
    const badges = document.querySelectorAll('.queue-card .badge')
    expect(badges.length).toBe(4)
  })

  it('shows dependencies on class cards', () => {
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)
    // DateHelper depends on ValidationHelper
    const depsText = screen.getAllByText(/Depends on:/)
    expect(depsText.length).toBe(2) // DateHelper and Calculator
  })

  it('shows Migrate button for each Pending class', () => {
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)
    const migrateButtons = screen.getAllByText('Migrate')
    expect(migrateButtons.length).toBe(4)
  })

  it('shows progress bar at 0 of 4', () => {
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)
    expect(screen.getByText('0 of 4 classes migrated')).toBeInTheDocument()
  })

  it('progress fill starts at 0%', () => {
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)
    const fill = screen.getByTestId('progress-fill')
    expect(fill).toHaveStyle({ width: '0%' })
  })

  it('shows method tags on class cards', () => {
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)
    expect(screen.getByText('IsNullOrEmpty')).toBeInTheDocument()
    expect(screen.getByText('Capitalize')).toBeInTheDocument()
    expect(screen.getByText('Add')).toBeInTheDocument()
  })

  it('all classes start with Pending status', () => {
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)
    const pendingStatuses = screen.getAllByText('Pending')
    expect(pendingStatuses.length).toBe(4)
  })

  it('clicking Migrate switches to wizard view', async () => {
    const user = userEvent.setup()
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)

    const migrateButtons = screen.getAllByText('Migrate')
    await user.click(migrateButtons[0])

    // Should show wizard with context banner
    expect(screen.getByText(/Back to Queue/)).toBeInTheDocument()
    expect(screen.getByText(/Migrating class 1 of 4/)).toBeInTheDocument()
  })

  it('shows Back button', () => {
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)
    expect(screen.getByText('Back')).toBeInTheDocument()
  })

  it('Back button calls onBackToUpload', async () => {
    const user = userEvent.setup()
    const onBackToUpload = vi.fn()
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={onBackToUpload} />)

    await user.click(screen.getByText('Back'))
    expect(onBackToUpload).toHaveBeenCalled()
  })

  it('shows Buy me a coffee link', () => {
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)
    expect(screen.getByText('Buy me a coffee')).toBeInTheDocument()
  })
})

describe('ProjectQueueView — collapsible cards', () => {
  it('shows collapsible header with class count', () => {
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)
    expect(screen.getByText('Migration Queue (4 classes)')).toBeInTheDocument()
  })

  it('cards are expanded by default', () => {
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)
    const header = screen.getByText('Migration Queue (4 classes)')
    expect(header).toHaveAttribute('aria-expanded', 'true')
  })

  it('chevron has open class when expanded', () => {
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)
    const chevron = document.querySelector('.collapsible-cards-header .collapsible-chevron')
    expect(chevron).toHaveClass('open')
  })

  it('clicking header collapses the cards', async () => {
    const user = userEvent.setup()
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)

    const header = screen.getByText('Migration Queue (4 classes)')
    await user.click(header)

    expect(header).toHaveAttribute('aria-expanded', 'false')
    const chevron = document.querySelector('.collapsible-cards-header .collapsible-chevron')
    expect(chevron).not.toHaveClass('open')
  })

  it('collapsed body has maxHeight 0px', async () => {
    const user = userEvent.setup()
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)

    await user.click(screen.getByText('Migration Queue (4 classes)'))

    const body = document.querySelector('.collapsible-cards-body') as HTMLElement
    expect(body.style.maxHeight).toBe('0px')
    expect(body.style.opacity).toBe('0')
  })

  it('clicking header again re-expands the cards', async () => {
    const user = userEvent.setup()
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)

    const header = screen.getByText('Migration Queue (4 classes)')
    await user.click(header) // collapse
    await user.click(header) // expand

    expect(header).toHaveAttribute('aria-expanded', 'true')
    const chevron = document.querySelector('.collapsible-cards-header .collapsible-chevron')
    expect(chevron).toHaveClass('open')
  })

  it('progress bar remains visible when cards are collapsed', async () => {
    const user = userEvent.setup()
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)

    await user.click(screen.getByText('Migration Queue (4 classes)'))

    expect(screen.getByText('0 of 4 classes migrated')).toBeInTheDocument()
    expect(screen.getByTestId('progress-fill')).toBeInTheDocument()
  })
})

describe('ProjectQueueView — disabled Migrate buttons', () => {
  it('Migrate buttons for classes with unmigrated deps are disabled', () => {
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)
    const migrateButtons = screen.getAllByText('Migrate')
    // ValidationHelper (no deps) — enabled
    expect(migrateButtons[0]).toBeEnabled()
    // StringHelper (no deps) — enabled
    expect(migrateButtons[1]).toBeEnabled()
    // DateHelper (depends on ValidationHelper=Pending) — disabled
    expect(migrateButtons[2]).toBeDisabled()
    // Calculator (depends on StringHelper=Pending, DateHelper=Pending) — disabled
    expect(migrateButtons[3]).toBeDisabled()
  })

  it('disabled Migrate button has tooltip explaining blocker', () => {
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)
    const migrateButtons = screen.getAllByText('Migrate')
    // DateHelper blocked by ValidationHelper
    expect(migrateButtons[2]).toHaveAttribute('title', 'Migrate ValidationHelper first')
    // Calculator blocked by StringHelper (first unmigrated dep)
    expect(migrateButtons[3]).toHaveAttribute('title', 'Migrate StringHelper first')
  })

  it('enabled Migrate buttons have no title attribute', () => {
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)
    const migrateButtons = screen.getAllByText('Migrate')
    expect(migrateButtons[0]).not.toHaveAttribute('title')
    expect(migrateButtons[1]).not.toHaveAttribute('title')
  })

  it('clicking disabled Migrate button does not switch to wizard', async () => {
    const user = userEvent.setup()
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)

    const migrateButtons = screen.getAllByText('Migrate')
    await user.click(migrateButtons[2]) // DateHelper — disabled

    // Should still be on queue view
    expect(screen.getByText('Migration Queue')).toBeInTheDocument()
    expect(screen.queryByText(/Back to Queue/)).not.toBeInTheDocument()
  })
})

describe('isClassEnabled', () => {
  const depGraph: Record<string, string[]> = {
    A: [],
    B: ['A'],
    C: ['A', 'B'],
  }

  it('returns true for class with no dependencies', () => {
    const statuses: Record<string, ClassStatus> = { A: 'Pending', B: 'Pending', C: 'Pending' }
    expect(isClassEnabled('A', depGraph, statuses)).toBe(true)
  })

  it('returns false when dependency is Pending', () => {
    const statuses: Record<string, ClassStatus> = { A: 'Pending', B: 'Pending', C: 'Pending' }
    expect(isClassEnabled('B', depGraph, statuses)).toBe(false)
  })

  it('returns true when dependency is Complete', () => {
    const statuses: Record<string, ClassStatus> = { A: 'Complete', B: 'Pending', C: 'Pending' }
    expect(isClassEnabled('B', depGraph, statuses)).toBe(true)
  })

  it('returns true when dependency is PR Raised', () => {
    const statuses: Record<string, ClassStatus> = { A: 'PR Raised', B: 'Pending', C: 'Pending' }
    expect(isClassEnabled('B', depGraph, statuses)).toBe(true)
  })

  it('returns false when dependency is In Progress', () => {
    const statuses: Record<string, ClassStatus> = {
      A: 'In Progress',
      B: 'Pending',
      C: 'Pending',
    }
    expect(isClassEnabled('B', depGraph, statuses)).toBe(false)
  })

  it('requires ALL dependencies to be migrated', () => {
    const statuses: Record<string, ClassStatus> = { A: 'Complete', B: 'Pending', C: 'Pending' }
    // C depends on A (Complete) and B (Pending) — should be false
    expect(isClassEnabled('C', depGraph, statuses)).toBe(false)
  })

  it('returns true when all dependencies are migrated', () => {
    const statuses: Record<string, ClassStatus> = { A: 'Complete', B: 'PR Raised', C: 'Pending' }
    expect(isClassEnabled('C', depGraph, statuses)).toBe(true)
  })
})

describe('firstUnmigratedDep', () => {
  const depGraph: Record<string, string[]> = {
    A: [],
    B: ['A'],
    C: ['A', 'B'],
  }

  it('returns null for class with no dependencies', () => {
    const statuses: Record<string, ClassStatus> = { A: 'Pending', B: 'Pending', C: 'Pending' }
    expect(firstUnmigratedDep('A', depGraph, statuses)).toBeNull()
  })

  it('returns the first unmigrated dependency', () => {
    const statuses: Record<string, ClassStatus> = { A: 'Pending', B: 'Pending', C: 'Pending' }
    expect(firstUnmigratedDep('C', depGraph, statuses)).toBe('A')
  })

  it('skips migrated deps and returns first unmigrated', () => {
    const statuses: Record<string, ClassStatus> = { A: 'Complete', B: 'Pending', C: 'Pending' }
    expect(firstUnmigratedDep('C', depGraph, statuses)).toBe('B')
  })

  it('returns null when all dependencies are migrated', () => {
    const statuses: Record<string, ClassStatus> = { A: 'Complete', B: 'PR Raised', C: 'Pending' }
    expect(firstUnmigratedDep('C', depGraph, statuses)).toBeNull()
  })
})

describe('ProjectQueueView — Raise PR button', () => {
  it('shows Raise PR button', () => {
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)
    expect(screen.getByText('Raise PR')).toBeInTheDocument()
  })

  it('Raise PR button is disabled when not all classes are complete', () => {
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)
    const btn = screen.getByText('Raise PR')
    expect(btn).toBeDisabled()
  })

  it('disabled Raise PR button has tooltip explaining requirement', () => {
    render(<ProjectQueueView analysis={mockAnalysis} onBackToUpload={vi.fn()} />)
    const btn = screen.getByText('Raise PR')
    expect(btn).toHaveAttribute('title', 'Migrate all classes before raising a PR')
  })
})
