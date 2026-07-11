import { useEffect, useRef, useState } from 'react'

/**
 * One stage of a {@link useConfirmedPipeline}: a labelled async call whose result is applied
 * (typically via `update({ ... })`) before the next stage runs.
 */
export interface PipelineStep {
  /** Loading-message label for this stage. */
  phase: string
  action: () => Promise<unknown>
  onResult: (result: unknown) => void
}

/**
 * Type-safe constructor for a pipeline stage — pairs a call's result type with its handler so the
 * heterogeneous `steps` array stays checked at the call site while the hook runs it as `unknown`.
 */
export function pipelineStep<T>(
  phase: string,
  action: () => Promise<T>,
  onResult: (result: T) => void,
): PipelineStep {
  return { phase, action, onResult } as PipelineStep
}

export interface ConfirmedPipeline {
  /** Show the confirm dialog (initially true unless the step is already done). */
  confirming: boolean
  /** The label of the stage currently running, or null when idle/done. */
  phase: string | null
  /** The pipeline failed; the surfaced message (or null). */
  error: string | null
  /** Open the confirm dialog — wire to the run-card button. */
  requestConfirm: () => void
  /** Dismiss the confirm dialog without running. */
  cancel: () => void
  /** Confirm and run the stages in order. */
  run: () => void
}

/**
 * A confirm gate in front of an ordered list of async stages — a single-element list covers the
 * plain single-action steps (analyse, generate interface, record baseline, raise PR), which read
 * `loading` as `phase !== null`. Each stage's result is applied before the next call, one error
 * string is surfaced on any failure, and `onReady()` fires after the last stage (and on mount if
 * the step is already done). A `mounted` ref guards every post-await write, so an unmount partway
 * through the pipeline can't set state on a dead component.
 *
 * The step still owns its JSX — it reads `confirming`/`phase`/`error` and wires the buttons.
 */
export function useConfirmedPipeline(
  steps: readonly PipelineStep[],
  opts: { alreadyDone: boolean; onReady: () => void; errorMessage: string },
): ConfirmedPipeline {
  const { alreadyDone, onReady, errorMessage } = opts
  const [confirming, setConfirming] = useState(!alreadyDone)
  const [phase, setPhase] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const mounted = useRef(true)
  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  useEffect(() => {
    if (alreadyDone) onReady()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const run = () => {
    setConfirming(false)
    void (async () => {
      try {
        for (const step of steps) {
          setPhase(step.phase)
          const result = await step.action()
          if (!mounted.current) return
          step.onResult(result)
        }
        if (!mounted.current) return
        setPhase(null)
        onReady()
      } catch (err) {
        if (!mounted.current) return
        setPhase(null)
        setError(err instanceof Error ? err.message : errorMessage)
      }
    })()
  }

  return {
    confirming,
    phase,
    error,
    requestConfirm: () => setConfirming(true),
    cancel: () => setConfirming(false),
    run,
  }
}
