/**
 * Shared types and view helpers for the Assure auto-repair attempt cards (Step 4).
 *
 * The loop runs against the user's untouched original VB.NET, so a failing test can only mean
 * the test is wrong — the backend rewrites just that test to match the real observed behaviour,
 * escalating mechanical → reasoning → escalation, then quarantining rather than faking a pass.
 * These types mirror the backend `RepairAttempt` contract so the card renders it directly.
 */

export type RepairRole = 'mechanical' | 'reasoning' | 'escalation'

/** Terminal result of a single attempt. Drives the chip label/colour and card border. */
export type RepairTag = 'green' | 'red' | 'escalated' | 'flag' | 'nofix'

export interface DiffLine {
  op: '-' | '+' | ' '
  text: string
}

export interface RepairAttempt {
  /** Human tier name shown on the card. */
  tier: string
  role: RepairRole
  rationale: string
  /** Empty when there was no valid edit to make (the gate returns NO EDIT). */
  diff: DiffLine[]
  /** Validity gate: a green result is not enough — the rewrite must still be a real test. */
  gate: { ok: boolean; note: string }
  /** null when the attempt produced no edit to re-run. */
  rerun: { green: boolean; note: string } | null
  tag: RepairTag
}

/** Result-chip label + status colour keyed by terminal tag. */
export const TAG_META: Record<RepairTag, { label: string; color: string }> = {
  green: { label: '✓ green', color: 'var(--green)' },
  red: { label: '✕ still red', color: 'var(--red)' },
  escalated: { label: '→ escalated', color: 'var(--amber)' },
  flag: { label: '⚠ value changed', color: 'var(--amber)' },
  nofix: { label: '∅ no valid fix', color: 'var(--red)' },
}

/** Card border colour reflecting the terminal tag; running cards use the neutral border. */
export function cardBorderFor(tag: RepairTag | 'running'): string {
  switch (tag) {
    case 'green':
      return 'rgba(52,211,153,0.28)'
    case 'red':
    case 'nofix':
      return 'rgba(251,111,115,0.24)'
    case 'escalated':
    case 'flag':
      return 'rgba(227,168,60,0.24)'
    default:
      return '#23262d'
  }
}
