import axios from 'axios'
import type { EngineParams } from '../config/engine'
import type {
  AnalysisResult,
  InterfaceResult,
  MutationJobStatus,
  TestsResult,
  StubResult,
  BuildResult,
  ImplementResult,
  ReadinessReport,
  BaselineResult,
  BaselineTestsResult,
  RepairAttemptResult,
  PullRequestResult,
  ProjectAnalysis,
  CostResult,
} from './types'

/* Real HTTP client: axios instances, the error-body interceptor, artifact download
 * helpers, and the request methods the wizard calls. */

const api = axios.create({ baseURL: '/api/migrate' })

// Assure-mode endpoints live under a sibling base path (hybrid API: analyse is
// shared via /api/migrate with a `mode` param; baseline + baseline-tests are dedicated).
const assureApi = axios.create({ baseURL: '/api/assure' })

// Surface the backend's graceful error body ({ "error": "..." }, HTTP 422 — e.g. an
// unconfigured Copilot credential or a preview Java target) as the thrown Error message,
// so each wizard step can display it inline rather than a generic status code.
const surfaceErrorBody = (error: { response?: { data?: unknown } }): Promise<never> => {
  const data = error?.response?.data
  if (data && typeof data === 'object' && 'error' in data && typeof data.error === 'string') {
    return Promise.reject(new Error(data.error))
  }
  return Promise.reject(error)
}
api.interceptors.response.use((response) => response, surfaceErrorBody)
assureApi.interceptors.response.use((response) => response, surfaceErrorBody)

/* ── Assure test-suite artifact downloads ──
 * The baseline suites are real server-side artifacts of the baseline-tests step (they ran green
 * against the untouched VB.NET), so the download buttons stream them straight from the backend
 * rather than re-zipping generated text in the browser. These are pure URL builders + a same-origin
 * anchor trigger — identical in mock and real modes (there is no client-side generation to mock).
 */
const ASSURE_BASE = assureApi.defaults.baseURL ?? '/api/assure'

/** URL of one assured class's MSTest `.cs` file. */
export const assureClassTestsUrl = (sessionId: string, className: string): string =>
  `${ASSURE_BASE}/${encodeURIComponent(sessionId)}/tests/${encodeURIComponent(className)}`

/** URL of the assembled MSTest project (`.csproj` + README + every `.cs`) as a zip. */
export const assureTestsBundleUrl = (sessionId: string): string =>
  `${ASSURE_BASE}/${encodeURIComponent(sessionId)}/tests.zip`

/** Stream a same-origin file download via a temporary anchor (no in-browser zip assembly). */
function triggerDownload(url: string, filename: string): void {
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  a.remove()
}

/** Download one assured class's baseline test file (`{Class}Tests.cs`). */
export const downloadClassTests = (sessionId: string, className: string): void =>
  triggerDownload(assureClassTestsUrl(sessionId, className), `${className}Tests.cs`)

/** Download every assured class's tests as a runnable MSTest project zip. */
export const downloadTestsBundle = (sessionId: string): void =>
  triggerDownload(assureTestsBundleUrl(sessionId), 'VBGone-Assure-Tests.zip')

export const realApi = {
  async analyse(filename: string, content: string, engine?: EngineParams): Promise<AnalysisResult> {
    const { data } = await api.post<AnalysisResult>('/analyse', { filename, content, ...engine })
    return data
  },

  async generateInterface(
    sessionId: string,
    className: string,
    engine?: EngineParams,
  ): Promise<InterfaceResult> {
    const { data } = await api.post<InterfaceResult>('/interface', {
      sessionId,
      className,
      ...engine,
    })
    return data
  },

  async generateTests(
    sessionId: string,
    className: string,
    engine?: EngineParams,
  ): Promise<TestsResult> {
    const { data } = await api.post<TestsResult>('/tests', { sessionId, className, ...engine })
    return data
  },

  async generateStub(
    sessionId: string,
    className: string,
    engine?: EngineParams,
  ): Promise<StubResult> {
    const { data } = await api.post<StubResult>('/stub', { sessionId, className, ...engine })
    return data
  },

  async build(sessionId: string): Promise<BuildResult> {
    const { data } = await api.post<BuildResult>('/build', { sessionId })
    return data
  },

  async implement(
    sessionId: string,
    className: string,
    mode: 'STUB' | 'CLAUDE',
    engine?: EngineParams,
  ): Promise<ImplementResult> {
    const { data } = await api.post<ImplementResult>('/implement', {
      sessionId,
      className,
      mode,
      ...engine,
    })
    return data
  },

  async buildAfterImplement(sessionId: string, mode: 'STUB' | 'CLAUDE'): Promise<BuildResult> {
    void mode
    const { data } = await api.post<BuildResult>('/build', { sessionId })
    return data
  },

  async retryImplement(
    sessionId: string,
    className: string,
    failingTests: string[],
    attempt?: number,
    engine?: EngineParams,
  ): Promise<ImplementResult> {
    const { data } = await api.post<ImplementResult>('/retry-implement', {
      sessionId,
      className,
      failingTests,
      attempt: attempt ?? 1,
      ...engine,
    })
    return data
  },

  async raisePR(
    sessionId: string,
    repoOwner: string,
    repoName: string,
    branchName: string,
  ): Promise<PullRequestResult> {
    const { data } = await api.post<PullRequestResult>('/pr', {
      sessionId,
      repoOwner,
      repoName,
      branchName,
    })
    return data
  },

  async uploadProject(file: File): Promise<ProjectAnalysis> {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await api.post<ProjectAnalysis>('/upload-project', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  },

  async generateBaseline(
    sessionId: string,
    className: string,
    engine?: EngineParams,
  ): Promise<BaselineResult> {
    const { data } = await assureApi.post<BaselineResult>('/baseline', {
      sessionId,
      className,
      ...engine,
    })
    return data
  },

  async runBaselineTests(
    sessionId: string,
    className: string,
    engine?: EngineParams,
  ): Promise<BaselineTestsResult> {
    const { data } = await assureApi.post<BaselineTestsResult>('/baseline-tests', {
      sessionId,
      className,
      ...engine,
    })
    return data
  },

  async rerunBaselineTests(
    sessionId: string,
    className: string,
    code: string,
  ): Promise<BaselineTestsResult> {
    const { data } = await assureApi.post<BaselineTestsResult>('/rerun-baseline-tests', {
      sessionId,
      className,
      code,
    })
    return data
  },

  async augmentBaselineTests(
    sessionId: string,
    className: string,
    code: string,
    coveragePercent: number | null,
    engine?: EngineParams,
  ): Promise<BaselineTestsResult> {
    const { data } = await assureApi.post<BaselineTestsResult>('/augment-baseline-tests', {
      sessionId,
      className,
      code,
      coveragePercent,
      ...engine,
    })
    return data
  },

  async quarantineBaseline(
    sessionId: string,
    className: string,
    code: string,
    tests: string[],
  ): Promise<BaselineTestsResult> {
    const { data } = await assureApi.post<BaselineTestsResult>('/quarantine-baseline', {
      sessionId,
      className,
      code,
      tests,
    })
    return data
  },

  async repairBaselineTest(
    sessionId: string,
    className: string,
    code: string,
    failingTest: string,
    tier: number,
    engine?: EngineParams,
  ): Promise<RepairAttemptResult> {
    const { data } = await assureApi.post<RepairAttemptResult>('/repair', {
      sessionId,
      className,
      code,
      failingTest,
      tier,
      ...engine,
    })
    return data
  },

  async startMutationTest(
    sessionId: string,
    className: string,
    suiteCode: string,
  ): Promise<MutationJobStatus> {
    const { data } = await assureApi.post<MutationJobStatus>('/mutation-test', {
      sessionId,
      className,
      suiteCode,
    })
    return data
  },

  async getMutationJob(jobId: string): Promise<MutationJobStatus> {
    const { data } = await assureApi.get<MutationJobStatus>(`/mutation-test/${jobId}`)
    return data
  },

  async assess(filename: string, content: string): Promise<ReadinessReport> {
    const { data } = await assureApi.post<ReadinessReport>('/assess', { filename, content })
    return data
  },

  async assessProject(file: File): Promise<ReadinessReport> {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await assureApi.post<ReadinessReport>('/assess-project', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  },

  async ingestRepo(url: string): Promise<ReadinessReport> {
    // Public-only, no auth: the server clones the repo, keeps .vb sources, and returns the same
    // ReadinessReport as an uploaded .zip. Specific failures (private/404, no .vb source) come back
    // as { error } bodies surfaced by the interceptor as an Error with that message.
    const { data } = await assureApi.post<ReadinessReport>('/ingest-repo', { url })
    return data
  },

  async fetchCost(sessionId: string): Promise<CostResult> {
    const { data } = await api.get<CostResult>(`/cost/${sessionId}`)
    return data
  },
}

/** The API contract, derived from the real client so the mock can never drift from it. */
export type MigrateApi = typeof realApi

export { api, assureApi, surfaceErrorBody }
