import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CollapsibleCode } from './CollapsibleCode'

vi.mock('prism-react-renderer', () => ({
  Highlight: ({ code }: { code: string }) => <pre>{code}</pre>,
  themes: { vsDark: {} },
}))

describe('CollapsibleCode', () => {
  it('renders the title', () => {
    render(<CollapsibleCode title="Generated Tests (10 tests)" code="test code" />)
    expect(screen.getByText('Generated Tests (10 tests)')).toBeInTheDocument()
  })

  it('is collapsed by default', () => {
    render(<CollapsibleCode title="Tests" code="test code" />)
    const header = screen.getByRole('button', { name: /Tests/ })
    expect(header).toHaveAttribute('aria-expanded', 'false')
    const body = header
      .closest('.collapsible-code')!
      .querySelector('.collapsible-body') as HTMLElement
    expect(body.style.maxHeight).toBe('0px')
    expect(body.style.opacity).toBe('0')
  })

  it('expands when header is clicked', async () => {
    const user = userEvent.setup()
    render(<CollapsibleCode title="Tests" code="test code" />)
    const header = screen.getByRole('button', { name: /Tests/ })

    await user.click(header)

    expect(header).toHaveAttribute('aria-expanded', 'true')
  })

  it('collapses again on second click', async () => {
    const user = userEvent.setup()
    render(<CollapsibleCode title="Tests" code="test code" />)
    const header = screen.getByRole('button', { name: /Tests/ })

    await user.click(header)
    expect(header).toHaveAttribute('aria-expanded', 'true')

    await user.click(header)
    expect(header).toHaveAttribute('aria-expanded', 'false')
  })

  it('renders open when defaultOpen is true', () => {
    render(<CollapsibleCode title="Tests" code="test code" defaultOpen />)
    const header = screen.getByRole('button', { name: /Tests/ })
    expect(header).toHaveAttribute('aria-expanded', 'true')
  })

  it('renders the chevron without open class when collapsed', () => {
    render(<CollapsibleCode title="Tests" code="test code" />)
    const chevron = document.querySelector('.collapsible-chevron')
    expect(chevron).not.toHaveClass('open')
  })

  it('adds open class to chevron when expanded', async () => {
    const user = userEvent.setup()
    render(<CollapsibleCode title="Tests" code="test code" />)

    await user.click(screen.getByRole('button', { name: /Tests/ }))

    const chevron = document.querySelector('.collapsible-chevron')
    expect(chevron).toHaveClass('open')
  })

  it('renders the code content in the DOM even when collapsed', () => {
    render(<CollapsibleCode title="Tests" code="some test code here" />)
    expect(screen.getByText('some test code here')).toBeInTheDocument()
  })
})
