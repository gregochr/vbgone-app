import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { AppHeader } from './AppHeader'

// A controllable config for the header's useWizardConfig(). vi.hoisted keeps it
// available to the (hoisted) vi.mock factory without a TDZ error.
const mocks = vi.hoisted(() => ({
  config: {
    mode: 'assure',
    targetLanguage: 'csharp',
    provider: 'copilot',
    modelOverrides: {} as Record<string, string>,
    runner: 'linux' as 'linux' | 'windows',
    currentStep: 0,
    setMode: vi.fn(),
    setTargetLanguage: vi.fn(),
    setRunner: vi.fn(),
    openEngine: vi.fn(),
    sessionCost: 0,
  },
}))

vi.mock('../config/WizardConfigContext', () => ({
  useWizardConfig: () => mocks.config,
}))

beforeEach(() => {
  Object.assign(mocks.config, {
    mode: 'assure',
    provider: 'copilot',
    modelOverrides: {},
    runner: 'linux',
    currentStep: 0,
    sessionCost: 0,
  })
  vi.clearAllMocks()
})

const runnerSeg = (label: 'Linux' | 'Windows') =>
  screen.getByText(label).closest('button') as HTMLButtonElement

const engineButton = () => screen.getByText('Copilot').closest('button') as HTMLButtonElement

describe('AppHeader — Assure engine lock', () => {
  it('lets you change the engine on Assure step 1 (Upload)', () => {
    mocks.config.mode = 'assure'
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
    mocks.config.mode = 'assure'
    mocks.config.currentStep = 1
    render(<AppHeader />)
    const btn = engineButton()
    expect(btn).toHaveAttribute('aria-disabled', 'true')
    expect(btn.className).toContain('locked')
    fireEvent.click(btn)
    expect(mocks.config.openEngine).not.toHaveBeenCalled()
  })

  it('keeps the engine locked deeper into the Assure run (Baseline, index 2)', () => {
    mocks.config.mode = 'assure'
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

describe('AppHeader — RUNNER toggle', () => {
  it('lets you pick Windows in Assure on step 1 (Upload)', () => {
    mocks.config.mode = 'assure'
    mocks.config.currentStep = 0
    render(<AppHeader />)
    expect(runnerSeg('Linux').className).toContain('active')
    const win = runnerSeg('Windows')
    expect(win).not.toHaveAttribute('aria-disabled')
    fireEvent.click(win)
    expect(mocks.config.setRunner).toHaveBeenCalledWith('windows')
  })

  it('fixes the runner once the Assure run is under way (Readiness, index 1)', () => {
    mocks.config.mode = 'assure'
    mocks.config.currentStep = 1
    render(<AppHeader />)
    const win = runnerSeg('Windows')
    expect(win).toHaveAttribute('aria-disabled', 'true')
    fireEvent.click(win)
    expect(mocks.config.setRunner).not.toHaveBeenCalled()
  })

  it('locks Windows in Migrate mode and shows Linux as the runner', () => {
    mocks.config.mode = 'migrate'
    mocks.config.runner = 'windows' // even if a prior Assure run chose Windows
    mocks.config.currentStep = 0
    render(<AppHeader />)
    expect(runnerSeg('Linux').className).toContain('active')
    const win = runnerSeg('Windows')
    expect(win).toHaveAttribute('aria-disabled', 'true')
    expect(win).toHaveAttribute('title')
    fireEvent.click(win)
    expect(mocks.config.setRunner).not.toHaveBeenCalled()
  })
})
