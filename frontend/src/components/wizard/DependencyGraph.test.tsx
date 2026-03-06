import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { DependencyGraph } from './DependencyGraph'
import type { ClassInfo } from '../../api/migrateApi'
import type { ClassStatus } from './ProjectQueueView'

const classes: ClassInfo[] = [
  { name: 'ValidationHelper', methods: ['IsNullOrEmpty'], dependencies: [], complexity: 'LOW' },
  {
    name: 'DateHelper',
    methods: ['IsWeekday'],
    dependencies: ['ValidationHelper'],
    complexity: 'MEDIUM',
  },
  {
    name: 'Calculator',
    methods: ['Add'],
    dependencies: ['DateHelper'],
    complexity: 'HIGH',
  },
]

const depGraph: Record<string, string[]> = {
  ValidationHelper: [],
  DateHelper: ['ValidationHelper'],
  Calculator: ['DateHelper'],
}

const allPending: Record<string, ClassStatus> = {
  ValidationHelper: 'Pending',
  DateHelper: 'Pending',
  Calculator: 'Pending',
}

function renderGraph(
  statuses: Record<string, ClassStatus> = allPending,
  onMigrate = vi.fn(),
) {
  return render(
    <DependencyGraph
      classes={classes}
      dependencyGraph={depGraph}
      statuses={statuses}
      onMigrate={onMigrate}
    />,
  )
}

function findNodeGroup(name: string): Element {
  const labels = document.querySelectorAll('svg text')
  const target = Array.from(labels).find((t) => t.textContent === name)
  return target!.closest('g')!
}

describe('DependencyGraph', () => {
  it('renders the graph container', () => {
    renderGraph()
    expect(screen.getByTestId('dependency-graph')).toBeInTheDocument()
  })

  it('renders the title', () => {
    renderGraph()
    expect(screen.getByText('Dependency Graph')).toBeInTheDocument()
  })

  it('renders the legend with all three complexity levels', () => {
    renderGraph()
    expect(screen.getByText('LOW')).toBeInTheDocument()
    expect(screen.getByText('MEDIUM')).toBeInTheDocument()
    expect(screen.getByText('HIGH')).toBeInTheDocument()
  })

  it('renders three legend dots', () => {
    renderGraph()
    const dots = document.querySelectorAll('.dep-graph-dot')
    expect(dots.length).toBe(3)
  })

  it('renders an SVG element', () => {
    renderGraph()
    const svg = document.querySelector('svg.dep-graph-svg')
    expect(svg).toBeTruthy()
  })

  it('renders a text label for each class', () => {
    renderGraph()
    const labels = document.querySelectorAll('svg text')
    expect(labels.length).toBe(3)
    const texts = Array.from(labels).map((t) => t.textContent)
    expect(texts).toContain('ValidationHelper')
    expect(texts).toContain('DateHelper')
    expect(texts).toContain('Calculator')
  })

  it('renders link lines for dependencies', () => {
    renderGraph()
    const lines = document.querySelectorAll('svg line')
    expect(lines.length).toBe(2)
  })

  it('renders an arrow marker definition', () => {
    renderGraph()
    const marker = document.querySelector('svg marker#dep-arrow')
    expect(marker).toBeTruthy()
  })

  it('renders nothing inside SVG when classes array is empty', () => {
    render(
      <DependencyGraph
        classes={[]}
        dependencyGraph={{}}
        statuses={{}}
        onMigrate={vi.fn()}
      />,
    )
    const svg = document.querySelector('svg.dep-graph-svg')
    expect(svg).toBeTruthy()
    expect(svg!.querySelectorAll('circle').length).toBe(0)
    expect(svg!.querySelectorAll('line').length).toBe(0)
  })

  it('handles a single class with no dependencies', () => {
    const single: ClassInfo[] = [
      { name: 'Solo', methods: ['DoWork'], dependencies: [], complexity: 'LOW' },
    ]
    render(
      <DependencyGraph
        classes={single}
        dependencyGraph={{ Solo: [] }}
        statuses={{ Solo: 'Pending' }}
        onMigrate={vi.fn()}
      />,
    )
    const lines = document.querySelectorAll('svg line')
    expect(lines.length).toBe(0)
  })

  it('shows hint text below the graph', () => {
    renderGraph()
    expect(screen.getByText('Click a class node to start migration')).toBeInTheDocument()
  })
})

describe('DependencyGraph — tooltips', () => {
  it('shows "Click to migrate" for enabled Pending nodes', () => {
    renderGraph()
    const titles = document.querySelectorAll('svg title')
    const texts = Array.from(titles).map((t) => t.textContent)
    expect(texts).toContain('Click to migrate ValidationHelper')
  })

  it('shows "migrate X first" for disabled Pending nodes', () => {
    renderGraph()
    const titles = document.querySelectorAll('svg title')
    const texts = Array.from(titles).map((t) => t.textContent)
    // DateHelper depends on ValidationHelper which is Pending
    expect(texts).toContain('DateHelper \u2014 migrate ValidationHelper first')
  })

  it('shows status tooltip for non-Pending nodes', () => {
    const mixed: Record<string, ClassStatus> = {
      ...allPending,
      ValidationHelper: 'Complete',
    }
    renderGraph(mixed)
    const titles = document.querySelectorAll('svg title')
    const texts = Array.from(titles).map((t) => t.textContent)
    expect(texts).toContain('ValidationHelper \u2014 Complete')
  })
})

describe('DependencyGraph — cursor styles', () => {
  it('shows pointer cursor on enabled Pending nodes', () => {
    renderGraph()
    const nodeGroup = findNodeGroup('ValidationHelper') as HTMLElement
    expect(nodeGroup.style.cursor).toBe('pointer')
  })

  it('shows not-allowed cursor on disabled Pending nodes', () => {
    renderGraph()
    const nodeGroup = findNodeGroup('DateHelper') as HTMLElement
    expect(nodeGroup.style.cursor).toBe('not-allowed')
  })

  it('shows default cursor on Complete nodes', () => {
    const mixed: Record<string, ClassStatus> = {
      ...allPending,
      ValidationHelper: 'Complete',
    }
    renderGraph(mixed)
    const nodeGroup = findNodeGroup('ValidationHelper') as HTMLElement
    expect(nodeGroup.style.cursor).toBe('default')
  })
})

describe('DependencyGraph — click behaviour', () => {
  it('clicking an enabled Pending node calls onMigrate', () => {
    const onMigrate = vi.fn()
    renderGraph(allPending, onMigrate)
    fireEvent.click(findNodeGroup('ValidationHelper'))
    expect(onMigrate).toHaveBeenCalledWith('ValidationHelper')
  })

  it('clicking a disabled Pending node does NOT call onMigrate', () => {
    const onMigrate = vi.fn()
    renderGraph(allPending, onMigrate)
    fireEvent.click(findNodeGroup('DateHelper'))
    expect(onMigrate).not.toHaveBeenCalled()
  })

  it('clicking a Complete node does NOT call onMigrate', () => {
    const onMigrate = vi.fn()
    const mixed: Record<string, ClassStatus> = {
      ...allPending,
      ValidationHelper: 'Complete',
    }
    renderGraph(mixed, onMigrate)
    fireEvent.click(findNodeGroup('ValidationHelper'))
    expect(onMigrate).not.toHaveBeenCalled()
  })
})

describe('DependencyGraph — node visuals by status', () => {
  it('enabled Pending nodes get pulse circles', () => {
    renderGraph()
    const pulseCircles = document.querySelectorAll('svg .dep-graph-pulse')
    // Only ValidationHelper is enabled (no deps)
    expect(pulseCircles.length).toBe(1)
  })

  it('disabled Pending nodes do NOT get pulse circles', () => {
    renderGraph()
    // DateHelper and Calculator are disabled — should not pulse
    const pulseCircles = document.querySelectorAll('svg .dep-graph-pulse')
    // Only 1 pulse (ValidationHelper)
    expect(pulseCircles.length).toBe(1)
  })

  it('In Progress nodes get pulse circles', () => {
    const withInProgress: Record<string, ClassStatus> = {
      ...allPending,
      ValidationHelper: 'In Progress',
    }
    renderGraph(withInProgress)
    const pulseCircles = document.querySelectorAll('svg .dep-graph-pulse')
    expect(pulseCircles.length).toBe(1)
  })

  it('Complete nodes get a filled green circle', () => {
    const withComplete: Record<string, ClassStatus> = {
      ...allPending,
      ValidationHelper: 'Complete',
    }
    renderGraph(withComplete)
    // Find the main circle for ValidationHelper
    const nodeGroup = findNodeGroup('ValidationHelper')
    const circles = nodeGroup.querySelectorAll('circle')
    // Should have main circle + inner dot
    const mainCircle = Array.from(circles).find(
      (c) => c.getAttribute('r') === '22',
    )
    expect(mainCircle?.getAttribute('fill')).toBe('#4CAF50')
  })

  it('PR Raised nodes get a filled gold circle', () => {
    const withPR: Record<string, ClassStatus> = {
      ...allPending,
      ValidationHelper: 'PR Raised',
    }
    renderGraph(withPR)
    const nodeGroup = findNodeGroup('ValidationHelper')
    const circles = nodeGroup.querySelectorAll('circle')
    const mainCircle = Array.from(circles).find(
      (c) => c.getAttribute('r') === '22',
    )
    expect(mainCircle?.getAttribute('fill')).toBe('#E5A00D')
  })

  it('disabled Pending nodes get greyed-out fill', () => {
    renderGraph()
    const nodeGroup = findNodeGroup('DateHelper')
    const circles = nodeGroup.querySelectorAll('circle')
    const mainCircle = Array.from(circles).find(
      (c) => c.getAttribute('r') === '22',
    )
    expect(mainCircle?.getAttribute('fill')).toBe('#555')
  })

  it('disabled Pending nodes get greyed-out stroke', () => {
    renderGraph()
    const nodeGroup = findNodeGroup('DateHelper')
    const circles = nodeGroup.querySelectorAll('circle')
    const mainCircle = Array.from(circles).find(
      (c) => c.getAttribute('r') === '22',
    )
    expect(mainCircle?.getAttribute('stroke')).toBe('#555')
  })

  it('completed nodes get an inner dot', () => {
    const withComplete: Record<string, ClassStatus> = {
      ...allPending,
      ValidationHelper: 'Complete',
    }
    renderGraph(withComplete)
    const nodeGroup = findNodeGroup('ValidationHelper')
    const innerDot = Array.from(nodeGroup.querySelectorAll('circle')).find(
      (c) => c.getAttribute('r') === '6',
    )
    expect(innerDot).toBeTruthy()
  })

  it('disabled Pending nodes have dimmed label text', () => {
    renderGraph()
    const label = Array.from(document.querySelectorAll('svg text')).find(
      (t) => t.textContent === 'DateHelper',
    )
    expect(label?.getAttribute('fill')).toBe('#666')
  })

  it('enabled Pending nodes have normal label text', () => {
    renderGraph()
    const label = Array.from(document.querySelectorAll('svg text')).find(
      (t) => t.textContent === 'ValidationHelper',
    )
    expect(label?.getAttribute('fill')).toBe('#b0b3b8')
  })
})

describe('DependencyGraph — dependency unlocking', () => {
  it('node becomes enabled once its dependency is Complete', () => {
    const withComplete: Record<string, ClassStatus> = {
      ...allPending,
      ValidationHelper: 'Complete',
    }
    renderGraph(withComplete)
    const nodeGroup = findNodeGroup('DateHelper') as HTMLElement
    // DateHelper should now be enabled (its dep ValidationHelper is Complete)
    expect(nodeGroup.style.cursor).toBe('pointer')
  })

  it('node becomes enabled once its dependency is PR Raised', () => {
    const withPR: Record<string, ClassStatus> = {
      ...allPending,
      ValidationHelper: 'PR Raised',
    }
    renderGraph(withPR)
    const nodeGroup = findNodeGroup('DateHelper') as HTMLElement
    expect(nodeGroup.style.cursor).toBe('pointer')
  })

  it('node stays disabled if dependency is In Progress', () => {
    const withInProgress: Record<string, ClassStatus> = {
      ...allPending,
      ValidationHelper: 'In Progress',
    }
    renderGraph(withInProgress)
    const nodeGroup = findNodeGroup('DateHelper') as HTMLElement
    expect(nodeGroup.style.cursor).toBe('not-allowed')
  })

  it('deeply nested node stays disabled until chain is complete', () => {
    // Calculator depends on DateHelper which depends on ValidationHelper
    // Only ValidationHelper is Complete — Calculator should still be disabled
    const partial: Record<string, ClassStatus> = {
      ValidationHelper: 'Complete',
      DateHelper: 'Pending',
      Calculator: 'Pending',
    }
    renderGraph(partial)
    const nodeGroup = findNodeGroup('Calculator') as HTMLElement
    expect(nodeGroup.style.cursor).toBe('not-allowed')
  })

  it('deeply nested node becomes enabled once full chain is complete', () => {
    const allDone: Record<string, ClassStatus> = {
      ValidationHelper: 'Complete',
      DateHelper: 'Complete',
      Calculator: 'Pending',
    }
    renderGraph(allDone)
    const nodeGroup = findNodeGroup('Calculator') as HTMLElement
    expect(nodeGroup.style.cursor).toBe('pointer')
  })
})
