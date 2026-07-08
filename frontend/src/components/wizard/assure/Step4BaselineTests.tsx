import { useEffect, useState } from 'react'
import type { WizardState } from '../WizardShell'
import {
  runBaselineTests,
  rerunBaselineTests,
  quarantineBaseline,
  repairBaselineTest,
} from '../../../api/migrateApi'
import { ConfirmDialog } from '../ConfirmDialog'
import { CodeBlock } from '../CodeBlock'
import { CoverageBadge } from '../CoverageBadge'
import { useWizardConfig } from '../../../config/WizardConfigContext'
import {
  PROVIDERS,
  ASSURE_TEST_FW,
  looksUiCoupled,
  modelFor,
  modelLabelFor,
  providerColor,
} from '../../../config/engine'
import { TAG_META, cardBorderFor } from './repairCard'
import type { RepairAttempt } from './repairCard'

interface Props {
  state: WizardState
  update: (partial: Partial<WizardState>) => void
  onReady: () => void
  /** Portfolio queue drill-in — swaps the closing panel for the queue done-state. */
  fromQueue?: boolean
  assuredCount?: number
  readyTotal?: number
  nextClassName?: string
  onAssureNext?: () => void
  onBackToReadiness?: () => void
}

const KICKER = 'STEP 04 · BASELINE TESTS'
const TITLE = 'Confirm the baseline'

type LoggedAttempt = RepairAttempt & { status: 'running' | 'done' }

/**
 * Assure step 4 (last). Generates an MSTest suite that records how the *original*,
 * unmodified VB.NET behaves today, then runs it against that original on the CLR. Polarity
 * flips from Migrate: green is the goal. Because the code is untouched, a failing test can
 * only mean the test is wrong — not the code — so a red run drives the auto-repair loop
 * (mechanical → reasoning → escalation, up to 3 attempts, then quarantine).
 */
export function Step4BaselineTests({
  state,
  update,
  onReady,
  fromQueue,
  assuredCount = 0,
  readyTotal = 0,
  nextClassName,
  onAssureNext,
  onBackToReadiness,
}: Props) {
  const { provider, modelOverrides, engineParams } = useWizardConfig()
  const prov = PROVIDERS[provider]
  const reasoningModel = modelLabelFor(provider, 'reasoning', modelOverrides)
  const reasoningModelId = modelFor(provider, 'reasoning', modelOverrides)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showConfirm, setShowConfirm] = useState(!state.baselineTests)

  // ── Auto-repair loop state (a genuine red run drives the backend loop) ──
  const [repairLog, setRepairLog] = useState<LoggedAttempt[]>([])
  const [repairOutcome, setRepairOutcome] = useState<'succeeded' | 'quarantined' | null>(null)
  const [realRepairStarted, setRealRepairStarted] = useState(false)
  const [realRepairCode, setRealRepairCode] = useState<string | null>(null)

  const className =
    state.analysis?.suggestedMigrationOrder[state.currentClassIndex] ??
    state.analysis?.classes[0]?.name ??
    ''
  const sessionId = state.analysis?.sessionId ?? ''

  useEffect(() => {
    if (state.baselineTests) onReady()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // A class is downloadable only once its baseline actually goes green — the backend records a
  // suite for it only then (see AssureService.executeSuite / repair). `netted` alone isn't enough:
  // it also holds classes left early or quarantined, which have no suite to download.
  const withClass = (list: string[], name: string) =>
    !name || list.includes(name) ? list : [...list, name]

  const applyResult = (result: import('../../../api/migrateApi').BaselineTestsResult) => {
    update({
      baselineTests: result,
      netFaithful: result.netFaithful,
      ...(result.netFaithful
        ? { assuredGreen: withClass(state.assuredGreen ?? [], className) }
        : {}),
    })
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
  // the tests. The (possibly edited) suite is sent to the run-only endpoint (no AI call).
  const rerunSuite = () => {
    if (!state.baselineTests) return
    setLoading(true)
    rerunBaselineTests(sessionId, className, state.baselineTests.code)
      .then(applyResult)
      .catch(onError)
  }

  const editSuite = (edited: string) => {
    if (!state.baselineTests) return
    update({ baselineTests: { ...state.baselineTests, code: edited } })
  }

  // ── Auto-repair loop control ──
  const resetRepair = () => {
    setRepairLog([])
    setRepairOutcome(null)
    setRealRepairStarted(false)
    setRealRepairCode(null)
  }

  // Escalating tiers: mechanical (cheap swap) → reasoning (restructure) → escalation (exact type).
  const TIER_ROLES = ['mechanical', 'reasoning', 'escalation'] as const

  /**
   * Real auto-repair on a genuine red run: call the backend once per tier, feeding the previous
   * attempt's code forward, rendering each card as it settles. Stops on the first green attempt;
   * three non-green attempts (or a value that differs every run) end in quarantine. The backend
   * owns the actual rewrite, the validity gate and the re-run — this only orchestrates the loop.
   */
  const startRealRepair = async (failingTest: string) => {
    if (!state.baselineTests) return
    resetRepair()
    setRealRepairStarted(true)
    let code = state.baselineTests.code
    let outcome: 'succeeded' | 'quarantined' = 'quarantined'
    try {
      for (let tier = 1; tier <= 3; tier++) {
        const role = TIER_ROLES[tier - 1]
        setRepairLog((log) => [
          ...log,
          {
            tier: role.charAt(0).toUpperCase() + role.slice(1),
            role,
            rationale: '',
            diff: [],
            gate: { ok: true, note: '' },
            rerun: null,
            tag: 'red',
            status: 'running',
          },
        ])
        const attempt = await repairBaselineTest(
          sessionId,
          className,
          code,
          failingTest,
          tier,
          engineParams,
        )
        code = attempt.code
        setRealRepairCode(code)
        setRepairLog((log) => {
          const next = log.slice()
          next[next.length - 1] = {
            tier: attempt.tier,
            role: attempt.role,
            rationale: attempt.rationale,
            diff: attempt.diff,
            gate: attempt.gate,
            rerun: attempt.rerun,
            tag: attempt.tag,
            status: 'done',
          }
          return next
        })
        if (attempt.netFaithful) {
          outcome = 'succeeded'
          break
        }
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Auto-repair failed')
      return
    }
    setRepairOutcome(outcome)
    // Persist the corrected suite so the code panel keeps it; leave netFaithful for the loop's
    // own banner to report (flipping it here would swap the cards for the plain green view).
    if (state.baselineTests) update({ baselineTests: { ...state.baselineTests, code } })
    if (outcome === 'succeeded') {
      // A repaired-green class is now downloadable (its suite is recorded server-side).
      update({ assuredGreen: withClass(state.assuredGreen ?? [], className) })
      onReady()
      return
    }
    // Quarantine: set the unrepairable test(s) aside ([Ignore]) and re-run the rest. When the
    // remainder is green the backend records a downloadable suite, so the class is still assured
    // with the passing tests. This backend re-run takes a while and the queue's "Assure with N
    // quarantined →" button is already live, so touch ONLY the additive `assuredGreen` here (and
    // local panel state) — never write `baselineTests`, which would clobber the next class if the
    // user has already advanced. Leaving netFaithful false also keeps the quarantine card on screen.
    const failing = state.baselineTests?.failures.map((f) => f.name) ?? [failingTest]
    try {
      const result = await quarantineBaseline(sessionId, className, code, failing)
      setRealRepairCode(result.code) // show the [Ignore]-annotated suite in the code panel
      if (result.netFaithful) {
        update({ assuredGreen: withClass(state.assuredGreen ?? [], className) })
      }
    } catch {
      // Setting the test aside failed — the last attempt's code stays shown; class isn't downloadable.
    }
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
            provider to capture your app's current behaviour as a {ASSURE_TEST_FW} test suite, then
            compile and run it against your original VB.NET on the CLR.
          </p>
          <p>
            {'🔒'} Your code is sent securely over HTTPS and is not stored by Anthropic beyond the
            request.
          </p>
          <p>
            {'⚡'} Here, <strong>green is the goal</strong> — it means the tests accurately describe
            how your code behaves today.
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
            Capturing your app's current behaviour as tests and running them against your original
            VB.NET…
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
          {prov.name} writes a set of {ASSURE_TEST_FW} tests that check the <strong>real</strong>{' '}
          behaviour — the actual errors and converted values — then runs them against your original
          VB.NET. Here, <strong>green is the goal</strong>.
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

  // Read the real run result. A compile failure (WinForms-coupled VB on the Linux sidecar) and
  // a "0 discovered tests" glitch are distinct from a failing test — surface them separately.
  const faithful = tests.netFaithful
  const { passed, total, failed } = tests.build
  const compileError = tests.build.errors.length > 0
  const noTests = !faithful && !compileError && total === 0
  const uiCoupled = compileError && !fromQueue && looksUiCoupled(state.content)

  // A genuine red run (compiled, tests discovered, ≥1 real failure) drives the auto-repair loop;
  // compile errors and empty suites keep their own distinct manual paths.
  const firstFailing = tests.failures[0]?.name ?? ''
  const genuineDrift = !faithful && !compileError && !noTests
  const canAutoRepair = genuineDrift && firstFailing !== ''
  const repairSucceeded = repairOutcome === 'succeeded'
  const repairQuarantined = repairOutcome === 'quarantined'
  const showTopRedBanner = !faithful && !(genuineDrift && realRepairStarted)
  const showClosing = faithful || (genuineDrift && repairSucceeded)
  const codePanelCode = realRepairCode ?? tests.code

  return (
    <div>
      {header}

      {faithful ? (
        <div className="net-banner net-green" data-testid="net-banner-green">
          <span className="net-banner-label">{'✓'} GREEN</span>
          <span className="net-banner-text">
            {passed} / {total} passing against your original VB.NET. The tests accurately describe
            how your code behaves today — if one fails, it means a test is wrong,{' '}
            <strong>not that your code is broken</strong>.
          </span>
        </div>
      ) : (
        showTopRedBanner && (
          <div className="net-banner net-red" data-testid="net-banner-red">
            <span className="net-banner-label">{'▲'} TESTS FAILED</span>
            <div className="net-banner-body">
              {compileError ? (
                <span className="net-banner-text">
                  The tests or the original VB didn't compile against the CLR, so we couldn't record
                  how it behaves. This is <strong>not a pass</strong> — fix the issue below and
                  re-run.
                </span>
              ) : noTests ? (
                <span className="net-banner-text" data-testid="net-no-tests">
                  The suite compiled and ran, but MSTest discovered{' '}
                  <strong>0 runnable tests</strong> — so there's nothing recorded yet. This isn't a
                  wrong test and there's nothing to fix; it's an empty suite.{' '}
                  <strong>Re-generate the suite</strong> and run it again.
                </span>
              ) : (
                <span className="net-banner-text">
                  {failed} / {total} failed against your <strong>untouched</strong> original. The
                  test expects behaviour the code doesn't actually have — so{' '}
                  <strong>the test is wrong, not your code</strong>. Auto-repair rewrites the
                  failing test to match what the code does.
                </span>
              )}
            </div>
          </div>
        )
      )}

      {/* Coverage of the legacy VB — how much of the original the safety net actually pins. */}
      {!compileError && !noTests && (
        <CoverageBadge
          coveragePercent={tests.build.coveragePercent}
          branchPercent={tests.build.branchCoveragePercent}
          ofLabel="your original VB.NET"
        />
      )}

      {/* Compile failure — explain Assure's precondition (runs on its own), tailored to cause. */}
      {compileError && (
        <div className="net-precondition" data-testid="net-precondition">
          <span className="net-precondition-glyph" aria-hidden="true">
            {'ⓘ'}
          </span>
          {uiCoupled ? (
            <span>
              This source looks <strong>UI-coupled</strong> (WinForms). Assure runs your original
              VB.NET on its own on the CLR, so it can only cover a{' '}
              <strong>business-logic class that runs without the screen</strong> — one that doesn't
              import <code>System.Windows.Forms</code>, inherit <code>Form</code>, or touch
              controls. If the logic already lives in a separate class, point Assure at that file;
              if it's inside the form's event handlers, it has to be separated first (that's a
              Migrate-style change).
            </span>
          ) : (
            <span>
              Assure compiles your original VB.NET and runs it on its own on the CLR. The source
              needs to be self-contained with no UI or platform dependencies. Fix the errors above
              (or upload just the business-logic class) and re-run.
            </span>
          )}
        </div>
      )}

      {/* Failing tests (or compile errors) — the actionable detail, until a repair takes over. */}
      {((compileError && tests.build.errors.length > 0) ||
        (genuineDrift && !realRepairStarted && tests.failures.length > 0)) && (
        <div className="failed-tests" data-testid="failed-tests">
          <div className="failed-tests-head">
            {compileError ? 'COMPILE ERRORS' : 'FAILED TESTS'}
          </div>
          {compileError
            ? tests.build.errors.map((e, i) => (
                <div className="failed-test-row" key={i}>
                  <code className="failed-test-msg">{e}</code>
                </div>
              ))
            : tests.failures.map((f) => (
                <div className="failed-test-row" key={f.name}>
                  <code className="failed-test-name">{f.name}</code>
                  {f.message && <span className="failed-test-msg">{f.message}</span>}
                </div>
              ))}
        </div>
      )}

      {/* Auto-repair action row — a genuine red run, before the loop starts. */}
      {canAutoRepair && !realRepairStarted && (
        <>
          <div className="repair-action-row">
            <button className="btn-plex" onClick={() => startRealRepair(firstFailing)}>
              Auto-repair · up to 3 attempts
            </button>
            <button className="btn-ghost" onClick={rerunSuite}>
              Edit manually &amp; re-run
            </button>
          </div>
          <div className="repair-caption">
            Auto-repair changes only the failing test to match what the code actually does — the
            passing tests stay untouched. Every edit is checked (no meaningless always-pass tests;
            same method &amp; inputs) and logged.
          </div>
        </>
      )}

      {/* The real backend loop: attempt cards, then the succeeded or quarantine outcome. */}
      {genuineDrift && realRepairStarted && (
        <>
          <RepairLoop
            test={firstFailing}
            log={repairLog}
            total={3}
            provider={provider}
            overrides={modelOverrides}
          />
          {repairSucceeded && <RepairedBanner test={firstFailing} />}
          {repairQuarantined && (
            <QuarantineReal
              test={firstFailing}
              fromQueue={fromQueue}
              onAssureNext={onAssureNext}
              onBackToReadiness={onBackToReadiness}
            />
          )}
        </>
      )}

      <div className="code-header">
        <span>{tests.testClassName}.cs</span>
        <span className="code-header-caption">{ASSURE_TEST_FW} · vs original VB.NET</span>
      </div>
      {/* Editable while a fix hasn't started, so the user can also correct a test by hand. */}
      <CodeBlock
        code={codePanelCode}
        editable={!faithful && !realRepairStarted}
        onEdit={editSuite}
      />

      {/* Distinct manual paths: re-generate an empty suite, or edit-and-re-run a compile failure. */}
      {!faithful && noTests ? (
        <div className="net-rerun-row">
          <button className="btn-plex" onClick={() => setShowConfirm(true)}>
            Re-generate suite &amp; run
          </button>
          <span className="net-rerun-hint">
            Captures your app's current behaviour afresh as tests and runs them against your
            original VB.
          </span>
        </div>
      ) : (
        compileError && (
          <div className="net-rerun-row">
            <button className="btn-plex" onClick={rerunSuite}>
              Edit tests &amp; re-run
            </button>
            <span className="net-rerun-hint">
              Runs the edited tests against your original VB — no new generation.
            </span>
          </div>
        )
      )}

      {/* Portfolio queue: a done-state that advances the queue. Single-file: the closing panel. */}
      {showClosing &&
        (fromQueue ? (
          <QueueDone
            className={className}
            assuredCount={assuredCount}
            readyTotal={readyTotal}
            nextClassName={nextClassName}
            onAssureNext={onAssureNext}
            onBackToReadiness={onBackToReadiness}
          />
        ) : (
          <BaselineClosing />
        ))}
    </div>
  )
}

/** The sequential auto-repair attempt cards (running → settled), one per escalating tier. */
function RepairLoop({
  test,
  log,
  total,
  provider,
  overrides,
}: {
  test: string
  log: LoggedAttempt[]
  total: number
  provider: import('../../../config/engine').ProviderId
  overrides: import('../../../config/engine').ModelOverrides
}) {
  return (
    <div className="repair-loop" data-testid="repair-loop">
      <div className="repair-loop-label">AUTO-REPAIR · {test}</div>
      <div className="repair-cards">
        {log.map((attempt, i) => {
          const running = attempt.status === 'running'
          const meta = TAG_META[attempt.tag]
          const model = modelLabelFor(provider, attempt.role, overrides)
          return (
            <div
              className="repair-card"
              key={i}
              style={{ borderColor: cardBorderFor(running ? 'running' : attempt.tag) }}
            >
              <div className="repair-card-head">
                <span className="repair-attempt-badge">
                  ATTEMPT {i + 1} / {total}
                </span>
                <span className="repair-tier">{attempt.tier}</span>
                <span className="repair-model">{model}</span>
                <span className="repair-card-spacer" />
                {running ? (
                  <span className="spinner" />
                ) : (
                  <span
                    className="repair-result-chip"
                    style={{ color: meta.color, borderColor: meta.color }}
                  >
                    {meta.label}
                  </span>
                )}
              </div>

              {!running && (
                <>
                  <div className="repair-rationale">{attempt.rationale}</div>
                  {attempt.diff.length > 0 && (
                    <div className="repair-diff">
                      {attempt.diff.map((d, j) => (
                        <div
                          className={`diff-line diff-${d.op === '+' ? 'add' : d.op === '-' ? 'del' : 'ctx'}`}
                          key={j}
                        >
                          {d.op === ' ' ? '  ' : `${d.op} `}
                          {d.text}
                        </div>
                      ))}
                    </div>
                  )}
                  <div className="repair-check">
                    <span
                      className={`repair-gate-chip ${attempt.gate.ok ? 'gate-ok' : 'gate-noedit'}`}
                    >
                      {attempt.gate.ok ? 'CHECK OK' : 'NO EDIT'}
                    </span>
                    <span className="repair-check-note">{attempt.gate.note}</span>
                  </div>
                  {attempt.rerun && (
                    <div className="repair-check">
                      <span
                        className={`repair-rerun-chip ${attempt.rerun.green ? 'rerun-ok' : 'rerun-fail'}`}
                      >
                        {attempt.rerun.green ? 'RE-RUN ✓' : 'RE-RUN ✕'}
                      </span>
                      <span className="repair-check-note">{attempt.rerun.note}</span>
                    </div>
                  )}
                </>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}

function BaselineClosing() {
  return (
    <div className="baseline-closing" data-testid="baseline-closing">
      <div className="baseline-closing-head">
        <span className="baseline-closing-check" aria-hidden="true">
          {'✓'}
        </span>
        <span className="baseline-closing-title">You now have a safety net of tests</span>
      </div>
      <p className="baseline-closing-body">
        Upgrade a dependency, re-run these tests, and any failure is a real change worth
        investigating. <strong>Green means nothing changed.</strong>
      </p>
      <div className="baseline-closing-foot">
        baseline saved · how you run the patch-and-re-test loop — by hand, in CI, or a later step —
        is up to you
      </div>
    </div>
  )
}

function QueueDone({
  className,
  assuredCount,
  readyTotal,
  nextClassName,
  onAssureNext,
  onBackToReadiness,
}: {
  className: string
  assuredCount: number
  readyTotal: number
  nextClassName?: string
  onAssureNext?: () => void
  onBackToReadiness?: () => void
}) {
  return (
    <div className="queue-done" data-testid="queue-done">
      <div className="queue-done-head">
        <span className="queue-done-check" aria-hidden="true">
          {'✓'}
        </span>
        <div>
          <div className="queue-done-title">{className} assured</div>
          <div className="queue-done-count">
            {assuredCount + 1} / {readyTotal} ready classes assured
          </div>
        </div>
      </div>
      <div className="queue-done-actions">
        {nextClassName ? (
          <button className="btn-plex" onClick={onAssureNext}>
            Assure next class — {nextClassName} →
          </button>
        ) : (
          <button className="btn-plex" onClick={onBackToReadiness}>
            All ready classes assured — back to readiness
          </button>
        )}
        {nextClassName && (
          <button className="btn-ghost" onClick={onBackToReadiness}>
            Back to readiness
          </button>
        )}
      </div>
    </div>
  )
}

/** Green "BASELINE REPAIRED" banner shown when the real loop makes the baseline green again. */
function RepairedBanner({ test }: { test: string }) {
  return (
    <div className="net-banner net-green repair-outcome" data-testid="repair-succeeded">
      <span className="net-banner-label">{'✓'} BASELINE REPAIRED</span>
      <div className="net-banner-body">
        <span className="net-banner-text">
          The failing test now matches what the code actually does — green against your untouched
          VB.NET. Only that test changed; the rest are untouched.
        </span>
        <div className="repair-audit">
          logged · {test} · before and after saved in the audit trail
        </div>
      </div>
    </div>
  )
}

/** Amber quarantine card shown when three attempts can't produce a valid, reliable fix. */
function QuarantineReal({
  test,
  fromQueue,
  onAssureNext,
  onBackToReadiness,
}: {
  test: string
  fromQueue?: boolean
  onAssureNext?: () => void
  onBackToReadiness?: () => void
}) {
  return (
    <div className="quarantine-card" data-testid="repair-quarantined">
      <span className="quarantine-glyph" aria-hidden="true">
        {'⚠️'}
      </span>
      <div className="quarantine-body">
        <div className="quarantine-title">Gave up after 3 attempts — test set aside for review</div>
        <p className="quarantine-text">
          <code className="quarantine-test">{test}</code> couldn't be repaired: no fixed test
          matches how the code behaves — it may give a <strong>different answer every run</strong>.
          Auto-repair wouldn't fake a pass, so it's left out of the baseline and flagged for a
          person to check; the other tests are unaffected.
        </p>
        <div className="quarantine-actions">
          {fromQueue ? (
            <>
              <button className="btn-plex btn-sm" onClick={onAssureNext}>
                Assure with 1 quarantined →
              </button>
              <button className="btn-ghost" onClick={onBackToReadiness}>
                Back to readiness
              </button>
            </>
          ) : (
            <span className="quarantine-note">
              baseline saved with the passing tests · 1 flagged for review
            </span>
          )}
        </div>
      </div>
    </div>
  )
}
