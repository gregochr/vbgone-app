import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import App from './App'

describe('App', () => {
  it('renders the wordmark in the header', () => {
    render(<App />)
    expect(screen.getByText('vbgone')).toBeInTheDocument()
  })

  it('renders the target language toggle and engine button', () => {
    render(<App />)
    // Target segmented control
    expect(screen.getByRole('button', { name: 'C#' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Java/ })).toBeInTheDocument()
    // Engine button shows the active provider
    expect(screen.getByText('Claude')).toBeInTheDocument()
  })

  it('renders the wizard shell on the first step', () => {
    render(<App />)
    expect(screen.getByText('Upload legacy VB.NET')).toBeInTheDocument()
  })
})
