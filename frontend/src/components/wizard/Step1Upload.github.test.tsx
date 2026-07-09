import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useEffect } from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { WizardConfigProvider, useWizardConfig } from '../../config/WizardConfigContext'
import { Step1Upload } from './Step1Upload'
import type { WizardState } from './WizardShell'
import { ingestRepo } from '../../api/migrateApi'
import type { ReadinessReport } from '../../api/migrateApi'
import { REPO_MESSAGES } from '../../config/repoUrl'

// Real parseRepo / deriveAnalysis; only the network ingest is stubbed.
vi.mock('../../api/migrateApi', async () => {
  const actual =
    await vi.importActual<typeof import('../../api/migrateApi')>('../../api/migrateApi')
  return { ...actual, ingestRepo: vi.fn() }
})

const mockIngest = vi.mocked(ingestRepo)

const emptyState: WizardState = {
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
  fromQueue: false,
}

const MIXED_REPORT: ReadinessReport = {
  sessionId: 'sess-1',
  confidence: 'static',
  totals: {
    classes: 142,
    methods: 1180,
    netReady: 68,
    windowsGated: 41,
    refactorFirst: 33,
    methodNetReady: 540,
    methodWindowsGated: 360,
    methodRefactorFirst: 280,
  },
  classes: [
    {
      name: 'OrderService',
      file: 'Services/OrderService.vb',
      bucket: 'net-ready',
      reason: 'public, no WinForms references',
      methods: [
        {
          name: 'CalculateTotal',
          visibility: 'public',
          bucket: 'net-ready',
          reason: 'value in/out',
        },
      ],
    },
  ],
}

type Props = {
  state: WizardState
  update: (p: Partial<WizardState>) => void
  onReady: () => void
}

/** Flip the shared config to Assure so the GitHub-ingestion section (assure-gated) renders. */
function AssureHarness(props: Props) {
  const { setMode } = useWizardConfig()
  useEffect(() => {
    setMode('assure')
  }, [setMode])
  return <Step1Upload {...props} />
}

const renderAssure = (props: Props) =>
  render(
    <WizardConfigProvider>
      <AssureHarness {...props} />
    </WizardConfigProvider>,
  )

const typeUrl = async (user: ReturnType<typeof userEvent.setup>, value: string) => {
  const input = await screen.findByPlaceholderText('https://github.com/org/legacy-app')
  await user.clear(input)
  await user.type(input, value)
  return input
}

describe('Step1Upload — GitHub repo ingestion', () => {
  beforeEach(() => {
    mockIngest.mockReset()
  })

  it('renders the GitHub section (divider, input, note) in Assure mode', async () => {
    renderAssure({ state: emptyState, update: vi.fn(), onReady: vi.fn() })

    expect(await screen.findByText('OR ANALYSE A GITHUB REPO')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('https://github.com/org/legacy-app')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Analyse' })).toBeInTheDocument()
    expect(screen.getByText('Public repos only')).toBeInTheDocument()
  })

  it('rejects a non-github URL client-side without calling the backend', async () => {
    const user = userEvent.setup()
    renderAssure({ state: emptyState, update: vi.fn(), onReady: vi.fn() })

    await typeUrl(user, 'https://gitlab.com/org/legacy-app')
    await user.click(screen.getByRole('button', { name: 'Analyse' }))

    expect(await screen.findByTestId('repo-error')).toHaveTextContent(REPO_MESSAGES.nonGithub)
    expect(mockIngest).not.toHaveBeenCalled()
  })

  it('ingests a valid repo, pre-loads the report, and enters the portfolio path', async () => {
    const user = userEvent.setup()
    const update = vi.fn()
    const onReady = vi.fn()
    mockIngest.mockResolvedValue(MIXED_REPORT)

    renderAssure({ state: emptyState, update, onReady })

    await typeUrl(user, 'https://github.com/org/legacy-app')
    await user.click(screen.getByRole('button', { name: 'Analyse' }))

    await waitFor(() =>
      expect(mockIngest).toHaveBeenCalledWith('https://github.com/org/legacy-app'),
    )
    await waitFor(() =>
      expect(update).toHaveBeenCalledWith(
        expect.objectContaining({
          repoSlug: 'org/legacy-app',
          readiness: MIXED_REPORT,
          filename: 'org-legacy-app.zip',
          analysis: expect.objectContaining({ sessionId: 'sess-1' }),
        }),
      ),
    )
    expect(onReady).toHaveBeenCalled()
  })

  it('surfaces a backend private/404 message inline on the error line', async () => {
    const user = userEvent.setup()
    mockIngest.mockRejectedValue(
      new Error(
        'Can’t reach org/secret — it’s private or doesn’t exist. VBGone only reads public repositories (no sign-in).',
      ),
    )

    renderAssure({ state: emptyState, update: vi.fn(), onReady: vi.fn() })

    await typeUrl(user, 'https://github.com/org/secret')
    await user.click(screen.getByRole('button', { name: 'Analyse' }))

    expect(await screen.findByTestId('repo-error')).toHaveTextContent(/private or doesn’t exist/)
  })

  it('submits on Enter and clears the error as the user types', async () => {
    const user = userEvent.setup()
    mockIngest.mockResolvedValue(MIXED_REPORT)
    renderAssure({ state: emptyState, update: vi.fn(), onReady: vi.fn() })

    // First: a bad URL sets the error line.
    await typeUrl(user, 'notaurl')
    await user.click(screen.getByRole('button', { name: 'Analyse' }))
    expect(await screen.findByTestId('repo-error')).toBeInTheDocument()

    // Typing again clears the error, and Enter triggers analyse.
    const input = await typeUrl(user, 'https://github.com/org/legacy-app')
    expect(screen.queryByTestId('repo-error')).not.toBeInTheDocument()
    await user.type(input, '{Enter}')
    await waitFor(() =>
      expect(mockIngest).toHaveBeenCalledWith('https://github.com/org/legacy-app'),
    )
  })

  it('shows the chosen-source card (slug + meta) once a repo is ingested', async () => {
    const chosen: WizardState = {
      ...emptyState,
      filename: 'org-legacy-app.zip',
      repoSlug: 'org/legacy-app',
      readiness: MIXED_REPORT,
    }
    renderAssure({ state: chosen, update: vi.fn(), onReady: vi.fn() })

    const card = await screen.findByTestId('repo-chosen')
    expect(card).toHaveTextContent('org/legacy-app')
    expect(card).toHaveTextContent(
      'github repo · 142 .vb classes · 1,180 methods · non-source skipped',
    )
    // The URL input is gone once a source is chosen.
    expect(
      screen.queryByPlaceholderText('https://github.com/org/legacy-app'),
    ).not.toBeInTheDocument()
  })
})
