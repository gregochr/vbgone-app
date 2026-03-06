import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { InfoTip } from './InfoTip'

describe('InfoTip', () => {
  it('renders the trigger button', () => {
    render(<InfoTip>Some help text</InfoTip>)
    expect(screen.getByRole('button', { name: 'More info' })).toBeInTheDocument()
  })

  it('does not show popover by default', () => {
    render(<InfoTip>Some help text</InfoTip>)
    expect(screen.queryByText('Some help text')).not.toBeInTheDocument()
  })

  it('shows popover when trigger is clicked', async () => {
    const user = userEvent.setup()
    render(<InfoTip>Some help text</InfoTip>)
    await user.click(screen.getByRole('button', { name: 'More info' }))
    expect(screen.getByText('Some help text')).toBeInTheDocument()
  })

  it('hides popover when close button is clicked', async () => {
    const user = userEvent.setup()
    render(<InfoTip>Some help text</InfoTip>)
    await user.click(screen.getByRole('button', { name: 'More info' }))
    expect(screen.getByText('Some help text')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Close' }))
    expect(screen.queryByText('Some help text')).not.toBeInTheDocument()
  })

  it('hides popover when clicking outside', async () => {
    const user = userEvent.setup()
    render(
      <div>
        <InfoTip>Some help text</InfoTip>
        <span>Outside</span>
      </div>,
    )
    await user.click(screen.getByRole('button', { name: 'More info' }))
    expect(screen.getByText('Some help text')).toBeInTheDocument()
    await user.click(screen.getByText('Outside'))
    expect(screen.queryByText('Some help text')).not.toBeInTheDocument()
  })

  it('toggles popover on repeated trigger clicks', async () => {
    const user = userEvent.setup()
    render(<InfoTip>Some help text</InfoTip>)
    const trigger = screen.getByRole('button', { name: 'More info' })
    await user.click(trigger)
    expect(screen.getByText('Some help text')).toBeInTheDocument()
    await user.click(trigger)
    expect(screen.queryByText('Some help text')).not.toBeInTheDocument()
  })

  it('renders custom label', () => {
    render(<InfoTip label="?">Some help text</InfoTip>)
    expect(screen.getByRole('button', { name: 'More info' })).toHaveTextContent('?')
  })
})
