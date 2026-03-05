import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
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
  interfaceResult: null,
  tests: null,
  redBuild: null,
  implementResult: null,
  greenBuild: null,
  prResult: null,
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
    expect(screen.getByText('IFoo')).toBeInTheDocument()
    expect(screen.getByText(/Generated C# interface for Foo/)).toBeInTheDocument()
  })

  it('displays mocked API response data', () => {
    const stateWithIface = { ...baseState, interfaceResult: mockInterface }
    render(<Step3Interface state={stateWithIface} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('public interface IFoo { int Bar(); }')).toBeInTheDocument()
  })

  it('shows loading state while API call is in progress', () => {
    vi.mocked(api.generateInterface).mockReturnValue(new Promise(() => {}))
    render(<Step3Interface state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    expect(screen.getByText('Generating C# Interface')).toBeInTheDocument()
    expect(screen.getByText(/Claude is generating the interface for Foo/)).toBeInTheDocument()
  })

  it('shows error state if API call fails', async () => {
    vi.mocked(api.generateInterface).mockRejectedValue(new Error('Timeout'))
    render(<Step3Interface state={baseState} update={vi.fn()} onReady={vi.fn()} />)
    await waitFor(() => {
      expect(screen.getByText('Interface Generation Failed')).toBeInTheDocument()
      expect(screen.getByText('Timeout')).toBeInTheDocument()
    })
  })

  it('calls update and onReady after successful API call', async () => {
    vi.mocked(api.generateInterface).mockResolvedValue(mockInterface)
    const update = vi.fn()
    const onReady = vi.fn()
    render(<Step3Interface state={baseState} update={update} onReady={onReady} />)
    await waitFor(() => {
      expect(update).toHaveBeenCalledWith({ interfaceResult: mockInterface })
      expect(onReady).toHaveBeenCalled()
    })
  })
})
