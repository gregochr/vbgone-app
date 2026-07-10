import { useLayoutEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { InfoTip } from './InfoTip'
import { modelLabelFor } from '../../config/engine'
import type { ModelOverrides, ProviderId, Role } from '../../config/engine'

type StepRole = Role | 'source' | 'github' | 'static'

interface WizardStepperProps {
  activeSteps: { label: string; tip: ReactNode }[]
  activeRoles: StepRole[]
  provider: ProviderId
  modelOverrides: ModelOverrides
  step: number
  minStep: number
  isMultiClass: boolean
  assure: boolean
  /** The final PR step is complete (Migrate only) — shows the step-6 tick. */
  prComplete: boolean
  /** Number of classes migrated so far — drives the loop-back arc colour. */
  completedCount: number
  totalClasses: number
  currentClassIndex: number
  onStepClick: (target: number) => void
}

/**
 * The wizard's step nav: the clickable step chips plus the multi-class loop-back arc, whose
 * geometry is measured from the rendered chips in a layout effect. Extracted from WizardShell —
 * the chips and the <svg> arc must stay in one <nav> because the effect querySelects the chips
 * as siblings of the arc.
 */
export function WizardStepper({
  activeSteps,
  activeRoles,
  provider,
  modelOverrides,
  step,
  minStep,
  isMultiClass,
  assure,
  prComplete,
  completedCount,
  totalClasses,
  currentClassIndex,
  onStepClick,
}: WizardStepperProps) {
  // Loop-back arc measurement for multi-class stepper
  const navRef = useRef<HTMLElement>(null)
  const [arcPath, setArcPath] = useState<{
    d: string
    width: number
    height: number
    left: number
  } | null>(null)
  const allClassesDone = completedCount >= totalClasses
  const iterationStarted = step > 2 || completedCount > 0
  const arcColour = allClassesDone
    ? 'var(--green)'
    : iterationStarted
      ? 'var(--amber)'
      : 'var(--grey)'
  const arcColourName = allClassesDone ? 'green' : iterationStarted ? 'amber' : 'grey'

  useLayoutEffect(() => {
    if (!isMultiClass || !navRef.current) return
    const nav = navRef.current
    const interfaceBox = nav.querySelector('[data-step-index="2"]') as HTMLElement | null
    const implementBox = nav.querySelector('[data-step-index="4"]') as HTMLElement | null
    if (!interfaceBox || !implementBox) return

    const navRect = nav.getBoundingClientRect()
    const iRect = interfaceBox.getBoundingClientRect()
    const eRect = implementBox.getBoundingClientRect()

    const left = iRect.left - navRect.left + iRect.width / 2
    const right = eRect.left - navRect.left + eRect.width / 2
    const width = right - left
    const arcHeight = 22
    const totalHeight = arcHeight + 8

    // Curved path from right (Implement) down and back to left (Interface)
    const d = `M ${width} 0 C ${width} ${arcHeight}, 0 ${arcHeight}, 0 0`

    setArcPath({ d, width, height: totalHeight, left })
  }, [isMultiClass, step, currentClassIndex])

  return (
    <nav
      className={`wizard-steps${isMultiClass && arcPath ? ' has-loop-arc' : ''}`}
      ref={navRef}
      style={{ position: 'relative' }}
    >
      {activeSteps.map(({ label, tip }, i) => {
        const isCompleted = i < step || (!assure && i === 5 && prComplete)
        const isActive = i === step
        const role = activeRoles[i]
        const sub =
          role === 'source' || role === 'github' || role === 'static'
            ? role
            : modelLabelFor(provider, role, modelOverrides)
        const clickable = i >= minStep && i <= step
        return (
          <div className="wizard-step-item" key={label} data-step-index={i}>
            <div
              className={`wizard-step-box ${isActive ? 'active' : ''}`}
              role="button"
              tabIndex={clickable ? 0 : -1}
              aria-current={isActive ? 'step' : undefined}
              onClick={clickable ? () => onStepClick(i) : undefined}
              onKeyDown={
                clickable
                  ? (e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault()
                        onStepClick(i)
                      }
                    }
                  : undefined
              }
            >
              <div
                className={`wizard-step-dot ${isCompleted ? 'completed' : isActive ? 'active' : ''}`}
              >
                {isCompleted ? '✓' : i + 1}
              </div>
              <span className="wizard-step-text">
                <span className={`wizard-step-label ${isCompleted || isActive ? 'active' : ''}`}>
                  {label}
                </span>
                <span className={`wizard-step-sub ${isCompleted || isActive ? 'active' : ''}`}>
                  {sub}
                </span>
              </span>
              <span className="step-infotip">
                <InfoTip>{tip}</InfoTip>
              </span>
            </div>
            {i < activeSteps.length - 1 && (
              <div className={`wizard-step-connector ${i < step ? 'completed' : ''}`} />
            )}
          </div>
        )
      })}
      {isMultiClass && arcPath && (
        <svg
          className="loop-back-arc"
          data-testid="loop-back-arc"
          data-arc-colour={arcColourName}
          width={arcPath.width + 12}
          height={arcPath.height}
          style={{
            position: 'absolute',
            left: arcPath.left - 6,
            bottom: 0,
            pointerEvents: 'none',
            overflow: 'visible',
          }}
        >
          <defs>
            <marker
              id="loop-arrow"
              viewBox="0 0 10 10"
              refX="1"
              refY="5"
              markerWidth="6"
              markerHeight="6"
              orient="auto-start-reverse"
            >
              <path d="M 0 0 L 10 5 L 0 10 z" fill={arcColour} />
            </marker>
          </defs>
          <path
            d={arcPath.d}
            fill="none"
            stroke={arcColour}
            strokeWidth="2"
            strokeDasharray="6 3"
            markerEnd="url(#loop-arrow)"
            transform="translate(6, 2)"
          />
        </svg>
      )}
    </nav>
  )
}
