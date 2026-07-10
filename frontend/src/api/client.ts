import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig } from 'axios'
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

/* Generic request helpers: POST/GET a JSON body and unwrap the axios response to its
 * data. Every realApi method is one call to these — the named method surface (the
 * MigrateApi contract) stays intact, so the mock and callers are unaffected. */
const post = <T>(
  client: AxiosInstance,
  path: string,
  body?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> =>
  // Only forward config when supplied, so plain JSON calls stay two-arg posts.
  (config ? client.post<T>(path, body, config) : client.post<T>(path, body)).then((r) => r.data)

const get = <T>(client: AxiosInstance, path: string): Promise<T> =>
  client.get<T>(path).then((r) => r.data)

/** Build a multipart FormData body carrying a single `file` field. */
const fileUpload = (file: File): { body: FormData; config: AxiosRequestConfig } => {
  const body = new FormData()
  body.append('file', file)
  return { body, config: { headers: { 'Content-Type': 'multipart/form-data' } } }
}

export const realApi = {
  analyse: (filename: string, content: string, engine?: EngineParams): Promise<AnalysisResult> =>
    post(api, '/analyse', { filename, content, ...engine }),

  generateInterface: (
    sessionId: string,
    className: string,
    engine?: EngineParams,
  ): Promise<InterfaceResult> => post(api, '/interface', { sessionId, className, ...engine }),

  generateTests: (
    sessionId: string,
    className: string,
    engine?: EngineParams,
  ): Promise<TestsResult> => post(api, '/tests', { sessionId, className, ...engine }),

  generateStub: (
    sessionId: string,
    className: string,
    engine?: EngineParams,
  ): Promise<StubResult> => post(api, '/stub', { sessionId, className, ...engine }),

  build: (sessionId: string): Promise<BuildResult> => post(api, '/build', { sessionId }),

  implement: (
    sessionId: string,
    className: string,
    mode: 'STUB' | 'CLAUDE',
    engine?: EngineParams,
  ): Promise<ImplementResult> => post(api, '/implement', { sessionId, className, mode, ...engine }),

  buildAfterImplement: (sessionId: string, mode: 'STUB' | 'CLAUDE'): Promise<BuildResult> => {
    void mode
    return post(api, '/build', { sessionId })
  },

  retryImplement: (
    sessionId: string,
    className: string,
    failingTests: string[],
    attempt?: number,
    engine?: EngineParams,
  ): Promise<ImplementResult> =>
    post(api, '/retry-implement', {
      sessionId,
      className,
      failingTests,
      attempt: attempt ?? 1,
      ...engine,
    }),

  raisePR: (
    sessionId: string,
    repoOwner: string,
    repoName: string,
    branchName: string,
  ): Promise<PullRequestResult> => post(api, '/pr', { sessionId, repoOwner, repoName, branchName }),

  uploadProject: (file: File): Promise<ProjectAnalysis> => {
    const { body, config } = fileUpload(file)
    return post(api, '/upload-project', body, config)
  },

  generateBaseline: (
    sessionId: string,
    className: string,
    engine?: EngineParams,
  ): Promise<BaselineResult> => post(assureApi, '/baseline', { sessionId, className, ...engine }),

  runBaselineTests: (
    sessionId: string,
    className: string,
    engine?: EngineParams,
  ): Promise<BaselineTestsResult> =>
    post(assureApi, '/baseline-tests', { sessionId, className, ...engine }),

  rerunBaselineTests: (
    sessionId: string,
    className: string,
    code: string,
  ): Promise<BaselineTestsResult> =>
    post(assureApi, '/rerun-baseline-tests', { sessionId, className, code }),

  augmentBaselineTests: (
    sessionId: string,
    className: string,
    code: string,
    coveragePercent: number | null,
    engine?: EngineParams,
  ): Promise<BaselineTestsResult> =>
    post(assureApi, '/augment-baseline-tests', {
      sessionId,
      className,
      code,
      coveragePercent,
      ...engine,
    }),

  quarantineBaseline: (
    sessionId: string,
    className: string,
    code: string,
    tests: string[],
  ): Promise<BaselineTestsResult> =>
    post(assureApi, '/quarantine-baseline', { sessionId, className, code, tests }),

  repairBaselineTest: (
    sessionId: string,
    className: string,
    code: string,
    failingTest: string,
    tier: number,
    engine?: EngineParams,
  ): Promise<RepairAttemptResult> =>
    post(assureApi, '/repair', { sessionId, className, code, failingTest, tier, ...engine }),

  startMutationTest: (
    sessionId: string,
    className: string,
    suiteCode: string,
  ): Promise<MutationJobStatus> =>
    post(assureApi, '/mutation-test', { sessionId, className, suiteCode }),

  getMutationJob: (jobId: string): Promise<MutationJobStatus> =>
    get(assureApi, `/mutation-test/${jobId}`),

  assess: (filename: string, content: string): Promise<ReadinessReport> =>
    post(assureApi, '/assess', { filename, content }),

  assessProject: (file: File): Promise<ReadinessReport> => {
    const { body, config } = fileUpload(file)
    return post(assureApi, '/assess-project', body, config)
  },

  ingestRepo: (url: string): Promise<ReadinessReport> =>
    // Public-only, no auth: the server clones the repo, keeps .vb sources, and returns the same
    // ReadinessReport as an uploaded .zip. Specific failures (private/404, no .vb source) come back
    // as { error } bodies surfaced by the interceptor as an Error with that message.
    post(assureApi, '/ingest-repo', { url }),

  fetchCost: (sessionId: string): Promise<CostResult> => get(api, `/cost/${sessionId}`),
}

/** The API contract, derived from the real client so the mock can never drift from it. */
export type MigrateApi = typeof realApi

export { api, assureApi, surfaceErrorBody }
