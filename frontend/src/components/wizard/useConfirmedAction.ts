import { useEffect, useState } from 'react'

/**
 * The confirm → loading → error → done state machine shared by the single-action
 * wizard steps (analyse, generate interface, record baseline, raise PR).
 *
 * Each of those steps ran the identical shape by hand: three `useState`s, a mount
 * effect that calls `onReady()` when the step's result is already in state, and a
 * runner that flips `showConfirm`/`loading`, calls one async API, then either
 * `update`s + `onReady`s or surfaces the error message. This centralises that.
 *
 * The step still owns all its JSX (confirm copy, run card, result) — it just reads
 * `confirming`/`loading`/`error` and wires the buttons to `requestConfirm`/`cancel`/`run`.
 */
export interface ConfirmedAction {
  /** Show the confirm dialog (initially true unless the step is already done). */
  confirming: boolean
  /** The async action is in flight. */
  loading: boolean
  /** The action failed; the surfaced message (or null). */
  error: string | null
  /** Open the confirm dialog — wire to the run-card button. */
  requestConfirm: () => void
  /** Dismiss the confirm dialog without running. */
  cancel: () => void
  /** Confirm and execute the async action. */
  run: () => void
}

export function useConfirmedAction<T>(opts: {
  /** Whether the step's result is already present (skips the confirm on mount). */
  alreadyDone: boolean
  /** The single async API call this step performs. */
  action: () => Promise<T>
  /** Apply the successful result (typically an `update({ ... })`). */
  onResult: (result: T) => void
  /** Advance the wizard once the result exists (on mount if already done, else on success). */
  onReady: () => void
  /** Fallback message when the thrown error is not an `Error`. */
  errorMessage: string
}): ConfirmedAction {
  const { alreadyDone, action, onResult, onReady, errorMessage } = opts
  const [confirming, setConfirming] = useState(!alreadyDone)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (alreadyDone) onReady()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const run = () => {
    setConfirming(false)
    setLoading(true)
    action()
      .then((result) => {
        onResult(result)
        setLoading(false)
        onReady()
      })
      .catch((err) => {
        setLoading(false)
        setError(err instanceof Error ? err.message : errorMessage)
      })
  }

  return {
    confirming,
    loading,
    error,
    requestConfirm: () => setConfirming(true),
    cancel: () => setConfirming(false),
    run,
  }
}
