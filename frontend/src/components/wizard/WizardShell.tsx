import { useState, useEffect, useLayoutEffect, useCallback, useRef } from 'react'
import './WizardShell.css'
import { Step1Upload } from './Step1Upload'
import { Step2Analysis } from './Step2Analysis'
import { Step3Interface } from './Step3Interface'
import { Step4Tests } from './Step4Tests'
import { Step5Implement } from './Step5Implement'
import { Step6PR } from './Step6PR'
import { InfoTip } from './InfoTip'
import { fetchCost } from '../../api/migrateApi'
import type {
  AnalysisResult,
  InterfaceResult,
  TestsResult,
  StubResult,
  BuildResult,
  ImplementResult,
  PullRequestResult,
} from '../../api/migrateApi'

const STEPS: { label: string; tip: React.ReactNode }[] = [
  {
    label: 'Upload',
    tip: (
      <>
        <p>
          <strong>VBGone migrates legacy VB.NET business logic to modern, tested C#</strong> — one
          class at a time. Upload a <strong>.vb</strong> source file or a <strong>.zip</strong>{' '}
          containing multiple files to get started.
        </p>
        <p>
          Not sure what to expect? Use the <strong>Load Demo File</strong> button to load a real
          example — genuine legacy Windows Forms code from GitHub, with business logic embedded in
          button click handlers and no separation of concerns. Exactly the kind of code VBGone was
          built to modernise.
        </p>
        <p>
          VBGone will analyse your code, generate a C# interface and a comprehensive NUnit test
          suite, build and run the tests, and raise a Pull Request against your output repository —
          all with a full quality pipeline automatically triggered on every PR.
        </p>
      </>
    ),
  },
  {
    label: 'Analysis',
    tip: (
      <>
        <p>
          <strong>Claude Sonnet</strong> reads your VB.NET source and builds a complete picture of
          the codebase before a single line of C# is written. It identifies every class, its public
          methods, and dependencies between classes.
        </p>
        <p>
          Crucially, it looks past the Windows Forms noise — button click handlers, form load
          events, UI wiring — and extracts the <strong>pure business logic</strong> underneath. Each
          class is rated LOW, MEDIUM, or HIGH complexity based on method count, branching logic, and
          dependency depth.
        </p>
        <p>
          A suggested migration order is produced, starting with the simplest, most self-contained
          classes — building confidence and test coverage before tackling the complex ones.
        </p>
        <p>
          This analysis becomes the foundation for every subsequent step.{' '}
          <strong>Review it carefully</strong> — if the summary looks wrong, the generated tests
          will be wrong too.
        </p>
      </>
    ),
  },
  {
    label: 'Interface',
    tip: (
      <>
        <p>
          A C# interface is the cornerstone of the{' '}
          <strong>
            <a
              href="https://martinfowler.com/bliki/StranglerFigApplication.html"
              target="_blank"
              rel="noopener noreferrer"
            >
              Strangler Fig migration pattern
            </a>
          </strong>
          . By defining the contract first — the method signatures, parameter types, and return
          types — VBGone creates a seam between the legacy VB.NET system and its modern C#
          replacement.
        </p>
        <p>
          Both implementations satisfy the same interface, which means the legacy code can continue
          running in production while the C# replacement is built and tested alongside it. Only when
          the tests are green and the team is confident does the legacy implementation get retired.
        </p>
        <p>
          <strong>Claude Haiku</strong> handles interface generation — it is a mechanical task,
          extracting method signatures from the analysis and expressing them in idiomatic C#. All UI
          concerns are stripped: no Windows Forms dependencies, no event handler signatures, no UI
          types. What remains is pure business logic expressed as a clean contract.
        </p>
        <p>
          <strong>Review the interface carefully and edit if needed</strong> — every subsequent step
          is built on this foundation.
        </p>
      </>
    ),
  },
  {
    label: 'Tests',
    tip: (
      <>
        <p>
          <strong>This is the most important step in the migration.</strong> Claude Sonnet generates
          a comprehensive NUnit test suite based on the behaviour of the VB.NET source — covering
          the happy path, edge cases, boundary values, and error conditions like divide by zero and
          null inputs.
        </p>
        <p>
          The tests are written against the C# interface, not the implementation — so they will be
          valid regardless of how the class is eventually implemented.
        </p>
        <p>
          <strong>Claude Haiku</strong> then generates a stub implementation: a class that satisfies
          the interface but throws <strong>NotImplementedException</strong> for every method.{' '}
          <strong>.NET test</strong> is run immediately. Every test will fail.{' '}
          <strong>This is intentional — and it is a good sign.</strong>
        </p>
        <p>
          The failing tests are the migration contract. They define exactly what correct behaviour
          looks like in C#, verified against the original VB.NET logic. No implementation is correct
          unless all of these tests pass. This is the <strong>Red phase</strong> of
          Red-Green-Refactor TDD. The stub and the failing tests together are more valuable than any
          amount of documentation.
        </p>
      </>
    ),
  },
  {
    label: 'Implement',
    tip: (
      <>
        <p>
          <strong>You have two paths.</strong> The <strong>Claude path</strong> asks Claude Sonnet
          to generate a full C# implementation based on the VB.NET behaviour. It writes idiomatic
          modern C# — expression-bodied members, pattern matching, nullable reference types where
          appropriate. <strong>.NET test</strong> runs immediately and all tests should pass green.
          This is fast, useful for straightforward classes, and gives you a solid starting point
          even if you intend to refine the implementation afterwards.
        </p>
        <p>
          The <strong>Stub path</strong> downloads the stub and lets you implement the class
          yourself in Rider or Visual Studio. You make the tests go green. This is the recommended
          path for production migrations — the developer who implements the class reads the original
          VB.NET, understands the business logic, and owns every line of the C# replacement.
        </p>
        <p>
          The tests keep you honest: if they pass, the behaviour is correct. If they fail, something
          is wrong. Either path produces the same output — a C# class that satisfies the interface
          and passes the test suite. The difference is who writes it and how well they understand it
          afterwards.
        </p>
      </>
    ),
  },
  {
    label: 'Raise PR',
    tip: (
      <>
        <p>
          VBGone commits three files to a new branch in your output repository and raises a{' '}
          <strong>Pull Request</strong>: the C# interface, the implementation, and the NUnit test
          suite. The branch name reflects the class being migrated, making it easy to track progress
          across a large codebase.
        </p>
        <p>
          The moment the PR is raised, the <strong>GitHub Actions CI pipeline</strong> triggers
          automatically. <strong>Roslynator</strong> analyses the C# for outdated patterns and
          suggests modern alternatives. <strong>Coverlet</strong> measures test coverage and reports
          to Codecov, ensuring high coverage from day one. <strong>Stryker.NET</strong> runs
          mutation testing nightly — introducing deliberate bugs and verifying the tests catch them,
          measuring test suite quality rather than just coverage percentage. <strong>CodeQL</strong>{' '}
          scans for security vulnerabilities.
        </p>
        <p>
          Review the PR, address any pipeline findings, and merge when everything is green. The
          legacy VB.NET class can now be retired — its behaviour is preserved in C#, proven by
          tests, and protected by a quality pipeline that will catch regressions on every future
          change.
        </p>
        <p>
          <strong>No Claude call at this step</strong> — this is pure GitHub API.
        </p>
      </>
    ),
  },
]

export interface CompletedClass {
  className: string
  interfaceResult: InterfaceResult
  tests: TestsResult
  stubResult: StubResult
  implementResult: ImplementResult
}

export interface WizardState {
  filename: string
  content: string
  analysis: AnalysisResult | null
  currentClassIndex: number
  completedClasses: CompletedClass[]
  interfaceResult: InterfaceResult | null
  tests: TestsResult | null
  stubResult: StubResult | null
  redBuild: BuildResult | null
  implementResult: ImplementResult | null
  greenBuild: BuildResult | null
  prResult: PullRequestResult | null
}

const initialState: WizardState = {
  filename: '',
  content: '',
  analysis: null,
  currentClassIndex: 0,
  completedClasses: [],
  interfaceResult: null,
  tests: null,
  stubResult: null,
  redBuild: null,
  implementResult: null,
  greenBuild: null,
  prResult: null,
}

// Exchange rate hard-coded — update periodically
// Current rate: 1 USD = 0.79 GBP (March 2026)
const USD_TO_GBP = 0.79

const NEXT_TITLES = [
  'Analyse your VB.NET with Claude Sonnet',
  'Generate the C# interface with Claude Haiku',
  'Generate tests and run the red build',
  'Choose your implementation path',
  'Raise a Pull Request against vbgone-output',
]

const BACK_TITLES = [
  '',
  'Back to upload',
  'Back to analysis',
  'Back to interface',
  'Back to tests',
  'Back to implementation',
]

export interface ProjectMode {
  sessionId: string
  className: string
  classIndex: number
  totalClasses: number
  onComplete: (raised: boolean) => void
  onBackToQueue: () => void
}

interface WizardShellProps {
  projectMode?: ProjectMode
  onProjectAnalysed?: (analysis: import('../../api/migrateApi').ProjectAnalysis) => void
}

export function WizardShell({ projectMode, onProjectAnalysed }: WizardShellProps = {}) {
  // In project mode, start at step 2 (Interface) since analysis is already done
  const startStep = projectMode ? 2 : 0
  const [step, setStep] = useState(startStep)
  const [state, setState] = useState<WizardState>(() => {
    if (projectMode) {
      return {
        ...initialState,
        currentClassIndex: 0,
        completedClasses: [],
        filename: `${projectMode.className}.vb`,
        content: '',
        analysis: {
          sessionId: projectMode.sessionId,
          classes: [
            {
              name: projectMode.className,
              methods: [],
              dependencies: [],
              complexity: 'LOW' as const,
            },
          ],
          suggestedMigrationOrder: [projectMode.className],
          summary: `Migrating ${projectMode.className} from project queue`,
        },
      }
    }
    return initialState
  })
  const [stepReady, setStepReady] = useState(false)
  const [totalCost, setTotalCost] = useState(0)
  const sessionIdRef = useRef<string | undefined>(projectMode ? projectMode.sessionId : undefined)

  const update = (partial: Partial<WizardState>) => {
    setState((prev) => {
      const next = { ...prev, ...partial }
      sessionIdRef.current = next.analysis?.sessionId
      return next
    })
  }

  const refreshCost = useCallback(() => {
    const sessionId = sessionIdRef.current
    if (!sessionId) return
    fetchCost(sessionId)
      .then((result) => setTotalCost(result.totalCost))
      .catch(() => {})
  }, [])

  useEffect(() => {
    refreshCost()
  }, [step, refreshCost])

  // Notify project queue when class migration is complete
  useEffect(() => {
    if (projectMode && step === 5) {
      // In project mode, mark Complete (not PR Raised) — PR is raised from the queue
      projectMode.onComplete(false)
    }
  }, [step]) // eslint-disable-line react-hooks/exhaustive-deps

  const totalClasses = state.analysis?.suggestedMigrationOrder?.length ?? 1
  const isMultiClass = !projectMode && totalClasses > 1
  const minStep = projectMode ? 2 : state.currentClassIndex > 0 ? 2 : 0

  const next = () => {
    // After Step 5 (index 4) in multi-class: save and advance or finish
    if (step === 4 && isMultiClass) {
      const completed: CompletedClass = {
        className: state.analysis!.suggestedMigrationOrder[state.currentClassIndex],
        interfaceResult: state.interfaceResult!,
        tests: state.tests!,
        stubResult: state.stubResult!,
        implementResult: state.implementResult!,
      }

      if (state.currentClassIndex < totalClasses - 1) {
        // More classes — save, reset per-class state, back to Interface
        setState((prev) => ({
          ...prev,
          currentClassIndex: prev.currentClassIndex + 1,
          completedClasses: [...prev.completedClasses, completed],
          interfaceResult: null,
          tests: null,
          stubResult: null,
          redBuild: null,
          implementResult: null,
          greenBuild: null,
        }))
        setStepReady(false)
        setStep(2) // Back to Interface (Step 3)
        return
      } else {
        // Last class — save before proceeding to PR
        setState((prev) => ({
          ...prev,
          completedClasses: [...prev.completedClasses, completed],
        }))
      }
    }

    setStepReady(false)
    setStep((s) => Math.min(s + 1, 5))
  }
  const back = () => {
    setStepReady(false)
    setStep((s) => Math.max(s - 1, minStep))
  }

  const onReady = () => {
    setStepReady(true)
    refreshCost()
  }

  // Loop-back arc measurement for multi-class stepper
  const navRef = useRef<HTMLElement>(null)
  const [arcPath, setArcPath] = useState<{
    d: string
    width: number
    height: number
    left: number
  } | null>(null)
  const allClassesDone = state.completedClasses.length >= totalClasses
  const iterationStarted = step > 2 || state.completedClasses.length > 0
  const arcColour = allClassesDone
    ? 'var(--green)'
    : iterationStarted
      ? 'var(--amber)'
      : 'var(--grey)'
  const arcColourName = allClassesDone ? 'green' : iterationStarted ? 'amber' : 'grey'

  useLayoutEffect(() => {
    if (!isMultiClass || !navRef.current) {
      setArcPath(null)
      return
    }
    const nav = navRef.current
    const interfaceBox = nav.querySelector('[data-step-index="2"]') as HTMLElement | null
    const implementBox = nav.querySelector('[data-step-index="4"]') as HTMLElement | null
    if (!interfaceBox || !implementBox) return

    const navRect = nav.getBoundingClientRect()
    const iRect = interfaceBox.getBoundingClientRect()
    const eRect = implementBox.getBoundingClientRect()

    const left = iRect.left - navRect.left + iRect.width / 2
    const right = eRect.left - navRect.left + eRect.width / 2
    const width = right - left
    const arcHeight = 22
    const totalHeight = arcHeight + 8

    // Curved path from right (Implement) down and back to left (Interface)
    const d = `M ${width} 0 C ${width} ${arcHeight}, 0 ${arcHeight}, 0 0`

    setArcPath({ d, width, height: totalHeight, left })
  }, [isMultiClass, step, state.currentClassIndex])

  const classKey = state.currentClassIndex
  const steps = [
    <Step1Upload
      key={0}
      state={state}
      update={update}
      onReady={() => setStepReady(true)}
      onProjectAnalysed={onProjectAnalysed}
    />,
    <Step2Analysis key={1} state={state} update={update} onReady={onReady} />,
    <Step3Interface key={`2-${classKey}`} state={state} update={update} onReady={onReady} />,
    <Step4Tests key={`3-${classKey}`} state={state} update={update} onReady={onReady} />,
    <Step5Implement key={`4-${classKey}`} state={state} update={update} onReady={onReady} />,
    <Step6PR key={5} state={state} update={update} onReady={onReady} projectMode={projectMode} />,
  ]

  return (
    <div className="wizard">
      <nav
        className={`wizard-steps${isMultiClass && arcPath ? ' has-loop-arc' : ''}`}
        ref={navRef}
        style={{ position: 'relative' }}
      >
        {STEPS.map(({ label, tip }, i) => {
          const isCompleted = i < step || (i === 5 && !!state.prResult)
          return (
            <div className="wizard-step-item" key={label} data-step-index={i}>
              <div
                className={`wizard-step-box ${isCompleted ? 'completed' : i === step ? 'active' : ''}`}
              >
                <div
                  className={`wizard-step-dot ${isCompleted ? 'completed' : i === step ? 'active' : ''}`}
                >
                  {isCompleted ? '\u2713' : i + 1}
                </div>
                <span className={`wizard-step-label ${isCompleted || i === step ? 'active' : ''}`}>
                  {label}
                </span>
                <span className="step-infotip">
                  <InfoTip>{tip}</InfoTip>
                </span>
              </div>
              {i < STEPS.length - 1 && (
                <div className={`wizard-step-connector ${i < step ? 'completed' : ''}`} />
              )}
            </div>
          )
        })}
        {totalCost > 0 && (
          <span
            className="cost-display"
            data-testid="cost-display"
            title="Total Anthropic API cost for this session"
          >
            Est. cost: ${totalCost.toFixed(4)} (~ £{(totalCost * USD_TO_GBP).toFixed(4)})
          </span>
        )}
        {isMultiClass && arcPath && (
          <svg
            className="loop-back-arc"
            data-testid="loop-back-arc"
            data-arc-colour={arcColourName}
            width={arcPath.width + 12}
            height={arcPath.height}
            style={{
              position: 'absolute',
              left: arcPath.left - 6,
              bottom: 0,
              pointerEvents: 'none',
              overflow: 'visible',
            }}
          >
            <defs>
              <marker
                id="loop-arrow"
                viewBox="0 0 10 10"
                refX="1"
                refY="5"
                markerWidth="6"
                markerHeight="6"
                orient="auto-start-reverse"
              >
                <path d="M 0 0 L 10 5 L 0 10 z" fill={arcColour} />
              </marker>
            </defs>
            <path
              d={arcPath.d}
              fill="none"
              stroke={arcColour}
              strokeWidth="2"
              strokeDasharray="6 3"
              markerEnd="url(#loop-arrow)"
              transform="translate(6, 2)"
            />
          </svg>
        )}
      </nav>

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

      <div className={`wizard-nav ${step === 5 ? 'wizard-nav-final' : ''}`}>
        <button
          className="btn-back"
          onClick={back}
          disabled={step <= minStep}
          title={BACK_TITLES[step]}
        >
          Back
        </button>
        <span className="coffee-text">
          Made with {'\u2615'} by Chris —{' '}
          <a
            className="coffee-link"
            href="https://buymeacoffee.com/gregorychris"
            target="_blank"
            rel="noopener noreferrer"
          >
            Buy me a coffee
          </a>
        </span>
        {step < 5 && (
          <button
            className="btn-next"
            onClick={next}
            disabled={!stepReady}
            title={
              step === 4 && state.greenBuild && state.greenBuild.buildStatus !== 'GREEN'
                ? 'Fix failing tests before raising a PR'
                : step === 4 && isMultiClass && state.currentClassIndex < totalClasses - 1
                  ? `Next class: ${state.analysis!.suggestedMigrationOrder[state.currentClassIndex + 1]}`
                  : NEXT_TITLES[step]
            }
          >
            {step === 4 && isMultiClass && state.currentClassIndex < totalClasses - 1
              ? 'Next Class'
              : 'Next'}
          </button>
        )}
      </div>
    </div>
  )
}
