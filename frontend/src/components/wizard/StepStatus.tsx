import type { ReactNode } from 'react'

/**
 * The loading-spinner and error views shared verbatim by the single-action wizard
 * steps, rendered under the step's own header. Render this when the step's action is
 * running or has failed (`loading || error`); the step renders its own idle/result
 * content otherwise.
 */
export function StepStatus({
  header,
  loading,
  loadingText,
  error,
}: {
  header: ReactNode
  loading: boolean
  loadingText: ReactNode
  error: string | null
}) {
  return (
    <div>
      {header}
      {loading ? (
        <div className="busy-row">
          <span className="spinner" />
          <span className="loading-text">{loadingText}</span>
        </div>
      ) : (
        <div className="build-status build-red">{error}</div>
      )}
    </div>
  )
}
