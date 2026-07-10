import type {
  AnalysisResult,
  InterfaceResult,
  TestsResult,
  StubResult,
  BuildResult,
  ImplementResult,
  PullRequestResult,
  BaselineResult,
  BaselineTestsResult,
  ReadinessReport,
} from '../../api/migrateApi'

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
  // Assure-mode artifacts:
  /** A real uploaded .zip estate (Assure portfolio scan) — sent to /assess-project. */
  zipFile: File | null
  /**
   * Set when the estate came from a public GitHub repo (ingested at Upload). Holds the `owner/repo`
   * slug for the chosen-source card; the readiness report is pre-loaded, so Readiness renders it
   * directly instead of re-scanning.
   */
  repoSlug?: string
  readiness: ReadinessReport | null
  baselineResult: BaselineResult | null
  baselineTests: BaselineTestsResult | null
  netFaithful: boolean
  /** Portfolio queue: class names assured so far, and whether we drilled in from the report. */
  netted: string[]
  /**
   * Subset of {@link netted} whose baseline actually went green (initial pass or a successful
   * repair) — i.e. the classes the backend recorded a downloadable suite for. Gates the
   * test-suite download touchpoints, since `netted` also holds classes left early or quarantined.
   */
  assuredGreen?: string[]
  fromQueue: boolean
}

export const initialState: WizardState = {
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
  zipFile: null,
  readiness: null,
  baselineResult: null,
  baselineTests: null,
  netFaithful: true,
  netted: [],
  assuredGreen: [],
  fromQueue: false,
}

export type WizardAction =
  | { type: 'merge'; partial: Partial<WizardState> }
  | { type: 'reset' }
  | { type: 'advanceClass'; completed: CompletedClass; hasMore: boolean }

/**
 * Pure state transitions for the wizard. `merge` backs the child `update()` and the assure-queue
 * transitions (which still compute their partial in the component because they also drive the
 * separate step/stepReady state). `reset` is the mode-switch reset — keep the uploaded file, clear
 * everything else. `advanceClass` is the multi-class save-and-advance after Step 5.
 */
export function wizardReducer(state: WizardState, action: WizardAction): WizardState {
  switch (action.type) {
    case 'merge':
      return { ...state, ...action.partial }
    case 'reset':
      return { ...initialState, filename: state.filename, content: state.content }
    case 'advanceClass': {
      const completedClasses = [...state.completedClasses, action.completed]
      return action.hasMore
        ? {
            ...state,
            currentClassIndex: state.currentClassIndex + 1,
            completedClasses,
            interfaceResult: null,
            tests: null,
            stubResult: null,
            redBuild: null,
            implementResult: null,
            greenBuild: null,
          }
        : { ...state, completedClasses }
    }
  }
}
