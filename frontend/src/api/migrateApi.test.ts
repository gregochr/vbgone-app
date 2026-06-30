import { describe, it, expect } from 'vitest'
import {
  analyse,
  generateInterface,
  generateTests,
  generateStub,
  build,
  implement,
  buildAfterImplement,
  raisePR,
  fetchCost,
  generateBaseline,
  runBaselineTests,
  rerunBaselineTests,
  DEMO_PROTECT_CONTENT,
  DEMO_COMPLEX_CONTENT,
} from './migrateApi'

describe('migrateApi mock functions', () => {
  it('analyse returns session with classes', async () => {
    const result = await analyse('Test.vb', 'content')
    expect(result.sessionId).toBeTruthy()
    expect(result.classes).toHaveLength(1)
    expect(result.classes[0].name).toBe('Form1')
    expect(result.classes[0].methods).toContain('Button1_Click')
    expect(result.classes[0].methods).toHaveLength(6)
    expect(result.suggestedMigrationOrder).toEqual(['Form1'])
    expect(result.summary).toBeTruthy()
  })

  it('analyse returns correct complexity', async () => {
    const result = await analyse('Test.vb', 'content')
    expect(result.classes[0].complexity).toBe('LOW')
  })

  it('generateInterface returns interface code', async () => {
    const result = await generateInterface('session-1', 'Foo')
    expect(result.interfaceName).toBe('IFoo')
    expect(result.className).toBe('Foo')
    expect(result.code).toContain('public interface IFoo')
  })

  it('generateTests returns test class and count', async () => {
    const result = await generateTests('session-1', 'Foo')
    expect(result.testClassName).toBe('FooTests')
    expect(result.testCount).toBe(30)
    expect(result.code).toContain('[TestFixture]')
    expect(result.code).toContain('FooTests')
  })

  it('generateStub returns stub with NotImplementedException', async () => {
    const result = await generateStub('session-1', 'Foo')
    expect(result.className).toBe('Foo')
    expect(result.code).toContain('NotImplementedException')
    expect(result.code).toContain('IFoo')
  })

  it('build returns RED status with all tests failing', async () => {
    const result = await build('session-1')
    expect(result.buildStatus).toBe('RED')
    expect(result.total).toBe(30)
    expect(result.passed).toBe(0)
    expect(result.failed).toBe(30)
  })

  it('implement with CLAUDE returns real implementation', async () => {
    const result = await implement('session-1', 'Foo', 'CLAUDE')
    expect(result.mode).toBe('CLAUDE')
    expect(result.code).toContain('a + b')
    expect(result.code).not.toContain('NotImplementedException')
  })

  it('implement with STUB returns stub', async () => {
    const result = await implement('session-1', 'Foo', 'STUB')
    expect(result.mode).toBe('STUB')
    expect(result.code).toContain('NotImplementedException')
  })

  it('buildAfterImplement returns GREEN for CLAUDE mode', async () => {
    const result = await buildAfterImplement('session-1', 'CLAUDE')
    expect(result.buildStatus).toBe('GREEN')
    expect(result.passed).toBe(30)
    expect(result.failed).toBe(0)
  })

  it('buildAfterImplement returns RED for STUB mode', async () => {
    const result = await buildAfterImplement('session-1', 'STUB')
    expect(result.buildStatus).toBe('RED')
    expect(result.passed).toBe(0)
    expect(result.failed).toBe(30)
  })

  it('raisePR returns PR URL and committed files', async () => {
    const result = await raisePR('session-1', 'owner', 'repo', 'migrate/foo')
    expect(result.prUrl).toBe('https://github.com/owner/repo/pull/1')
    expect(result.branchName).toBe('migrate/foo')
    expect(result.filesCommitted).toHaveLength(3)
  })

  it('fetchCost returns cost result', async () => {
    const result = await fetchCost('session-1')
    expect(result.sessionId).toBeTruthy()
    expect(result.steps).toEqual([])
    expect(result.totalCost).toBe(0)
  })

  it('generateBaseline returns the pinned public surface with defect tags', async () => {
    const result = await generateBaseline('session-1', 'OrderProcessor')
    expect(result.surfaceFile).toBe('OrderProcessor.dll · public surface')
    expect(result.members.length).toBeGreaterThan(0)
    const flagged = result.members.find((m) => m.defect)
    expect(flagged?.defect).toBeTruthy()
  })

  it('runBaselineTests returns a faithful green net against the original', async () => {
    const result = await runBaselineTests('session-1', 'OrderProcessor')
    expect(result.netFaithful).toBe(true)
    expect(result.testClassName).toBe('OrderProcessorBaseline')
    expect(result.build.buildStatus).toBe('GREEN')
    expect(result.build.passed).toBe(result.build.total)
    expect(result.code).toContain('[TestClass]')
    expect(result.failures).toEqual([])
  })

  it('rerunBaselineTests runs the edited net (no regeneration) and reflects it', async () => {
    const edited = '[TestClass] public class OrderProcessorBaseline { /* corrected */ }'
    const result = await rerunBaselineTests('session-1', 'OrderProcessor', edited)
    expect(result.netFaithful).toBe(true)
    expect(result.code).toBe(edited)
  })
})

describe('Protect demo source', () => {
  // The real characterisation run compiles the original VB headless on the Linux CLR sidecar,
  // which can't host WinForms. The Protect demo must therefore be UI-free or it lands in the
  // degraded ERROR path instead of green. (The Migrate complex demo, by contrast, IS WinForms.)
  const WINFORMS_MARKERS = [
    'System.Windows.Forms',
    'Inherits Form',
    'MsgBox',
    'MessageBox',
    'Handles ',
    'As TextBox',
    'As Button',
    '.Click',
  ]

  it('the Protect demo has no WinForms references (compiles standalone)', () => {
    for (const marker of WINFORMS_MARKERS) {
      expect(DEMO_PROTECT_CONTENT).not.toContain(marker)
    }
  })

  it('keeps the supporting types the characterisation suite instantiates', () => {
    expect(DEMO_PROTECT_CONTENT).toContain('Public Class OrderProcessor')
    expect(DEMO_PROTECT_CONTENT).toContain('Public Class LineItem')
    expect(DEMO_PROTECT_CONTENT).toContain('Public Class Order')
  })

  it('confirms the Migrate complex demo is the WinForms variant (contrast)', () => {
    expect(DEMO_COMPLEX_CONTENT).toContain('System.Windows.Forms')
  })
})
