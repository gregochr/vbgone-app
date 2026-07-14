import { describe, it, expect } from 'vitest'
import { deriveAnalysis, isActionable, readinessSubtitle } from './readiness'
import type { ReadinessReport } from '../../../api/migrateApi'
import type { Bucket } from '../../../config/engine'

const m = (name: string, bucket: Bucket) => ({ name, visibility: 'public', bucket, reason: '' })

// A mixed estate: one of each bucket, so the RUNNER toggle's effect is unambiguous.
const REPORT: ReadinessReport = {
  sessionId: 's1',
  confidence: 'static',
  totals: {
    classes: 3,
    methods: 3,
    netReady: 1,
    windowsGated: 1,
    refactorFirst: 1,
    methodNetReady: 1,
    methodWindowsGated: 1,
    methodRefactorFirst: 1,
  },
  classes: [
    {
      name: 'OrderService',
      file: 'e.zip',
      bucket: 'net-ready',
      reason: 'r',
      methods: [m('A', 'net-ready')],
    },
    {
      name: 'LedgerView',
      file: 'e.zip',
      bucket: 'windows-gated',
      reason: 'r',
      methods: [m('B', 'windows-gated')],
    },
    {
      name: 'MainForm',
      file: 'e.zip',
      bucket: 'refactor-first',
      reason: 'r',
      methods: [m('C', 'refactor-first')],
    },
  ],
  restApis: [],
}

describe('readiness — Windows runner actionability', () => {
  it('isActionable: windows-gated only counts when Windows is on; tangled never does', () => {
    expect(isActionable('net-ready', false)).toBe(true)
    expect(isActionable('windows-gated', false)).toBe(false)
    expect(isActionable('windows-gated', true)).toBe(true)
    expect(isActionable('refactor-first', true)).toBe(false)
  })

  it('queues only net-ready on Linux', () => {
    expect(deriveAnalysis(REPORT, true, false).suggestedMigrationOrder).toEqual(['OrderService'])
  })

  it('adds windows-gated to the queue on Windows — but never the tangled class', () => {
    expect(deriveAnalysis(REPORT, true, true).suggestedMigrationOrder).toEqual([
      'OrderService',
      'LedgerView',
    ])
  })

  it('subtitle count reflects the runner (1 ready on Linux, 2 on Windows)', () => {
    expect(readinessSubtitle(REPORT, false)).toContain('1 of 3 classes are ready')
    expect(readinessSubtitle(REPORT, true)).toContain('2 of 3 classes are ready')
  })
})
