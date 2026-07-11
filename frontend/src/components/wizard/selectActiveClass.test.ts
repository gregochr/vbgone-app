import { describe, it, expect } from 'vitest'
import type { AnalysisResult, ClassInfo } from '../../api/migrateApi'
import { initialState, selectActiveClass, type WizardState } from './wizardState'

const classInfo = (name: string): ClassInfo => ({
  name,
  methods: [],
  dependencies: [],
  complexity: 'LOW',
})

const withAnalysis = (analysis: AnalysisResult | null, currentClassIndex = 0): WizardState => ({
  ...initialState,
  analysis,
  currentClassIndex,
})

const analysis = (over: Partial<AnalysisResult>): AnalysisResult => ({
  sessionId: 'sess-1',
  classes: [classInfo('Alpha'), classInfo('Beta')],
  suggestedMigrationOrder: ['Alpha', 'Beta'],
  summary: '',
  ...over,
})

describe('selectActiveClass', () => {
  it('picks the class at currentClassIndex from the migration order', () => {
    const result = selectActiveClass(withAnalysis(analysis({}), 1))
    expect(result.className).toBe('Beta')
    expect(result.sessionId).toBe('sess-1')
    expect(result.classInfo).toEqual(classInfo('Beta'))
  })

  it('falls back to the first class when the order slot is empty', () => {
    // index past the end of the order → falls through to classes[0]
    const result = selectActiveClass(withAnalysis(analysis({ suggestedMigrationOrder: [] }), 3))
    expect(result.className).toBe('Alpha')
    expect(result.classInfo).toEqual(classInfo('Alpha'))
  })

  it('coerces missing session and class to empty string when analysis is null', () => {
    const result = selectActiveClass(withAnalysis(null))
    expect(result).toEqual({ className: '', sessionId: '', classInfo: undefined })
  })

  it('returns undefined classInfo when the selected name is not among the classes', () => {
    const result = selectActiveClass(
      withAnalysis(analysis({ suggestedMigrationOrder: ['Ghost'], classes: [classInfo('Alpha')] })),
    )
    expect(result.className).toBe('Ghost')
    expect(result.classInfo).toBeUndefined()
  })
})
