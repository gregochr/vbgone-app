/**
 * Public API surface for the migration wizard. Thin barrel: the HTTP client lives in
 * ./client, the mock implementation in ./mocks, the shared types in ./types, and the
 * demo fixtures in ./demos. VITE_USE_MOCKS selects which implementation the wizard uses.
 */
export * from './types'
export * from './demos'
export {
  api,
  assureClassTestsUrl,
  assureTestsBundleUrl,
  downloadClassTests,
  downloadTestsBundle,
} from './client'

import { realApi } from './client'
import { mockApi } from './mocks'

const USE_MOCKS = import.meta.env.VITE_USE_MOCKS === 'true'
const active = USE_MOCKS ? mockApi : realApi

export const analyse = active.analyse
export const generateInterface = active.generateInterface
export const generateTests = active.generateTests
export const generateStub = active.generateStub
export const build = active.build
export const implement = active.implement
export const buildAfterImplement = active.buildAfterImplement
export const retryImplement = active.retryImplement
export const raisePR = active.raisePR
export const uploadProject = active.uploadProject
export const generateBaseline = active.generateBaseline
export const runBaselineTests = active.runBaselineTests
export const rerunBaselineTests = active.rerunBaselineTests
export const augmentBaselineTests = active.augmentBaselineTests
export const quarantineBaseline = active.quarantineBaseline
export const repairBaselineTest = active.repairBaselineTest
export const startMutationTest = active.startMutationTest
export const getMutationJob = active.getMutationJob
export const assess = active.assess
export const assessProject = active.assessProject
export const fetchCost = active.fetchCost
