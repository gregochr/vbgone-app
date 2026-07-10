import { describe, it, expect } from 'vitest'
import { wizardReducer, initialState } from './wizardState'
import type { WizardState, CompletedClass } from './wizardState'

const completed: CompletedClass = {
  className: 'Foo',
  interfaceResult: { sessionId: 's1', className: 'Foo', interfaceName: 'IFoo', code: 'iface' },
  tests: {
    sessionId: 's1',
    className: 'Foo',
    testClassName: 'FooTests',
    code: 'tests',
    testCount: 3,
  },
  stubResult: { sessionId: 's1', className: 'Foo', code: 'stub' },
  implementResult: { sessionId: 's1', className: 'Foo', code: 'impl', mode: 'CLAUDE' },
}

// A multi-class Migrate state part-way through class 0 of 3, with per-class artifacts present.
const midRun: WizardState = {
  ...initialState,
  filename: 'Estate.vb',
  content: 'Public Class ...',
  analysis: {
    sessionId: 's1',
    classes: [],
    suggestedMigrationOrder: ['A', 'B', 'C'],
    summary: 'x',
  },
  currentClassIndex: 0,
  interfaceResult: { sessionId: 's1', className: 'A', interfaceName: 'IA', code: 'a' },
  tests: { sessionId: 's1', className: 'A', testClassName: 'ATests', code: 't', testCount: 1 },
  stubResult: { sessionId: 's1', className: 'A', code: 's' },
  redBuild: {
    sessionId: 's1',
    buildStatus: 'RED',
    total: 1,
    passed: 0,
    failed: 1,
    errors: [],
    failedTests: [],
  },
  implementResult: { sessionId: 's1', className: 'A', code: 'i', mode: 'CLAUDE' },
  greenBuild: {
    sessionId: 's1',
    buildStatus: 'GREEN',
    total: 1,
    passed: 1,
    failed: 0,
    errors: [],
    failedTests: [],
  },
  netted: ['X'],
}

describe('wizardReducer', () => {
  it('merge applies the partial over the current state and touches nothing else', () => {
    const next = wizardReducer(midRun, {
      type: 'merge',
      partial: { fromQueue: true, netFaithful: false },
    })
    expect(next.fromQueue).toBe(true)
    expect(next.netFaithful).toBe(false)
    // Untouched fields carry through by reference-equality of value.
    expect(next.filename).toBe('Estate.vb')
    expect(next.interfaceResult).toBe(midRun.interfaceResult)
    expect(next.netted).toEqual(['X'])
  })

  it('reset clears everything except the uploaded filename + content', () => {
    const next = wizardReducer(midRun, { type: 'reset' })
    expect(next.filename).toBe('Estate.vb')
    expect(next.content).toBe('Public Class ...')
    // Everything else back to the initial blank slate.
    expect(next.analysis).toBeNull()
    expect(next.interfaceResult).toBeNull()
    expect(next.greenBuild).toBeNull()
    expect(next.currentClassIndex).toBe(0)
    expect(next.completedClasses).toEqual([])
    expect(next.netted).toEqual([])
  })

  it('advanceClass with more classes saves, advances the index, and clears per-class artifacts', () => {
    const next = wizardReducer(midRun, { type: 'advanceClass', completed, hasMore: true })
    expect(next.completedClasses).toEqual([completed])
    expect(next.currentClassIndex).toBe(1)
    // Per-class artifacts reset for the next class.
    expect(next.interfaceResult).toBeNull()
    expect(next.tests).toBeNull()
    expect(next.stubResult).toBeNull()
    expect(next.redBuild).toBeNull()
    expect(next.implementResult).toBeNull()
    expect(next.greenBuild).toBeNull()
    // Cross-class state is preserved.
    expect(next.netted).toEqual(['X'])
    expect(next.analysis).toBe(midRun.analysis)
  })

  it('advanceClass on the last class saves without advancing or clearing artifacts', () => {
    const last: WizardState = { ...midRun, currentClassIndex: 2 }
    const next = wizardReducer(last, { type: 'advanceClass', completed, hasMore: false })
    expect(next.completedClasses).toEqual([completed])
    expect(next.currentClassIndex).toBe(2)
    // Artifacts left intact — the class is done, PR step reads them.
    expect(next.interfaceResult).toBe(last.interfaceResult)
    expect(next.greenBuild).toBe(last.greenBuild)
  })

  it('advanceClass appends to existing completedClasses rather than replacing', () => {
    const prior: WizardState = { ...midRun, completedClasses: [completed] }
    const second: CompletedClass = { ...completed, className: 'B' }
    const next = wizardReducer(prior, { type: 'advanceClass', completed: second, hasMore: true })
    expect(next.completedClasses.map((c) => c.className)).toEqual(['Foo', 'B'])
  })
})
