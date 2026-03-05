import { useState } from 'react'
import './WizardShell.css'
import { Step1Upload } from './Step1Upload'
import { Step2Analysis } from './Step2Analysis'
import { Step3Interface } from './Step3Interface'
import { Step4Tests } from './Step4Tests'
import { Step5Implement } from './Step5Implement'
import { Step6PR } from './Step6PR'
import type {
  AnalysisResult,
  InterfaceResult,
  TestsResult,
  BuildResult,
  ImplementResult,
  PullRequestResult,
} from '../../api/migrateApi'

const STEP_LABELS = ['Upload', 'Analysis', 'Interface', 'Tests', 'Implement', 'Raise PR']

export interface WizardState {
  filename: string
  content: string
  analysis: AnalysisResult | null
  interfaceResult: InterfaceResult | null
  tests: TestsResult | null
  redBuild: BuildResult | null
  implementResult: ImplementResult | null
  greenBuild: BuildResult | null
  prResult: PullRequestResult | null
}

const initialState: WizardState = {
  filename: '',
  content: '',
  analysis: null,
  interfaceResult: null,
  tests: null,
  redBuild: null,
  implementResult: null,
  greenBuild: null,
  prResult: null,
}

export function WizardShell() {
  const [step, setStep] = useState(0)
  const [state, setState] = useState<WizardState>(initialState)
  const [stepReady, setStepReady] = useState(false)

  const update = (partial: Partial<WizardState>) => setState((prev) => ({ ...prev, ...partial }))

  const next = () => {
    setStepReady(false)
    setStep((s) => Math.min(s + 1, 5))
  }
  const back = () => {
    setStepReady(false)
    setStep((s) => Math.max(s - 1, 0))
  }

  const steps = [
    <Step1Upload key={0} state={state} update={update} onReady={() => setStepReady(true)} />,
    <Step2Analysis key={1} state={state} update={update} onReady={() => setStepReady(true)} />,
    <Step3Interface key={2} state={state} update={update} onReady={() => setStepReady(true)} />,
    <Step4Tests key={3} state={state} update={update} onReady={() => setStepReady(true)} />,
    <Step5Implement key={4} state={state} update={update} onReady={() => setStepReady(true)} />,
    <Step6PR key={5} state={state} update={update} onReady={() => setStepReady(true)} />,
  ]

  return (
    <div className="wizard">
      <nav className="wizard-steps">
        {STEP_LABELS.map((label, i) => (
          <div className="wizard-step-item" key={label}>
            <div
              className={`wizard-step-dot ${i === step ? 'active' : i < step ? 'completed' : ''}`}
            >
              {i < step ? '\u2713' : i + 1}
            </div>
            <span className={`wizard-step-label ${i === step ? 'active' : ''}`}>{label}</span>
            {i < STEP_LABELS.length - 1 && (
              <div className={`wizard-step-connector ${i < step ? 'completed' : ''}`} />
            )}
          </div>
        ))}
      </nav>

      <div className="wizard-content">{steps[step]}</div>

      <div className="wizard-nav">
        <button className="btn-back" onClick={back} disabled={step === 0}>
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
          <button className="btn-next" onClick={next} disabled={!stepReady}>
            Next
          </button>
        )}
      </div>
    </div>
  )
}
