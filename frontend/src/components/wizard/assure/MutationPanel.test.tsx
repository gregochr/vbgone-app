import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MutationPanel } from './MutationPanel'
import * as api from '../../../api/migrateApi'
import type { MutationJobStatus } from '../../../api/migrateApi'

vi.mock('../../../api/migrateApi', () => ({
  startMutationTest: vi.fn(),
  getMutationJob: vi.fn(),
}))

const startMock = vi.mocked(api.startMutationTest)
const pollMock = vi.mocked(api.getMutationJob)

const running = (done: number, total: number): MutationJobStatus => ({
  jobId: 'j1',
  state: 'RUNNING',
  done,
  total,
  result: null,
  error: null,
})

const done = (): MutationJobStatus => ({
  jobId: 'j1',
  state: 'DONE',
  done: 4,
  total: 4,
  result: {
    total: 4,
    killed: 3,
    survived: 1,
    skipped: 0,
    score: 75,
    survivors: [
      {
        line: 41,
        operator: 'relational',
        before: '<=',
        after: '<',
        description: 'boundary went unchecked',
      },
    ],
  },
  error: null,
})

function panel() {
  render(<MutationPanel sessionId="s1" className="C" suiteCode="code" pollMs={5} />)
}

describe('MutationPanel', () => {
  beforeEach(() => {
    startMock.mockReset()
    pollMock.mockReset()
  })

  it('offers a "Prove the net" button up front', () => {
    panel()
    expect(screen.getByTestId('mutation-start')).toHaveTextContent('Prove the net')
    expect(screen.getByTestId('mutation-panel')).toHaveTextContent('notice a change')
  })

  it('runs, then shows the score and surviving mutants', async () => {
    startMock.mockResolvedValue(running(0, 4))
    pollMock.mockResolvedValue(done())

    panel()
    await userEvent.click(screen.getByTestId('mutation-start'))

    await waitFor(() => expect(screen.getByTestId('mutation-result')).toBeInTheDocument())
    expect(screen.getByText('75%')).toBeInTheDocument()
    expect(screen.getByTestId('mutation-result')).toHaveTextContent('3')
    expect(screen.getByText(/boundary went unchecked/)).toBeInTheDocument()
  })

  it('shows a clean result when the net kills everything', async () => {
    startMock.mockResolvedValue(running(0, 2))
    pollMock.mockResolvedValue({
      jobId: 'j1',
      state: 'DONE',
      done: 2,
      total: 2,
      result: { total: 2, killed: 2, survived: 0, skipped: 0, score: 100, survivors: [] },
      error: null,
    })

    panel()
    await userEvent.click(screen.getByTestId('mutation-start'))

    await waitFor(() => expect(screen.getByText('100%')).toBeInTheDocument())
    expect(screen.getByText(/No survivors/)).toBeInTheDocument()
  })

  it('surfaces a failed job (e.g. baseline not green)', async () => {
    startMock.mockResolvedValue({
      jobId: 'j1',
      state: 'FAILED',
      done: 0,
      total: 0,
      result: null,
      error: 'Baseline is RED, not GREEN — get the baseline green first.',
    })

    panel()
    await userEvent.click(screen.getByTestId('mutation-start'))

    await waitFor(() => expect(screen.getByTestId('mutation-failed')).toBeInTheDocument())
    expect(screen.getByText(/not GREEN/)).toBeInTheDocument()
    // Never started polling since the start response was terminal.
    expect(pollMock).not.toHaveBeenCalled()
  })
})
