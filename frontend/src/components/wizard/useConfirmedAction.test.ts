import { describe, it, expect, vi } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { useConfirmedAction } from './useConfirmedAction'

describe('useConfirmedAction', () => {
  const setup = (over: Partial<Parameters<typeof useConfirmedAction>[0]> = {}) => {
    const onResult = vi.fn()
    const onReady = vi.fn()
    const action = vi.fn().mockResolvedValue('RESULT')
    const opts = {
      alreadyDone: false,
      action,
      onResult,
      onReady,
      errorMessage: 'fallback',
      ...over,
    }
    const hook = renderHook(() => useConfirmedAction(opts))
    return { hook, onResult, onReady, action }
  }

  it('starts in confirming and does not ready on mount when not already done', () => {
    const { hook, onReady } = setup()
    expect(hook.result.current.confirming).toBe(true)
    expect(hook.result.current.loading).toBe(false)
    expect(onReady).not.toHaveBeenCalled()
  })

  it('skips confirm and readies on mount when already done', () => {
    const { hook, onReady } = setup({ alreadyDone: true })
    expect(hook.result.current.confirming).toBe(false)
    expect(onReady).toHaveBeenCalledTimes(1)
  })

  it('run() executes the action, applies the result, and readies', async () => {
    const { hook, onResult, onReady, action } = setup()

    act(() => {
      hook.result.current.run()
    })
    // Synchronously flips into the loading state and closes the confirm.
    expect(hook.result.current.loading).toBe(true)
    expect(hook.result.current.confirming).toBe(false)

    await waitFor(() => expect(hook.result.current.loading).toBe(false))
    expect(action).toHaveBeenCalledTimes(1)
    expect(onResult).toHaveBeenCalledWith('RESULT')
    expect(onReady).toHaveBeenCalledTimes(1)
    expect(hook.result.current.error).toBeNull()
  })

  it('surfaces an Error message on failure and stops loading', async () => {
    const action = vi.fn().mockRejectedValue(new Error('boom'))
    const { hook, onResult } = setup({ action })

    act(() => {
      hook.result.current.run()
    })

    await waitFor(() => expect(hook.result.current.error).toBe('boom'))
    expect(hook.result.current.loading).toBe(false)
    expect(onResult).not.toHaveBeenCalled()
  })

  it('falls back to errorMessage for a non-Error rejection', async () => {
    const action = vi.fn().mockRejectedValue('nope')
    const { hook } = setup({ action, errorMessage: 'fallback msg' })

    act(() => {
      hook.result.current.run()
    })

    await waitFor(() => expect(hook.result.current.error).toBe('fallback msg'))
  })

  it('requestConfirm and cancel toggle the confirm dialog', () => {
    const { hook } = setup({ alreadyDone: true })
    expect(hook.result.current.confirming).toBe(false)

    act(() => {
      hook.result.current.requestConfirm()
    })
    expect(hook.result.current.confirming).toBe(true)

    act(() => {
      hook.result.current.cancel()
    })
    expect(hook.result.current.confirming).toBe(false)
  })
})
