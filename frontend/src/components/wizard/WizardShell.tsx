import { useState, useReducer, useEffect, useCallback } from 'react'
import './WizardShell.css'
import { Step1Upload } from './Step1Upload'
import { Step2Analysis } from './Step2Analysis'
import { Step3Interface } from './Step3Interface'
import { Step4Tests } from './Step4Tests'
import { Step5Implement } from './Step5Implement'
import { Step6PR } from './Step6PR'
import { Step3Baseline } from './assure/Step3Baseline'
import { Step4BaselineTests } from './assure/Step4BaselineTests'
import { StepReadiness } from './assure/StepReadiness'
import { WizardStepper } from './WizardStepper'
import { fetchCost } from '../../api/migrateApi'
import { useWizardConfig } from '../../config/WizardConfigContext'
import { STEP_ROLES, ASSURE_STEP_ROLES } from '../../config/engine'
import { STEPS, ASSURE_STEPS, NEXT_TITLES, BACK_TITLES } from './wizardStepContent'
import { initialState, wizardReducer } from './wizardState'
import type { CompletedClass, WizardState } from './wizardState'

// Re-exported so the many `import type { WizardState } from './WizardShell'` sites keep resolving.
export type { CompletedClass, WizardState } from './wizardState'

export interface ProjectMode {
  sessionId: string
  className: string
  classIndex: number
  totalClasses: number
  onComplete: (raised: boolean) => void
  onBackToQueue: () => void
}

/** Seed state for the reducer: project-mode drills straight into one pre-analysed class. */
function initWizardState(projectMode?: ProjectMode): WizardState {
  if (!projectMode) return initialState
  return {
    ...initialState,
    filename: `${projectMode.className}.vb`,
    analysis: {
      sessionId: projectMode.sessionId,
      classes: [
        { name: projectMode.className, methods: [], dependencies: [], complexity: 'LOW' as const },
      ],
      suggestedMigrationOrder: [projectMode.className],
      summary: `Migrating ${projectMode.className} from project queue`,
    },
  }
}

interface WizardShellProps {
  projectMode?: ProjectMode
  onProjectAnalysed?: (analysis: import('../../api/migrateApi').ProjectAnalysis) => void
}

export function WizardShell({ projectMode, onProjectAnalysed }: WizardShellProps = {}) {
  // In project mode, start at step 2 (Interface) since analysis is already done
  const startStep = projectMode ? 2 : 0
  const [step, setStep] = useState(startStep)
  const [state, dispatch] = useReducer(wizardReducer, projectMode, initWizardState)
  const [stepReady, setStepReady] = useState(false)
  const { mode, provider, modelOverrides, setSessionCost, setCurrentStep } = useWizardConfig()

  const assure = mode === 'assure'
  const activeSteps = assure ? ASSURE_STEPS : STEPS
  const activeRoles = assure ? ASSURE_STEP_ROLES : STEP_ROLES
  const lastIndex = activeSteps.length - 1

  const update = (partial: Partial<WizardState>) => {
    dispatch({ type: 'merge', partial })
  }

  // The active session id. When it changes (analysis completes, a mode-switch reset clears it),
  // refreshCost's identity changes and the [step, refreshCost] effect re-fires, so the displayed
  // cost tracks the current session — no ref written during render.
  const sessionId = state.analysis?.sessionId

  const refreshCost = useCallback(() => {
    if (!sessionId) return
    fetchCost(sessionId)
      .then((result) => setSessionCost(result.totalCost))
      .catch(() => {})
  }, [sessionId, setSessionCost])

  useEffect(() => {
    refreshCost()
  }, [step, refreshCost])

  // Surface the current step so the header can lock the engine after step 1
  // (Assure fixes the provider once the run is under way).
  useEffect(() => {
    setCurrentStep(step)
  }, [step, setCurrentStep])

  // Switching MODE resets the wizard: step semantics differ too much to carry state.
  // Keep the uploaded file (Upload is identical in both modes); clear everything else.
  // Done during render (the documented "reset state when a value changes" pattern)
  // rather than in an effect, so there's no extra render pass. The session-cost reset
  // lives in the context's setMode (where that state is owned).
  const [prevMode, setPrevMode] = useState(mode)
  if (mode !== prevMode) {
    setPrevMode(mode)
    if (!projectMode) {
      setStep(0)
      setStepReady(false)
      dispatch({ type: 'reset' })
    }
  }

  // Notify project queue when class migration is complete
  useEffect(() => {
    if (projectMode && step === 5) {
      // In project mode, mark Complete (not PR Raised) — PR is raised from the queue
      projectMode.onComplete(false)
    }
  }, [step]) // eslint-disable-line react-hooks/exhaustive-deps

  const totalClasses = state.analysis?.suggestedMigrationOrder?.length ?? 1
  // Multi-class iteration (queue + loop-back arc) is a Migrate-only flow.
  const isMultiClass = !projectMode && !assure && totalClasses > 1
  // Assure can always step back to Readiness/Upload; Migrate locks to the class loop.
  const minStep = projectMode ? 2 : !assure && state.currentClassIndex > 0 ? 2 : 0

  // ── Assure portfolio queue navigation ──
  const assureOrder = state.analysis?.suggestedMigrationOrder ?? []
  const activeClassName: string | undefined = assureOrder[state.currentClassIndex]
  const isPortfolioInput = state.filename.toLowerCase().endsWith('.zip')
  const firstUnassured = (done: string[]) => assureOrder.find((n) => !done.includes(n))

  const assureClass = (name: string) => {
    const idx = assureOrder.indexOf(name)
    if (idx < 0) return
    dispatch({
      type: 'merge',
      partial: {
        currentClassIndex: idx,
        fromQueue: true,
        baselineResult: null,
        baselineTests: null,
        netFaithful: true,
      },
    })
    setStepReady(false)
    setStep(2) // Baseline
  }

  const assureNext = () => {
    const done = activeClassName ? [...state.netted, activeClassName] : state.netted
    const next = firstUnassured(done)
    dispatch({
      type: 'merge',
      partial: {
        netted: done,
        ...(next
          ? {
              currentClassIndex: assureOrder.indexOf(next),
              baselineResult: null,
              baselineTests: null,
              netFaithful: true,
            }
          : {}),
      },
    })
    setStepReady(false)
    setStep(next ? 2 : 1) // next class' Baseline, else back to Readiness
  }

  const backToReadiness = () => {
    const done =
      activeClassName && !state.netted.includes(activeClassName)
        ? [...state.netted, activeClassName]
        : state.netted
    dispatch({
      type: 'merge',
      partial: {
        netted: done,
        fromQueue: false,
        baselineResult: null,
        baselineTests: null,
        netFaithful: true,
      },
    })
    setStepReady(false)
    setStep(1) // Readiness
  }

  const nextClassName = firstUnassured(
    activeClassName ? [...state.netted, activeClassName] : state.netted,
  )

  const next = () => {
    // Assure portfolio: advancing from the Readiness report drills into the first ready class.
    if (assure && step === 1 && isPortfolioInput) {
      const fr = firstUnassured(state.netted)
      if (fr) assureClass(fr)
      return
    }
    // After Step 5 (index 4) in multi-class: save and advance or finish
    if (step === 4 && isMultiClass) {
      const completed: CompletedClass = {
        className: state.analysis!.suggestedMigrationOrder[state.currentClassIndex],
        interfaceResult: state.interfaceResult!,
        tests: state.tests!,
        stubResult: state.stubResult!,
        implementResult: state.implementResult!,
      }
      const hasMore = state.currentClassIndex < totalClasses - 1
      dispatch({ type: 'advanceClass', completed, hasMore })
      if (hasMore) {
        // More classes — advanced + per-class state cleared by the reducer; back to Interface.
        setStepReady(false)
        setStep(2) // Back to Interface (Step 3)
        return
      }
      // Last class — saved; fall through to the PR step.
    }

    setStepReady(false)
    setStep((s) => Math.min(s + 1, lastIndex))
  }
  const back = () => {
    setStepReady(false)
    setStep((s) => Math.max(s - 1, minStep))
  }
  // Stepper chips navigate to any already-reached step (>= minStep, <= current).
  const goToStep = (target: number) => {
    if (target === step || target < minStep || target > step) return
    setStepReady(false)
    setStep(target)
  }

  const onReady = () => {
    setStepReady(true)
    refreshCost()
  }

  const classKey = state.currentClassIndex
  const upload = (
    <Step1Upload
      key={0}
      state={state}
      update={update}
      onReady={() => setStepReady(true)}
      onProjectAnalysed={onProjectAnalysed}
    />
  )
  const analysis = <Step2Analysis key={1} state={state} update={update} onReady={onReady} />
  const steps = assure
    ? [
        upload,
        <StepReadiness
          key="1-assure"
          state={state}
          update={update}
          onReady={onReady}
          onAssureClass={assureClass}
        />,
        <Step3Baseline
          key="2-assure"
          state={state}
          update={update}
          onReady={onReady}
          fromQueue={state.fromQueue}
          activeClass={activeClassName}
          onBackToReadiness={backToReadiness}
        />,
        <Step4BaselineTests
          key="3-assure"
          state={state}
          update={update}
          onReady={onReady}
          fromQueue={state.fromQueue}
          assuredCount={state.netted.length}
          readyTotal={assureOrder.length || (state.readiness?.totals.netReady ?? 0)}
          nextClassName={nextClassName}
          onAssureNext={assureNext}
          onBackToReadiness={backToReadiness}
        />,
      ]
    : [
        upload,
        analysis,
        <Step3Interface key={`2-${classKey}`} state={state} update={update} onReady={onReady} />,
        <Step4Tests key={`3-${classKey}`} state={state} update={update} onReady={onReady} />,
        <Step5Implement key={`4-${classKey}`} state={state} update={update} onReady={onReady} />,
        <Step6PR
          key={5}
          state={state}
          update={update}
          onReady={onReady}
          projectMode={projectMode}
        />,
      ]

  return (
    <div className="wizard">
      <WizardStepper
        activeSteps={activeSteps}
        activeRoles={activeRoles}
        provider={provider}
        modelOverrides={modelOverrides}
        step={step}
        minStep={minStep}
        isMultiClass={isMultiClass}
        assure={assure}
        prComplete={!!state.prResult}
        completedCount={state.completedClasses.length}
        totalClasses={totalClasses}
        currentClassIndex={state.currentClassIndex}
        onStepClick={goToStep}
      />

      {projectMode && (
        <div className="project-banner" data-testid="project-banner">
          <button className="btn-plex" onClick={projectMode.onBackToQueue}>
            {'\u2190'} Back to Queue
          </button>
          <span className="project-banner-text">
            Migrating class {projectMode.classIndex} of {projectMode.totalClasses} —{' '}
            <strong>{projectMode.className}</strong>
          </span>
        </div>
      )}

      {isMultiClass && step >= 2 && step <= 4 && (
        <div className="project-banner" data-testid="class-progress-banner">
          <span className="project-banner-text">
            Migrating class {state.currentClassIndex + 1} of {totalClasses} —{' '}
            <strong>{state.analysis!.suggestedMigrationOrder[state.currentClassIndex]}</strong>
            {state.completedClasses.length > 0 && (
              <span style={{ marginLeft: 12, fontSize: '0.8rem' }}>
                ({state.completedClasses.length} completed)
              </span>
            )}
          </span>
        </div>
      )}

      <div className="wizard-content">{steps[step]}</div>

      <div className="wizard-nav">
        <button
          className="btn-back"
          onClick={back}
          disabled={step <= minStep}
          title={BACK_TITLES[step]}
        >
          Back
        </button>
        <span className="step-counter" data-testid="step-counter">
          STEP {step + 1} / {activeSteps.length}
        </span>
        {step < lastIndex && (
          <button
            className="btn-next"
            onClick={next}
            disabled={!stepReady}
            title={
              assure
                ? 'Next step'
                : step === 4 && state.greenBuild && state.greenBuild.buildStatus !== 'GREEN'
                  ? 'Fix failing tests before raising a PR'
                  : step === 4 && isMultiClass && state.currentClassIndex < totalClasses - 1
                    ? `Next class: ${state.analysis!.suggestedMigrationOrder[state.currentClassIndex + 1]}`
                    : NEXT_TITLES[step]
            }
          >
            {!assure && step === 4 && isMultiClass && state.currentClassIndex < totalClasses - 1
              ? 'Next Class'
              : 'Next'}
          </button>
        )}
      </div>
    </div>
  )
}
