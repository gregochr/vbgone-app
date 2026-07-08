import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { WizardConfigProvider } from '../../../config/WizardConfigContext'
import { StepReadiness } from './StepReadiness'
import * as api from '../../../api/migrateApi'
import type { WizardState } from '../WizardShell'
import type { ReadinessReport } from '../../../api/migrateApi'

// The three touchpoints call the download helpers; stub only those so we can assert wiring
// (the helpers' own anchor/URL behaviour is covered in api/assureDownloads.test.ts).
vi.mock('../../../api/migrateApi', async () => {
  const actual =
    await vi.importActual<typeof import('../../../api/migrateApi')>('../../../api/migrateApi')
  return { ...actual, downloadClassTests: vi.fn(), downloadTestsBundle: vi.fn() }
})

const rep: ReadinessReport = {
  sessionId: 'sess-9',
  confidence: 'static',
  totals: {
    classes: 3,
    methods: 6,
    netReady: 2,
    windowsGated: 0,
    refactorFirst: 1,
    methodNetReady: 4,
    methodWindowsGated: 0,
    methodRefactorFirst: 2,
  },
  classes: [
    {
      name: 'OrderService',
      file: 'Services/OrderService.vb',
      bucket: 'net-ready',
      reason: 'public, no WinForms references',
      methods: [
        { name: 'CalculateTotal', visibility: 'public', bucket: 'net-ready', reason: 'pure' },
        { name: 'SplitPerHead', visibility: 'public', bucket: 'net-ready', reason: 'pure' },
      ],
    },
    {
      name: 'InvoiceService',
      file: 'Services/InvoiceService.vb',
      bucket: 'net-ready',
      reason: 'public, no WinForms references',
      methods: [
        { name: 'Render', visibility: 'public', bucket: 'net-ready', reason: 'pure' },
        { name: 'Total', visibility: 'public', bucket: 'net-ready', reason: 'pure' },
      ],
    },
    {
      name: 'Form1',
      file: 'Form1.vb',
      bucket: 'refactor-first',
      reason: 'logic welded into Button_Click',
      methods: [
        { name: 'btnAdd_Click', visibility: 'private', bucket: 'refactor-first', reason: 'UI' },
        { name: 'btnSub_Click', visibility: 'private', bucket: 'refactor-first', reason: 'UI' },
      ],
    },
  ],
}

const baseState: WizardState = {
  filename: 'LegacyEstate.zip',
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
  fromQueue: false,
}

const renderReport = (netted: string[]) =>
  render(
    <WizardConfigProvider>
      <StepReadiness
        state={{ ...baseState, readiness: rep, netted }}
        update={() => {}}
        onReady={() => {}}
        onAssureClass={() => {}}
      />
    </WizardConfigProvider>,
  )

const rowFor = (name: string): HTMLElement => {
  const table = screen.getByTestId('class-table')
  const row = within(table).getByText(name).closest('.class-row-wrap')
  if (!row) throw new Error(`row for ${name} not found`)
  return row as HTMLElement
}

describe('StepReadiness — test-suite download touchpoints', () => {
  beforeEach(() => vi.clearAllMocks())

  it('per-row: an assured class exposes a ↓ tests button that downloads that class file', async () => {
    const user = userEvent.setup()
    renderReport(['OrderService'])

    const orderRow = rowFor('OrderService')
    expect(within(orderRow).getByText('✓ Assured')).toBeInTheDocument()
    await user.click(within(orderRow).getByRole('button', { name: /↓ tests/ }))

    expect(api.downloadClassTests).toHaveBeenCalledWith('sess-9', 'OrderService')
  })

  it('gating: a ready but not-yet-assured class offers Assure, not a download', () => {
    renderReport(['OrderService'])

    const invoiceRow = rowFor('InvoiceService')
    expect(within(invoiceRow).queryByRole('button', { name: /↓ tests/ })).toBeNull()
    expect(
      within(invoiceRow).getByRole('button', { name: /Assure this class/ }),
    ).toBeInTheDocument()
  })

  it('mid-flow bulk: the progress card downloads all assured suites as a zip', async () => {
    const user = userEvent.setup()
    renderReport(['OrderService'])

    const card = screen.getByTestId('queue-progress')
    await user.click(within(card).getByRole('button', { name: /Download all tests \(1\)/ }))

    expect(api.downloadTestsBundle).toHaveBeenCalledWith('sess-9')
  })

  it('shows no bulk download before any class is assured', () => {
    renderReport([])

    expect(screen.queryByTestId('queue-progress')).toBeNull()
    expect(screen.queryByRole('button', { name: /Download all tests/ })).toBeNull()
  })

  it('completion: the proceed panel flips to a zip download once every ready class is assured', async () => {
    const user = userEvent.setup()
    renderReport(['OrderService', 'InvoiceService'])

    const panel = screen.getByTestId('proceed-panel')
    expect(within(panel).getByText('All ready classes assured')).toBeInTheDocument()
    expect(within(panel).queryByRole('button', { name: /Assure the/ })).toBeNull()

    await user.click(within(panel).getByRole('button', { name: /Download all tests \(\.zip\)/ }))
    expect(api.downloadTestsBundle).toHaveBeenCalledWith('sess-9')
  })
})
