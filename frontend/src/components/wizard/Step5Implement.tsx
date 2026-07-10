import { useState, useEffect } from 'react'
import type { WizardState } from './WizardShell'
import { implement, buildAfterImplement, retryImplement, build } from '../../api/migrateApi'
import { ConfirmDialog } from './ConfirmDialog'
import { CollapsibleCode } from './CollapsibleCode'
import { CoverageBadge } from './CoverageBadge'
import { useWizardConfig } from '../../config/WizardConfigContext'
import { LANGS, PROVIDERS, modelFor, modelLabelFor, providerColor } from '../../config/engine'

interface Props {
  state: WizardState
  update: (partial: Partial<WizardState>) => void
  onReady: () => void
}

const MAX_ATTEMPTS = 3

export function Step5Implement({ state, update, onReady }: Props) {
  const { provider, targetLanguage, modelOverrides, engineParams } = useWizardConfig()
  const prov = PROVIDERS[provider]
  const lang = LANGS[targetLanguage]
  const implementModel = modelLabelFor(provider, 'implementation', modelOverrides)
  const escalationModel = modelLabelFor(provider, 'escalation', modelOverrides)
  const implementModelId = modelFor(provider, 'implementation', modelOverrides)
  const escalationModelId = modelFor(provider, 'escalation', modelOverrides)
  const [mode, setMode] = useState<'STUB' | 'CLAUDE' | null>('CLAUDE')
  const [loading, setLoading] = useState(false)
  const [phase, setPhase] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pendingMode, setPendingMode] = useState<'STUB' | 'CLAUDE' | null>(null)
  const [attempts, setAttempts] = useState(1)
  const [showRetryConfirm, setShowRetryConfirm] = useState(false)

  const className =
    state.analysis?.suggestedMigrationOrder[state.currentClassIndex] ??
    state.analysis?.classes[0]?.name ??
    ''
  const sessionId = state.analysis?.sessionId ?? ''

  useEffect(() => {
    if (state.greenBuild && state.implementResult && state.greenBuild.buildStatus === 'GREEN') {
      onReady()
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const run = async (chosen: 'STUB' | 'CLAUDE') => {
    setMode(chosen)
    setPendingMode(null)
    setLoading(true)
    setError(null)

    try {
      setPhase(
        chosen === 'CLAUDE'
          ? `${prov.name} is implementing…`
          : 'Generating stub for manual implementation…',
      )
      const implResult = await implement(sessionId, className, chosen, engineParams)
      update({ implementResult: implResult })

      setPhase(`Running ${lang.testCmd}…`)
      const buildResult = await buildAfterImplement(sessionId, chosen)
      update({ greenBuild: buildResult })

      setLoading(false)
      if (buildResult.buildStatus === 'GREEN') {
        onReady()
      }
    } catch (err) {
      setLoading(false)
      setError(err instanceof Error ? err.message : 'Implementation failed')
    }
  }

  const retry = async () => {
    setShowRetryConfirm(false)
    const failingTests = state.greenBuild?.failedTests ?? []
    setLoading(true)
    setError(null)
    const nextAttempt = attempts + 1
    setAttempts(nextAttempt)

    try {
      const isOpus = nextAttempt >= MAX_ATTEMPTS
      setPhase(
        isOpus ? `Escalating to ${escalationModel}…` : `${prov.name} is retrying implementation…`,
      )
      const implResult = await retryImplement(
        sessionId,
        className,
        failingTests,
        nextAttempt,
        engineParams,
      )
      update({ implementResult: implResult })

      setPhase(`Running ${lang.testCmd}…`)
      const buildResult = await build(sessionId)
      update({ greenBuild: buildResult })

      setLoading(false)
      if (buildResult.buildStatus === 'GREEN') {
        onReady()
      }
    } catch (err) {
      setLoading(false)
      setError(err instanceof Error ? err.message : 'Retry failed')
    }
  }

  const handleChoice = (chosen: 'STUB' | 'CLAUDE') => {
    setPendingMode(chosen)
  }

  const handleCancel = () => {
    setPendingMode(null)
    setMode(null)
    setLoading(false)
    setError(null)
  }

  if (loading) {
    return (
      <div>
        <div className="step-kicker">STEP 05 · IMPLEMENT · GREEN BUILD</div>
        <h2 className="step-title">Make the tests pass</h2>
        <div className="busy-row">
          <span className="spinner" />
          <span className="loading-text">{phase}</span>
        </div>
      </div>
    )
  }

  if (state.greenBuild && state.implementResult) {
    const b = state.greenBuild
    const isGreen = b.buildStatus === 'GREEN'
    const isRed = b.buildStatus === 'RED'
    const isClaude = state.implementResult.mode === 'CLAUDE'
    const canRetry = isRed && isClaude && attempts < MAX_ATTEMPTS
    const exhausted = isRed && isClaude && attempts >= MAX_ATTEMPTS

    return (
      <div>
        <div className="step-kicker">STEP 05 · IMPLEMENT · GREEN BUILD</div>
        <h2 className="step-title">Make the tests pass</h2>
        <p className="step-subtitle">
          Mode: {isClaude ? 'AI' : 'Manual'}
          {attempts > 1 && ` (attempt ${attempts} of ${MAX_ATTEMPTS})`}
        </p>

        <div className={`build-status ${isGreen ? 'build-green' : 'build-red'}`}>
          {b.buildStatus === 'ERROR'
            ? '\uD83D\uDD34 Build error — generated code did not compile'
            : isGreen
              ? `\uD83D\uDFE2 ${b.passed} / ${b.total} tests passing — this is the GREEN phase of Red-Green TDD.`
              : `\uD83D\uDD34 ${b.failed} / ${b.total} tests failing — review the implementation`}
          {isRed && b.failedTests.length > 0 && (
            <ul
              style={{ margin: '8px 0 0', paddingLeft: 20, fontSize: '0.85rem', fontWeight: 400 }}
            >
              {b.failedTests.map((name, i) => (
                <li key={i}>{name}</li>
              ))}
            </ul>
          )}
        </div>

        <CoverageBadge coveragePercent={b.coveragePercent} ofLabel="the implementation" />

        {canRetry && !showRetryConfirm && (
          <button
            className="btn-plex"
            style={{ marginBottom: 16 }}
            onClick={() => setShowRetryConfirm(true)}
          >
            Retry with Claude ({MAX_ATTEMPTS - attempts}{' '}
            {MAX_ATTEMPTS - attempts === 1 ? 'attempt' : 'attempts'} remaining)
          </button>
        )}

        {showRetryConfirm && (
          <ConfirmDialog onConfirm={retry} onCancel={() => setShowRetryConfirm(false)}>
            {attempts + 1 >= MAX_ATTEMPTS ? (
              <p>
                This is the final attempt. It will escalate to{' '}
                <strong>
                  {escalationModel} ({escalationModelId})
                </strong>{' '}
                — the most capable model — via the {prov.vendor} provider.
              </p>
            ) : (
              <p>
                This will make an API call to {prov.name} ({implementModelId}) via the {prov.vendor}{' '}
                provider.
              </p>
            )}
            <p>
              {'\uD83D\uDD12'} Your code is sent securely over HTTPS and is not stored beyond the
              request by {prov.vendor}.
            </p>
            <p>
              {'\uD83E\uDDEA'} The {b.failedTests.length} failing{' '}
              {b.failedTests.length === 1 ? 'test' : 'tests'} will be extracted from the test suite
              and sent to Claude alongside the current implementation.{' '}
              <strong>
                This targeted prompt engineering gives Claude the exact assertions it needs to
                satisfy, rather than just the test names.
              </strong>
            </p>
            <p>
              {'\uD83D\uDCB0'} Prompt caching is enabled — the system prompt is cached and reused
              across calls, reducing input token costs by up to 90% at scale.
            </p>
            <p>Proceed?</p>
          </ConfirmDialog>
        )}

        {exhausted && (
          <div className="build-status build-red" style={{ marginBottom: 16 }}>
            Claude was unable to make all tests pass after {MAX_ATTEMPTS} attempts. Download the
            stub and implement manually.
          </div>
        )}

        <CollapsibleCode title="Implementation" code={state.implementResult.code} />
      </div>
    )
  }

  if (pendingMode) {
    return (
      <div>
        <div className="step-kicker">STEP 05 · IMPLEMENT · GREEN BUILD</div>
        <h2 className="step-title">Make the tests pass</h2>
        <ConfirmDialog onConfirm={() => run(pendingMode)} onCancel={handleCancel}>
          {pendingMode === 'CLAUDE' ? (
            <>
              <p>
                This will make an API call to {prov.name} ({implementModelId}) via the {prov.vendor}{' '}
                provider.
              </p>
              <p>
                {'\uD83D\uDD12'} Your code is sent securely over HTTPS and is not stored by{' '}
                {prov.vendor} beyond the request.
              </p>
              <p>
                {'\uD83D\uDCB0'} Prompt caching is enabled — the system prompt is cached and reused
                across calls, reducing input token costs by up to 90% at scale.
              </p>
              <p>
                {'\u26A1'} Model: {implementModelId} — chosen for its ability to write correct,
                idiomatic modern C#. The implementation will use expression-bodied members, pattern
                matching, and nullable reference types where appropriate.
              </p>
              <p>
                After the implementation is generated, .NET test runs automatically. All tests
                should pass — if any fail, the implementation will need review.
              </p>
            </>
          ) : (
            <>
              <p>You have chosen to implement the C# yourself.</p>
              <p>
                {'\uD83D\uDCE5'} The stub will be downloaded — a C# class that implements the
                interface with NotImplementedException on every method.
              </p>
              <p>
                {'\uD83E\uDDEA'} Open the stub in Rider or Visual Studio alongside the NUnit test
                suite. Your goal is to make every test pass — the tests define the correct behaviour
                based on the original VB.NET source.
              </p>
              <p>
                {'\uD83D\uDCA1'} This is the recommended path for production migrations. The
                developer who implements the class reads the original VB.NET, understands the
                business logic, and owns every line of the C# replacement. No AI shortcut — just TDD
                discipline.
              </p>
              <p>No API call will be made at this step.</p>
            </>
          )}
          <p>Proceed?</p>
        </ConfirmDialog>
      </div>
    )
  }

  if (error) {
    return (
      <div>
        <div className="step-kicker">STEP 05 · IMPLEMENT · GREEN BUILD</div>
        <h2 className="step-title">Make the tests pass</h2>
        <div className="build-status build-red">{error}</div>
      </div>
    )
  }

  return (
    <div>
      <h2 className="step-title">Choose Implementation</h2>
      <p className="step-subtitle">
        Let {prov.name} implement the class, or download the stub and write it yourself. Either way
        the tests keep you honest.
      </p>

      <div className="impl-choices">
        <div
          className={`impl-choice impl-choice-left ${mode === 'CLAUDE' ? 'selected' : ''}`}
          onClick={() => handleChoice('CLAUDE')}
        >
          <div className="impl-choice-head">
            <span className="model-dot" style={{ background: providerColor(provider) }} />
            <span className="impl-choice-title">{prov.name} implements</span>
          </div>
          <div className="impl-choice-desc">
            AI writes idiomatic {lang.lang}, then <code>{lang.testCmd}</code> runs automatically.
          </div>
          <div className="impl-choice-meta">
            {implementModel} · retries escalate to {escalationModel}
          </div>
        </div>

        <div
          className={`impl-choice impl-choice-left ${mode === 'STUB' ? 'selected' : ''}`}
          onClick={() => handleChoice('STUB')}
        >
          <div className="impl-choice-head">
            <span className="impl-choice-glyph">{'\u232B'}</span>
            <span className="impl-choice-title">Manual (stub)</span>
          </div>
          <div className="impl-choice-desc">
            Download the stub and implement it in {lang.ide}. Recommended for production migrations.
          </div>
          <div className="impl-choice-meta">no API call · you own every line</div>
        </div>
      </div>
    </div>
  )
}
