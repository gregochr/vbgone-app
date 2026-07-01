import { useEffect, useState } from 'react'
import type { WizardState } from '../WizardShell'
import { runBaselineTests, rerunBaselineTests } from '../../../api/migrateApi'
import { ConfirmDialog } from '../ConfirmDialog'
import { CodeBlock } from '../CodeBlock'
import { useWizardConfig } from '../../../config/WizardConfigContext'
import {
  PROVIDERS,
  PROTECT_TEST_FW,
  looksUiCoupled,
  modelFor,
  modelLabelFor,
  providerColor,
} from '../../../config/engine'

interface Props {
  state: WizardState
  update: (partial: Partial<WizardState>) => void
  onReady: () => void
  /** Portfolio queue drill-in — swaps the closing panel for the queue done-state. */
  fromQueue?: boolean
  protectedCount?: number
  readyTotal?: number
  nextClassName?: string
  onProtectNext?: () => void
  onBackToReadiness?: () => void
}

const KICKER = 'STEP 04 · BASELINE TESTS'
const TITLE = 'Confirm the baseline'

/**
 * Protect step 4 (last). Generates an MSTest characterisation suite and runs it against
 * the *original* VB.NET on the CLR. Polarity flips from Migrate: green is the success
 * state; a red result means the net (the oracle) isn't faithful yet — not that the code
 * is broken.
 */
export function Step4BaselineTests({
  state,
  update,
  onReady,
  fromQueue,
  protectedCount = 0,
  readyTotal = 0,
  nextClassName,
  onProtectNext,
  onBackToReadiness,
}: Props) {
  const { provider, modelOverrides, engineParams } = useWizardConfig()
  const prov = PROVIDERS[provider]
  const reasoningModel = modelLabelFor(provider, 'reasoning', modelOverrides)
  const reasoningModelId = modelFor(provider, 'reasoning', modelOverrides)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showConfirm, setShowConfirm] = useState(!state.baselineTests)

  const className =
    state.analysis?.suggestedMigrationOrder[state.currentClassIndex] ??
    state.analysis?.classes[0]?.name ??
    ''
  const sessionId = state.analysis?.sessionId ?? ''

  useEffect(() => {
    if (state.baselineTests) onReady()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const applyResult = (result: import('../../../api/migrateApi').BaselineTestsResult) => {
    update({ baselineTests: result, netFaithful: result.netFaithful })
    setLoading(false)
    onReady()
  }
  const onError = (err: unknown) => {
    setLoading(false)
    setError(err instanceof Error ? err.message : 'Running the baseline suite failed')
  }

  const runSuite = () => {
    setShowConfirm(false)
    setLoading(true)
    runBaselineTests(sessionId, className, engineParams).then(applyResult).catch(onError)
  }

  // Re-running the same suite against unchanged VB yields the same red — the fix is editing
  // the assertions. The (possibly edited) net is sent to the run-only endpoint (no AI call).
  const rerunSuite = () => {
    if (!state.baselineTests) return
    setLoading(true)
    rerunBaselineTests(sessionId, className, state.baselineTests.code)
      .then(applyResult)
      .catch(onError)
  }

  const editNet = (edited: string) => {
    if (!state.baselineTests) return
    update({ baselineTests: { ...state.baselineTests, code: edited } })
  }

  const header = (
    <>
      <div className="step-kicker">{KICKER}</div>
      <h2 className="step-title">{TITLE}</h2>
    </>
  )

  if (showConfirm) {
    return (
      <div>
        {header}
        <ConfirmDialog onConfirm={runSuite} onCancel={() => setShowConfirm(false)}>
          <p>
            This will make an API call to {prov.name} ({reasoningModelId}) via the {prov.vendor}{' '}
            provider to emit a {PROTECT_TEST_FW} characterisation suite, then compile and run it
            against your original VB.NET on the CLR.
          </p>
          <p>
            {'🔒'} Your code is sent securely over HTTPS and is not stored by Anthropic beyond the
            request.
          </p>
          <p>
            {'⚡'} Here, <strong>green is the success state</strong> — it means the baseline
            faithfully describes current behaviour.
          </p>
          <p>Proceed?</p>
        </ConfirmDialog>
      </div>
    )
  }

  if (loading) {
    return (
      <div>
        {header}
        <div className="busy-row">
          <span className="spinner" />
          <span className="loading-text">
            Generating the characterisation suite and running it against your original VB.NET…
          </span>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div>
        {header}
        <div className="build-status build-red">{error}</div>
      </div>
    )
  }

  const tests = state.baselineTests
  if (!tests) {
    return (
      <div>
        {header}
        <p className="step-subtitle">
          {prov.name} emits an {PROTECT_TEST_FW} suite that asserts the <strong>real</strong>{' '}
          behaviour — the actual exception types and coerced results — then runs it against your
          original VB.NET. Here, <strong>green is the success state</strong>.
        </p>
        <div className="run-card">
          <div className="run-card-model">
            <span className="model-dot" style={{ background: providerColor(provider) }} />
            <span className="model-name">{reasoningModel}</span>
            <span className="model-caption">BASELINE SUITE · {prov.vendor}</span>
          </div>
          <button className="btn-plex" onClick={() => setShowConfirm(true)}>
            Generate suite &amp; run against VB.NET
          </button>
        </div>
      </div>
    )
  }

  // Read faithfulness from the actual run result, not a defaulted flag, so neither banner
  // can flash before there's a real result.
  const faithful = tests.netFaithful
  const { passed, total, failed } = tests.build
  // A compile failure (e.g. WinForms-coupled VB on the Linux sidecar) — distinct from a
  // drifted assertion. Surface the compiler output rather than the assertion framing.
  const compileError = tests.build.errors.length > 0
  const uiCoupled = compileError && looksUiCoupled(state.content)

  return (
    <div>
      {header}

      {faithful ? (
        <div className="net-banner net-green" data-testid="net-banner-green">
          <span className="net-banner-label">{'✓'} GREEN</span>
          <span className="net-banner-text">
            {passed} / {total} passing against your original VB.NET. The baseline faithfully
            describes current behaviour — any failure here means the baseline isn't accurate yet,{' '}
            <strong>not that your code is broken</strong>.
          </span>
        </div>
      ) : (
        <div className="net-banner net-red" data-testid="net-banner-red">
          <span className="net-banner-label">{'▲'} BASELINE NOT FAITHFUL</span>
          <div className="net-banner-body">
            {compileError ? (
              <span className="net-banner-text">
                The net or the original VB didn't compile against the CLR, so the behaviour couldn't
                be pinned. This is <strong>not a pass</strong> — fix the issue below and re-run.
              </span>
            ) : (
              <span className="net-banner-text">
                {failed} / {total} failing against your <strong>untouched</strong> original. An
                assertion describes behaviour the code doesn't actually have — the oracle drifted
                into aspiration. This is <strong>not a pass</strong>;{' '}
                <strong>correct the flagged assertion(s) in the baseline below</strong>, then
                re-run.
              </span>
            )}
          </div>
        </div>
      )}

      {/* Compile failure — explain Protect's headless precondition, tailored to the cause. */}
      {compileError && (
        <div className="net-precondition" data-testid="net-precondition">
          <span className="net-precondition-glyph" aria-hidden="true">
            {'ⓘ'}
          </span>
          {uiCoupled ? (
            <span>
              This source looks <strong>UI-coupled</strong> (WinForms). Protect runs your original
              VB.NET headless on the CLR, so it can only net a{' '}
              <strong>business-logic surface</strong> with no UI dependencies — a class that doesn't
              import <code>System.Windows.Forms</code>, inherit <code>Form</code>, or touch
              controls. If the logic already lives in a separate class, point Protect at that file;
              if it's inside the form's event handlers, it has to be separated first (that's a
              Migrate-style change).
            </span>
          ) : (
            <span>
              Protect compiles your original VB.NET headless on the CLR. The source needs to be
              self-contained with no UI or platform dependencies. Fix the errors above (or upload
              just the business-logic class) and re-run.
            </span>
          )}
        </div>
      )}

      {/* Failing assertions (or compile errors) — the actionable detail behind the headline. */}
      {!faithful && (compileError ? tests.build.errors.length > 0 : tests.failures.length > 0) && (
        <div className="net-failures" data-testid="net-failures">
          <div className="net-failures-head">
            {compileError ? 'COMPILE ERRORS' : 'DRIFTED ASSERTIONS'}
          </div>
          {compileError
            ? tests.build.errors.map((e, i) => (
                <div className="net-failure-row" key={i}>
                  <code className="net-failure-msg">{e}</code>
                </div>
              ))
            : tests.failures.map((f) => (
                <div className="net-failure-row" key={f.name}>
                  <code className="net-failure-name">{f.name}</code>
                  {f.message && <span className="net-failure-msg">{f.message}</span>}
                </div>
              ))}
        </div>
      )}

      <div className="code-header">
        <span>{tests.testClassName}.cs</span>
        <span className="code-header-caption">{PROTECT_TEST_FW} · vs original VB.NET</span>
      </div>
      {/* Editable in the red state so the user can correct the drifted assertion(s). */}
      <CodeBlock code={tests.code} editable={!faithful} onEdit={editNet} />

      {!faithful && (
        <div className="net-rerun-row">
          <button className="btn-plex" onClick={rerunSuite}>
            Edit baseline &amp; re-run
          </button>
          <span className="net-rerun-hint">
            Runs the edited net against your original VB — no new generation.
          </span>
        </div>
      )}

      {/* Portfolio queue: a done-state that advances the queue. Single-file: the closing panel. */}
      {faithful && fromQueue && (
        <div className="queue-done" data-testid="queue-done">
          <div className="queue-done-head">
            <span className="queue-done-check" aria-hidden="true">
              {'✓'}
            </span>
            <div>
              <div className="queue-done-title">{className} protected</div>
              <div className="queue-done-count">
                {protectedCount + 1} / {readyTotal} ready classes protected
              </div>
            </div>
          </div>
          <div className="queue-done-actions">
            {nextClassName ? (
              <button className="btn-plex" onClick={onProtectNext}>
                Protect next class — {nextClassName} →
              </button>
            ) : (
              <button className="btn-plex" onClick={onBackToReadiness}>
                All ready classes protected — back to readiness
              </button>
            )}
            {nextClassName && (
              <button className="btn-ghost" onClick={onBackToReadiness}>
                Back to readiness
              </button>
            )}
          </div>
        </div>
      )}

      {faithful && !fromQueue && (
        <div className="baseline-closing" data-testid="baseline-closing">
          <div className="baseline-closing-head">
            <span className="baseline-closing-check" aria-hidden="true">
              {'✓'}
            </span>
            <span className="baseline-closing-title">You now have a behavioural baseline</span>
          </div>
          <p className="baseline-closing-body">
            Upgrade a dependency, re-run this suite, and any failure is a real behavioural change to
            investigate. <strong>Green means nothing changed.</strong>
          </p>
          <div className="baseline-closing-foot">
            baseline pinned · how you operate the patch-and-re-run loop — manual, CI, or a future
            step — is yours to decide
          </div>
        </div>
      )}
    </div>
  )
}
