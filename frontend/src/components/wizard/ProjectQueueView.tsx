import { useState, useCallback, useRef, useEffect } from 'react'
import type { ProjectAnalysis, ClassInfo, CostResult, PullRequestResult } from '../../api/migrateApi'
import { fetchCost, raisePR } from '../../api/migrateApi'
import { WizardShell } from './WizardShell'
import { DependencyGraph } from './DependencyGraph'

export type ClassStatus = 'Pending' | 'In Progress' | 'Complete' | 'PR Raised'

/** Returns true when ALL dependencies of className have status Complete or PR Raised. */
export function isClassEnabled(
  className: string,
  dependencyGraph: Record<string, string[]>,
  statuses: Record<string, ClassStatus>,
): boolean {
  const deps = dependencyGraph[className] || []
  return deps.every((d) => statuses[d] === 'Complete' || statuses[d] === 'PR Raised')
}

/** Returns the first unmigrated dependency name, or null if all are migrated. */
export function firstUnmigratedDep(
  className: string,
  dependencyGraph: Record<string, string[]>,
  statuses: Record<string, ClassStatus>,
): string | null {
  const deps = dependencyGraph[className] || []
  return deps.find((d) => statuses[d] !== 'Complete' && statuses[d] !== 'PR Raised') || null
}

interface Props {
  analysis: ProjectAnalysis
  onBackToUpload: () => void
}

export function ProjectQueueView({ analysis, onBackToUpload }: Props) {
  const [statuses, setStatuses] = useState<Record<string, ClassStatus>>(() => {
    const initial: Record<string, ClassStatus> = {}
    for (const cls of analysis.classes) {
      initial[cls.name] = 'Pending'
    }
    return initial
  })
  const [activeClass, setActiveClass] = useState<string | null>(null)
  const [totalCost, setTotalCost] = useState(0)
  const [prResult, setPrResult] = useState<PullRequestResult | null>(null)
  const [prLoading, setPrLoading] = useState(false)
  const [prError, setPrError] = useState<string | null>(null)
  const sessionIdRef = useRef(analysis.sessionId)

  const refreshCost = useCallback(() => {
    fetchCost(sessionIdRef.current)
      .then((result: CostResult) => setTotalCost(result.totalCost))
      .catch(() => {})
  }, [])

  useEffect(() => {
    refreshCost()
  }, [refreshCost, statuses])

  const migratedCount = Object.values(statuses).filter(
    (s) => s === 'Complete' || s === 'PR Raised',
  ).length
  const totalClasses = analysis.classes.length
  const allComplete = migratedCount === totalClasses && totalClasses > 0
  const progressPercent = totalClasses > 0 ? (migratedCount / totalClasses) * 100 : 0

  const handleMigrate = (className: string) => {
    setStatuses((prev) => ({ ...prev, [className]: 'In Progress' }))
    setActiveClass(className)
  }

  const handleWizardComplete = (className: string) => {
    setStatuses((prev) => ({
      ...prev,
      [className]: 'Complete',
    }))
    setActiveClass(null)
    refreshCost()
  }

  const projectName = analysis.summary.split('.')[0] || 'project'
  const handleRaisePR = () => {
    const branchName = `migrate/${projectName.toLowerCase().replace(/\s+/g, '-')}`
    setPrLoading(true)
    setPrError(null)
    raisePR(analysis.sessionId, 'gregochr', 'vbgone-output', branchName)
      .then((result) => {
        setPrResult(result)
        setPrLoading(false)
        setStatuses((prev) => {
          const next = { ...prev }
          for (const key of Object.keys(next)) {
            if (next[key] === 'Complete') next[key] = 'PR Raised'
          }
          return next
        })
      })
      .catch((err) => {
        setPrLoading(false)
        setPrError(err instanceof Error ? err.message : 'PR creation failed')
      })
  }

  const handleBackToQueue = () => {
    if (activeClass) {
      setStatuses((prev) => ({ ...prev, [activeClass]: 'Pending' }))
    }
    setActiveClass(null)
  }

  // When a class is being migrated, show the wizard for that class
  if (activeClass) {
    const classInfo = analysis.classes.find((c) => c.name === activeClass)
    if (classInfo) {
      return (
        <ProjectWizardWrapper
          sessionId={analysis.sessionId}
          classInfo={classInfo}
          classIndex={analysis.suggestedMigrationOrder.indexOf(activeClass) + 1}
          totalClasses={totalClasses}
          onComplete={() => handleWizardComplete(activeClass)}
          onBackToQueue={handleBackToQueue}
        />
      )
    }
  }

  // Otherwise show the queue view
  return (
    <div className="project-queue-wrapper">
      <div className="project-queue">
        <div className="queue-header">
        <div>
          <h2 className="step-title">Migration Queue</h2>
          <p className="step-subtitle">{analysis.summary}</p>
        </div>
      </div>

      {/* Project summary */}
      <div className="queue-summary">
        <div className="queue-stat">
          <span className="queue-stat-value">{totalClasses}</span>
          <span className="queue-stat-label">Classes</span>
        </div>
        <div className="queue-stat">
          <span className="queue-stat-value">{migratedCount}</span>
          <span className="queue-stat-label">Migrated</span>
        </div>
        <div className="queue-stat">
          <span className="queue-stat-value">
            {analysis.classes.filter((c) => c.complexity === 'HIGH').length}
          </span>
          <span className="queue-stat-label">High Complexity</span>
        </div>
        {totalCost > 0 && (
          <div className="queue-stat">
            <span className="queue-stat-value">${totalCost.toFixed(4)}</span>
            <span className="queue-stat-label">Total Cost</span>
          </div>
        )}
      </div>

      {/* Progress bar */}
      <div className="queue-progress">
        <div className="queue-progress-bar">
          <div
            className="queue-progress-fill"
            style={{ width: `${progressPercent}%` }}
            data-testid="progress-fill"
          />
        </div>
        <span className="queue-progress-text">
          {migratedCount} of {totalClasses} classes migrated
        </span>
      </div>

      {/* Dependency graph */}
      <DependencyGraph
        classes={analysis.classes}
        dependencyGraph={analysis.dependencyGraph}
        statuses={statuses}
        onMigrate={handleMigrate}
      />

      {/* Class cards — collapsible */}
      <CollapsibleCards
        totalClasses={totalClasses}
        classes={analysis.classes}
        migrationOrder={analysis.suggestedMigrationOrder}
        dependencyGraph={analysis.dependencyGraph}
        statuses={statuses}
        onMigrate={handleMigrate}
      />

      {/* Raise PR — only when all classes are complete */}
      {!prResult && (
        <div className="queue-pr-section">
          <button
            className="btn-plex btn-raise-pr"
            onClick={handleRaisePR}
            disabled={!allComplete || prLoading}
            title={!allComplete ? 'Migrate all classes before raising a PR' : 'Raise a single PR for all migrated classes'}
          >
            {prLoading ? 'Raising PR...' : 'Raise PR'}
          </button>
          {prError && <p className="queue-pr-error">{prError}</p>}
        </div>
      )}

      {prResult && (
        <div className="queue-pr-result">
          <button className="btn-pr-success" disabled>
            {'\u2713'} PR Raised
          </button>
          <div className="info-card" style={{ marginTop: 12 }}>
            <div style={{ marginBottom: 12 }}>
              <a className="pr-link" href={prResult.prUrl} target="_blank" rel="noopener noreferrer">
                {prResult.prUrl}
              </a>
            </div>
            <div style={{ color: 'var(--grey)', fontSize: '0.85rem', marginBottom: 12 }}>
              Branch: <code>{prResult.branchName}</code>
            </div>
            <h4 style={{ marginBottom: 8 }}>Files committed</h4>
            <ul className="file-list">
              {prResult.filesCommitted.map((f) => (
                <li key={f}>{f}</li>
              ))}
            </ul>
          </div>
        </div>
      )}

      </div>

      <div className="wizard-nav">
        <button className="btn-back" onClick={onBackToUpload}>
          Back
        </button>
        <span className="coffee-text">
          Made with {'\u2615'} by Chris —{' '}
          <a
            className="coffee-link"
            href="https://buymeacoffee.com/gregorychris"
            target="_blank"
            rel="noopener noreferrer"
          >
            Buy me a coffee
          </a>
        </span>
      </div>
    </div>
  )
}

function CollapsibleCards({
  totalClasses,
  classes,
  migrationOrder,
  dependencyGraph,
  statuses,
  onMigrate,
}: {
  totalClasses: number
  classes: ClassInfo[]
  migrationOrder: string[]
  dependencyGraph: Record<string, string[]>
  statuses: Record<string, ClassStatus>
  onMigrate: (className: string) => void
}) {
  const [open, setOpen] = useState(true)
  const contentRef = useRef<HTMLDivElement>(null)
  const [maxHeight, setMaxHeight] = useState<string | undefined>(undefined)

  useEffect(() => {
    if (contentRef.current) {
      setMaxHeight(open ? `${contentRef.current.scrollHeight}px` : '0px')
    }
  }, [open, statuses])

  return (
    <div className="collapsible-cards">
      <button
        className="collapsible-cards-header"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
      >
        <span className={`collapsible-chevron ${open ? 'open' : ''}`}>{'\u25B6'}</span>
        Migration Queue ({totalClasses} classes)
      </button>
      <div
        className="collapsible-cards-body"
        ref={contentRef}
        style={{
          maxHeight: maxHeight,
          opacity: open ? 1 : 0,
        }}
      >
        <div className="queue-cards">
          {migrationOrder.map((className) => {
            const cls = classes.find((c) => c.name === className)
            if (!cls) return null
            const status = statuses[className]
            const deps = dependencyGraph[className] || []
            const enabled = isClassEnabled(className, dependencyGraph, statuses)
            const blockedBy = firstUnmigratedDep(className, dependencyGraph, statuses)
            return (
              <div
                className={`queue-card queue-card-${status.toLowerCase().replace(' ', '-')}`}
                key={className}
              >
                <div className="queue-card-header">
                  <h3 className="queue-card-name">{className}</h3>
                  <span className={`badge badge-${cls.complexity.toLowerCase()}`}>
                    {cls.complexity}
                  </span>
                </div>
                <div className="queue-card-methods">
                  {cls.methods.map((m) => (
                    <span className="method-tag" key={m}>
                      {m}
                    </span>
                  ))}
                </div>
                {deps.length > 0 && (
                  <div className="queue-card-deps">
                    Depends on:{' '}
                    {deps.map((d, i) => (
                      <span key={d}>
                        <strong>{d}</strong>
                        {i < deps.length - 1 ? ', ' : ''}
                      </span>
                    ))}
                  </div>
                )}
                <div className="queue-card-footer">
                  <span
                    className={`queue-status queue-status-${status.toLowerCase().replace(' ', '-')}`}
                  >
                    {status === 'PR Raised' && '\u2713 '}
                    {status === 'Complete' && '\u2713 '}
                    {status}
                  </span>
                  {status === 'Pending' && (
                    <button
                      className="btn-plex btn-migrate"
                      onClick={() => onMigrate(className)}
                      disabled={!enabled}
                      title={!enabled && blockedBy ? `Migrate ${blockedBy} first` : undefined}
                    >
                      Migrate
                    </button>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}

// Wrapper that renders the existing WizardShell pre-configured for a specific class
function ProjectWizardWrapper({
  sessionId,
  classInfo,
  classIndex,
  totalClasses,
  onComplete,
  onBackToQueue,
}: {
  sessionId: string
  classInfo: ClassInfo
  classIndex: number
  totalClasses: number
  onComplete: (raised: boolean) => void
  onBackToQueue: () => void
}) {
  return (
    <div className="project-wizard-wrapper">
      <WizardShell
        projectMode={{
          sessionId,
          className: classInfo.name,
          classIndex,
          totalClasses,
          onComplete,
          onBackToQueue,
        }}
      />
    </div>
  )
}
