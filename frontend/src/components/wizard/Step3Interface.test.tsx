import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Step3Interface } from './Step3Interface'
import type { WizardState } from './WizardShell'
import * as api from '../../api/migrateApi'

vi.mock('../../api/migrateApi', async () => {
  const actual = await vi.importActual('../../api/migrateApi')
  return { ...actual, generateInterface: vi.fn() }
})

const baseState: WizardState = {
  filename: 'Test.vb',
  content: '',
  analysis: {
    sessionId: 'session-1',
    classes: [{ name: 'Foo', methods: ['Bar'], dependencies: [], complexity: 'LOW' }],
    suggestedMigrationOrder: ['Foo'],
    summary: 'Test',
  },
  currentClassIndex: 0,
  completedClasses: [],
  interfaceResult: null,
  tests: null,
  stubResult: null,
  redBuild: null,
  implementResult: null,
  greenBuild: null,
  prResult: null,
  zipFile: null,
  readiness: null,
  baselineResult: null,
  baselineTests: null,
  netFaithful: true,
  netted: [],
  fromQueue: false,
}

const mockInterface: api.InterfaceResult = {
  sessionId: 'session-1',
  className: 'Foo',
  interfaceName: 'IFoo',
  code: 'public interface IFoo { int Bar(); }',
}

describe('Step3Interface', () => {
  it('renders correctly with interface data already in state', () => {
    const stateWithIface = { ...baseState, interfaceResult: mockInterface }
    render(<Step3Interface state={stateWithIface} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('IFoo.cs')).toBeInTheDocument()
    expect(screen.getByText(/Generated C# interface for Foo/)).toBeInTheDocument()
  })

  it('displays mocked API response data', () => {
    const stateWithIface = { ...baseState, interfaceResult: mockInterface }
    render(<Step3Interface state={stateWithIface} update={vi.fn()} onReady={vi.fn()} />)
    // Code is syntax-highlighted, so the text is split across token spans —
    // match the <pre> element's aggregate content rather than a single text node.
    expect(
      screen.getByText(
        (_, el) =>
          el?.tagName === 'PRE' && el.textContent === 'public interface IFoo { int Bar(); }',
      ),
    ).toBeInTheDocument()
  })

  it('shows confirm dialog before making API call', () => {
    render(<Step3Interface state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    expect(
      screen.getByText(
        /This will make an API call to Claude \(claude-haiku-4-5\) via the Anthropic provider/,
      ),
    ).toBeInTheDocument()
    expect(screen.getAllByText(/claude-haiku-4-5/).length).toBeGreaterThan(0)
    expect(screen.getByText('Continue')).toBeInTheDocument()
  })

  it('shows loading state after clicking Continue', async () => {
    const user = userEvent.setup()
    vi.mocked(api.generateInterface).mockReturnValue(new Promise(() => {}))
    render(<Step3Interface state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    await user.click(screen.getByText('Continue'))
    expect(screen.getByText(/Generating the C# interface for Foo/)).toBeInTheDocument()
  })

  it('shows error state if API call fails', async () => {
    const user = userEvent.setup()
    vi.mocked(api.generateInterface).mockRejectedValue(new Error('Timeout'))
    render(<Step3Interface state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    await user.click(screen.getByText('Continue'))
    await waitFor(() => {
      expect(screen.getByText('Timeout')).toBeInTheDocument()
    })
  })

  it('calls update and onReady after successful API call', async () => {
    const user = userEvent.setup()
    vi.mocked(api.generateInterface).mockResolvedValue(mockInterface)
    const update = vi.fn()
    const onReady = vi.fn()
    render(<Step3Interface state={baseState} update={update} onReady={onReady} />)
    await user.click(screen.getByText('Continue'))
    await waitFor(() => {
      expect(update).toHaveBeenCalledWith({ interfaceResult: mockInterface })
      expect(onReady).toHaveBeenCalled()
    })
  })

  it('shows god class warning when complexity is HIGH', () => {
    const highState: WizardState = {
      ...baseState,
      analysis: {
        ...baseState.analysis!,
        classes: [{ ...baseState.analysis!.classes[0], complexity: 'HIGH' }],
      },
      interfaceResult: mockInterface,
    }
    render(<Step3Interface state={highState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByTestId('god-class-warning')).toBeInTheDocument()
    expect(screen.getByText(/Complex class detected/)).toBeInTheDocument()
    expect(screen.getByText(/Strangler Fig pattern/)).toBeInTheDocument()
  })

  it('shows god class warning when code quality is POOR', () => {
    const poorState: WizardState = {
      ...baseState,
      analysis: {
        ...baseState.analysis!,
        classes: [{ ...baseState.analysis!.classes[0], codeQuality: 'POOR' }],
      },
      interfaceResult: mockInterface,
    }
    render(<Step3Interface state={poorState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByTestId('god-class-warning')).toBeInTheDocument()
  })

  it('does not show god class warning for LOW complexity GOOD quality', () => {
    const goodState: WizardState = {
      ...baseState,
      analysis: {
        ...baseState.analysis!,
        classes: [{ ...baseState.analysis!.classes[0], complexity: 'LOW', codeQuality: 'GOOD' }],
      },
      interfaceResult: mockInterface,
    }
    render(<Step3Interface state={goodState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.queryByTestId('god-class-warning')).not.toBeInTheDocument()
  })

  it('does not show god class warning when quality is undefined and complexity is LOW', () => {
    const stateWithIface = { ...baseState, interfaceResult: mockInterface }
    render(<Step3Interface state={stateWithIface} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.queryByTestId('god-class-warning')).not.toBeInTheDocument()
  })

  it('shows retry button after cancelling confirm dialog', async () => {
    const user = userEvent.setup()
    render(<Step3Interface state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('Continue')).toBeInTheDocument()
    await user.click(screen.getByText('Cancel'))
    expect(screen.getByText('Generate interface')).toBeInTheDocument()
  })

  it('re-shows confirm dialog after clicking retry button', async () => {
    const user = userEvent.setup()
    render(<Step3Interface state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    await user.click(screen.getByText('Cancel'))
    await user.click(screen.getByText('Generate interface'))
    expect(screen.getByText('Continue')).toBeInTheDocument()
    expect(screen.getAllByText(/claude-haiku-4-5/).length).toBeGreaterThan(0)
  })

  it('uses currentClassIndex for class name in multi-class mode', () => {
    const multiClassState: WizardState = {
      ...baseState,
      analysis: {
        sessionId: 'session-1',
        classes: [
          { name: 'Foo', methods: ['Bar'], dependencies: [], complexity: 'LOW' },
          { name: 'Baz', methods: ['Qux'], dependencies: ['Foo'], complexity: 'MEDIUM' },
        ],
        suggestedMigrationOrder: ['Foo', 'Baz'],
        summary: 'Two classes',
      },
      currentClassIndex: 1,
      interfaceResult: {
        sessionId: 'session-1',
        className: 'Baz',
        interfaceName: 'IBaz',
        code: 'public interface IBaz { int Qux(); }',
      },
    }
    render(<Step3Interface state={multiClassState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('IBaz.cs')).toBeInTheDocument()
    expect(screen.getByText(/Generated C# interface for Baz/)).toBeInTheDocument()
  })

  it('Strangler Fig pattern links to Martin Fowler', () => {
    const highState: WizardState = {
      ...baseState,
      analysis: {
        ...baseState.analysis!,
        classes: [{ ...baseState.analysis!.classes[0], complexity: 'HIGH' }],
      },
      interfaceResult: mockInterface,
    }
    render(<Step3Interface state={highState} update={vi.fn()} onReady={vi.fn()} />)
    const link = screen.getByRole('link', { name: /Strangler Fig pattern/ })
    expect(link).toHaveAttribute(
      'href',
      'https://martinfowler.com/bliki/StranglerFigApplication.html',
    )
  })
})
