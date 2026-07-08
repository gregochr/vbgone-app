import { useEffect, useRef, useState } from 'react'
import { startMutationTest, getMutationJob } from '../../../api/migrateApi'
import type { MutationJobStatus } from '../../../api/migrateApi'

interface Props {
  sessionId: string
  className: string
  /** The green characterisation suite to mutate against. */
  suiteCode: string
  /** Poll interval; overridable so tests don't wait real seconds. */
  pollMs?: number
}

/**
 * "Prove the net" — runs mutation testing over a green Assure baseline (ADR-0001, Path B). Starts
 * an async job, polls it, and shows the mutation score plus the surviving mutants (the net's blind
 * spots). Informational only; it never changes the GREEN/RED verdict.
 */
export function MutationPanel({ sessionId, className, suiteCode, pollMs = 1200 }: Props) {
  const [job, setJob] = useState<MutationJobStatus | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [starting, setStarting] = useState(false)
  const timer = useRef<ReturnType<typeof setInterval> | null>(null)

  const stop = () => {
    if (timer.current) {
      clearInterval(timer.current)
      timer.current = null
    }
  }
  useEffect(() => stop, [])

  const run = async () => {
    setError(null)
    setStarting(true)
    try {
      const initial = await startMutationTest(sessionId, className, suiteCode)
      setJob(initial)
      setStarting(false)
      if (initial.state === 'DONE' || initial.state === 'FAILED') return
      timer.current = setInterval(async () => {
        try {
          const next = await getMutationJob(initial.jobId)
          setJob(next)
          if (next.state === 'DONE' || next.state === 'FAILED') stop()
        } catch (e) {
          stop()
          setError(e instanceof Error ? e.message : 'Polling mutation testing failed')
        }
      }, pollMs)
    } catch (e) {
      setStarting(false)
      setError(e instanceof Error ? e.message : 'Could not start mutation testing')
    }
  }

  const inProgress = starting || job?.state === 'PENDING' || job?.state === 'RUNNING'
  const result = job?.state === 'DONE' ? job.result : null

  return (
    <div className="mutation-panel" data-testid="mutation-panel">
      {!job && !error && (
        <>
          <div className="mutation-intro">
            <strong>Prove the net.</strong> Mutation testing deliberately injects small regressions
            into your original VB.NET and checks the net catches each one. Coverage shows a line{' '}
            <em>ran</em>; this shows a test would actually <em>notice a change</em>.
          </div>
          <button
            className="btn-plex"
            onClick={run}
            disabled={starting}
            data-testid="mutation-start"
          >
            {starting ? 'Starting…' : 'Prove the net'}
          </button>
        </>
      )}

      {job && inProgress && (
        <div className="mutation-progress" data-testid="mutation-progress">
          <div className="mutation-progress-bar">
            <div className="mutation-progress-fill" style={{ width: `${percent(job)}%` }} />
          </div>
          <span className="mutation-progress-text">
            Injecting regressions… {job.done} / {job.total || '…'}
          </span>
        </div>
      )}

      {job?.state === 'FAILED' && (
        <div className="mutation-failed" data-testid="mutation-failed">
          ⚠ {job.error ?? 'Mutation testing failed.'}
        </div>
      )}

      {result && (
        <div className="mutation-result" data-testid="mutation-result">
          <div className={`mutation-score ${scoreClass(result.score)}`}>
            <span className="mutation-score-value">
              {result.score === null ? '—' : `${result.score}%`}
            </span>
            <span className="mutation-score-label">mutation score</span>
          </div>
          <div className="mutation-tally">
            <strong>{result.killed}</strong> killed · <strong>{result.survived}</strong> survived ·{' '}
            {result.skipped} skipped, of {result.total} mutants
          </div>
          {result.survivors.length > 0 ? (
            <div className="mutation-survivors">
              <div className="mutation-survivors-head">Blind spots — the net missed these:</div>
              <ul>
                {result.survivors.map((s, i) => (
                  <li key={i}>
                    <code>line {s.line}</code> <code>{s.before}</code> → <code>{s.after}</code>
                    <span className="mutation-survivor-desc"> — {s.description}</span>
                  </li>
                ))}
              </ul>
            </div>
          ) : (
            <div className="mutation-clean">
              ✔ No survivors — the net caught every injected regression.
            </div>
          )}
        </div>
      )}

      {error && (
        <div className="mutation-failed" data-testid="mutation-error">
          ⚠ {error}
        </div>
      )}
    </div>
  )
}

function percent(job: MutationJobStatus): number {
  return job.total ? Math.round((job.done / job.total) * 100) : 5
}

function scoreClass(score: number | null): string {
  if (score === null) return ''
  return score >= 80 ? 'mutation-ok' : 'mutation-warn'
}
