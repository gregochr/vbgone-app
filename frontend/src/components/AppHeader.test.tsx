import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { AppHeader } from './AppHeader'

// A controllable config for the header's useWizardConfig(). vi.hoisted keeps it
// available to the (hoisted) vi.mock factory without a TDZ error.
const mocks = vi.hoisted(() => ({
  config: {
    mode: 'protect',
    targetLanguage: 'csharp',
    provider: 'copilot',
    modelOverrides: {} as Record<string, string>,
    currentStep: 0,
    setMode: vi.fn(),
    setTargetLanguage: vi.fn(),
    openEngine: vi.fn(),
    sessionCost: 0,
  },
}))

vi.mock('../config/WizardConfigContext', () => ({
  useWizardConfig: () => mocks.config,
}))

beforeEach(() => {
  Object.assign(mocks.config, {
    mode: 'protect',
    provider: 'copilot',
    modelOverrides: {},
    currentStep: 0,
    sessionCost: 0,
  })
  vi.clearAllMocks()
})

const engineButton = () => screen.getByText('Copilot').closest('button') as HTMLButtonElement

describe('AppHeader — Protect engine lock', () => {
  it('lets you change the engine on Protect step 1 (Upload)', () => {
    mocks.config.mode = 'protect'
    mocks.config.currentStep = 0
    render(<AppHeader />)
    const btn = engineButton()
    expect(btn).not.toHaveAttribute('aria-disabled')
    expect(btn.className).not.toContain('locked')
    fireEvent.click(btn)
    expect(mocks.config.openEngine).toHaveBeenCalledTimes(1)
  })

  // The lock boundary: step 1 (Upload, index 0) is the only unlocked step, so
  // the very next step — Readiness, index 1 — must already be locked. This case
  // guards against an off-by-one (currentStep > 1) that would leave it open.
  it('locks the engine on the step immediately after Upload (Readiness, index 1)', () => {
    mocks.config.mode = 'protect'
    mocks.config.currentStep = 1
    render(<AppHeader />)
    const btn = engineButton()
    expect(btn).toHaveAttribute('aria-disabled', 'true')
    expect(btn.className).toContain('locked')
    fireEvent.click(btn)
    expect(mocks.config.openEngine).not.toHaveBeenCalled()
  })

  it('keeps the engine locked deeper into the Protect run (Baseline, index 2)', () => {
    mocks.config.mode = 'protect'
    mocks.config.currentStep = 2
    render(<AppHeader />)
    const btn = engineButton()
    expect(btn).toHaveAttribute('aria-disabled', 'true')
    expect(btn.className).toContain('locked')
    expect(btn).toHaveAttribute('title')
    fireEvent.click(btn)
    expect(mocks.config.openEngine).not.toHaveBeenCalled()
  })

  it('never locks the engine in Migrate mode, even on later steps', () => {
    mocks.config.mode = 'migrate'
    mocks.config.currentStep = 4
    render(<AppHeader />)
    const btn = engineButton()
    expect(btn).not.toHaveAttribute('aria-disabled')
    fireEvent.click(btn)
    expect(mocks.config.openEngine).toHaveBeenCalledTimes(1)
  })
})
