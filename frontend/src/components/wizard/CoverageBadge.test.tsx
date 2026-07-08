import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { CoverageBadge } from './CoverageBadge'

describe('CoverageBadge', () => {
  it('renders nothing when coverage was not collected (null)', () => {
    const { container } = render(<CoverageBadge coveragePercent={null} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('renders nothing when coverage is undefined', () => {
    const { container } = render(<CoverageBadge coveragePercent={undefined} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('shows an at-or-above-target badge in the ok state', () => {
    render(<CoverageBadge coveragePercent={87.5} />)
    const badge = screen.getByTestId('coverage-badge')
    expect(badge).toHaveClass('coverage-ok')
    expect(badge).toHaveTextContent('87.5% line coverage')
    expect(badge).toHaveTextContent('meets the 80% target')
  })

  it('warns in the warn state when below the threshold', () => {
    render(<CoverageBadge coveragePercent={72.3} />)
    const badge = screen.getByTestId('coverage-badge')
    expect(badge).toHaveClass('coverage-warn')
    expect(badge).toHaveTextContent('below the 80% target')
    expect(badge).toHaveTextContent('warning, not a blocker')
  })

  it('treats exactly the threshold as meeting it', () => {
    render(<CoverageBadge coveragePercent={80} />)
    expect(screen.getByTestId('coverage-badge')).toHaveClass('coverage-ok')
  })

  it('respects a custom threshold', () => {
    render(<CoverageBadge coveragePercent={85} threshold={90} />)
    const badge = screen.getByTestId('coverage-badge')
    expect(badge).toHaveClass('coverage-warn')
    expect(badge).toHaveTextContent('below the 90% target')
  })

  it('renders the provided ofLabel', () => {
    render(<CoverageBadge coveragePercent={91} ofLabel="your original VB.NET" />)
    expect(screen.getByTestId('coverage-badge')).toHaveTextContent(
      '91.0% line coverage of your original VB.NET',
    )
  })
})
