import { describe, it, expect, vi } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { useConfirmedPipeline, pipelineStep } from './useConfirmedPipeline'

describe('useConfirmedPipeline', () => {
  it('starts confirming when not already done and does not ready on mount', () => {
    const onReady = vi.fn()
    const { result } = renderHook(() =>
      useConfirmedPipeline([], { alreadyDone: false, onReady, errorMessage: 'x' }),
    )
    expect(result.current.confirming).toBe(true)
    expect(result.current.phase).toBeNull()
    expect(onReady).not.toHaveBeenCalled()
  })

  it('skips confirm and readies on mount when already done', () => {
    const onReady = vi.fn()
    const { result } = renderHook(() =>
      useConfirmedPipeline([], { alreadyDone: true, onReady, errorMessage: 'x' }),
    )
    expect(result.current.confirming).toBe(false)
    expect(onReady).toHaveBeenCalledTimes(1)
  })

  it('runs the stages in order, applies each result, then readies', async () => {
    const applied: string[] = []
    const onReady = vi.fn()
    const steps = [
      pipelineStep(
        'one',
        () => Promise.resolve('R1'),
        (r) => applied.push(`${r}`),
      ),
      pipelineStep(
        'two',
        () => Promise.resolve('R2'),
        (r) => applied.push(`${r}`),
      ),
    ]
    const { result } = renderHook(() =>
      useConfirmedPipeline(steps, { alreadyDone: false, onReady, errorMessage: 'x' }),
    )

    act(() => result.current.run())
    expect(result.current.confirming).toBe(false)

    await waitFor(() => expect(onReady).toHaveBeenCalledTimes(1))
    expect(applied).toEqual(['R1', 'R2']) // ordered, one result applied per stage
    expect(result.current.phase).toBeNull()
    expect(result.current.error).toBeNull()
  })

  it('stops at the failing stage, surfaces its message, and skips later stages', async () => {
    const later = vi.fn()
    const onReady = vi.fn()
    const steps = [
      pipelineStep(
        'one',
        () => Promise.reject(new Error('boom')),
        () => {},
      ),
      pipelineStep('two', () => Promise.resolve('R2'), later),
    ]
    const { result } = renderHook(() =>
      useConfirmedPipeline(steps, { alreadyDone: false, onReady, errorMessage: 'fallback' }),
    )

    act(() => result.current.run())

    await waitFor(() => expect(result.current.error).toBe('boom'))
    expect(result.current.phase).toBeNull()
    expect(later).not.toHaveBeenCalled()
    expect(onReady).not.toHaveBeenCalled()
  })

  it('falls back to errorMessage for a non-Error rejection', async () => {
    const steps = [
      pipelineStep(
        'one',
        () => Promise.reject('nope'),
        () => {},
      ),
    ]
    const { result } = renderHook(() =>
      useConfirmedPipeline(steps, {
        alreadyDone: false,
        onReady: vi.fn(),
        errorMessage: 'fallback msg',
      }),
    )
    act(() => result.current.run())
    await waitFor(() => expect(result.current.error).toBe('fallback msg'))
  })

  it('requestConfirm and cancel toggle the confirm gate', () => {
    const { result } = renderHook(() =>
      useConfirmedPipeline([], { alreadyDone: true, onReady: vi.fn(), errorMessage: 'x' }),
    )
    expect(result.current.confirming).toBe(false)
    act(() => result.current.requestConfirm())
    expect(result.current.confirming).toBe(true)
    act(() => result.current.cancel())
    expect(result.current.confirming).toBe(false)
  })

  it('does not apply a result or ready after unmount (mounted guard)', async () => {
    const applied = vi.fn()
    const onReady = vi.fn()
    let resolveFirst: (v: unknown) => void = () => {}
    const steps = [
      pipelineStep(
        'one',
        () =>
          new Promise((res) => {
            resolveFirst = res
          }),
        applied,
      ),
    ]
    const { result, unmount } = renderHook(() =>
      useConfirmedPipeline(steps, { alreadyDone: false, onReady, errorMessage: 'x' }),
    )

    act(() => result.current.run())
    unmount()
    await act(async () => {
      resolveFirst('R1')
      await Promise.resolve()
      await Promise.resolve()
    })
    expect(applied).not.toHaveBeenCalled()
    expect(onReady).not.toHaveBeenCalled()
  })
})
