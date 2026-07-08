/**
 * Auto-repair demo scenarios for Assure Step 4 ("Confirm the baseline").
 *
 * Assure generates an MSTest suite that records how the *original, unmodified* VB.NET
 * behaves today, then runs it against that original. Because the code is untouched, a
 * failing test can only mean the test is wrong — not the code. The auto-repair loop
 * rewrites the failing test to match observed behaviour, with up to 3 escalating attempts
 * (mechanical → reasoning → escalation), then gives up gracefully (quarantine).
 *
 * These three arcs are the acceptance scenarios from the design handoff. In production the
 * scenario is whatever the real failure is; here they let the live demo exercise every
 * outcome via the "simulate a failed run" affordance.
 *
 * NB: this is Assure-specific. In Migrate mode a red test is ambiguous (could be a bad
 * port), so this loop must not be reused there as-is.
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

export type RepairScenarioKey = 'easy' | 'stubborn' | 'unfixable'

export interface RepairScenario {
  key: RepairScenarioKey
  /** Label for the demo scenario switcher. */
  label: string
  /** The failing test that triggered the loop. */
  drift: { test: string; call: string; message: string; count: string }
  outcome: 'succeeded' | 'quarantined'
  attempts: RepairAttempt[]
}

/** Ordered for the segmented DEMO FAILURE switcher: Simple fix · Needs escalation · Unfixable. */
export const REPAIR_SCENARIOS: Record<RepairScenarioKey, RepairScenario> = {
  easy: {
    key: 'easy',
    label: 'Simple fix',
    drift: {
      test: 'PlaceOrder_TotalWithFraction_TruncatesToInt',
      call: 'sut.PlaceOrder(3, 9.9m)',
      message: 'Assert.AreEqual failed. Expected:<12>. Actual:<13>.',
      count: '1 / 23',
    },
    outcome: 'succeeded',
    attempts: [
      {
        tier: 'Mechanical',
        role: 'mechanical',
        rationale:
          'Just one wrong number. The observed return is 13 — VB.NET CInt uses banker’s rounding (9.9 → 10), so the “truncates” premise was wrong. Correcting the expected value and the misleading name.',
        diff: [
          { op: '-', text: 'void PlaceOrder_TotalWithFraction_TruncatesToInt()' },
          { op: '+', text: 'void PlaceOrder_TotalWithFraction_RoundsToInt()' },
          { op: '-', text: 'Assert.AreEqual(12, result);   // 9.9 truncates to 9' },
          { op: '+', text: 'Assert.AreEqual(13, result);   // CInt rounds 9.9 -> 10' },
        ],
        gate: {
          ok: true,
          note: 'Still calls PlaceOrder(3, 9.9m) and still checks the return value. Not turned into a meaningless always-pass test.',
        },
        rerun: { green: true, note: '23 / 23 passing against untouched VB.NET.' },
        tag: 'green',
      },
    ],
  },
  stubborn: {
    key: 'stubborn',
    label: 'Needs escalation',
    drift: {
      test: 'PlaceOrder_TotalExceedsIntMax_ReturnsClamped',
      call: 'sut.PlaceOrder(0, (decimal)int.MaxValue + 1)',
      message: 'Assert.AreEqual failed. Expected:<2147483647>. Actual: threw OverflowException.',
      count: '1 / 23',
    },
    outcome: 'succeeded',
    attempts: [
      {
        tier: 'Mechanical',
        role: 'mechanical',
        rationale:
          'No literal to swap — the code threw an error where the test expected a value. A simple edit can’t restructure the test. Escalating.',
        diff: [],
        gate: { ok: false, note: 'No simple number-swap works when the code throws.' },
        rerun: null,
        tag: 'escalated',
      },
      {
        tier: 'Reasoning',
        role: 'reasoning',
        rationale:
          'Rewriting the test to expect the error. An out-of-range decimal cast raises an overflow — now checked with ThrowsException.',
        diff: [
          { op: '-', text: 'int r = sut.PlaceOrder(0, (decimal)int.MaxValue + 1);' },
          { op: '-', text: 'Assert.AreEqual(int.MaxValue, r);' },
          { op: '+', text: 'Assert.ThrowsException<ArithmeticException>(() =>' },
          { op: '+', text: '    sut.PlaceOrder(0, (decimal)int.MaxValue + 1));' },
        ],
        gate: { ok: true, note: 'Checks the real error on the same input.' },
        rerun: {
          green: false,
          note: 'Still red — the real error is OverflowException; the test named its parent type, and MSTest needs the exact one.',
        },
        tag: 'red',
      },
      {
        tier: 'Escalation',
        role: 'escalation',
        rationale: 'Switching to the exact error type from the failure.',
        diff: [
          { op: '-', text: 'Assert.ThrowsException<ArithmeticException>(() =>' },
          { op: '+', text: 'Assert.ThrowsException<OverflowException>(() =>' },
        ],
        gate: { ok: true, note: 'Exact error type; input unchanged.' },
        rerun: { green: true, note: '23 / 23 passing.' },
        tag: 'green',
      },
    ],
  },
  unfixable: {
    key: 'unfixable',
    label: 'Unfixable',
    drift: {
      test: 'PlaceOrder_StampsSequenceNumber',
      call: 'sut.PlaceOrder(5, 10m)',
      message: 'Assert.AreEqual failed. Expected:<1001>. Actual:<1043>.',
      count: '1 / 23',
    },
    outcome: 'quarantined',
    attempts: [
      {
        tier: 'Mechanical',
        role: 'mechanical',
        rationale: 'Swapping the expected value to the observed 1043.',
        diff: [
          { op: '-', text: 'Assert.AreEqual(1001, sut.PlaceOrder(5, 10m));' },
          { op: '+', text: 'Assert.AreEqual(1043, sut.PlaceOrder(5, 10m));' },
        ],
        gate: { ok: true, note: 'A valid number swap.' },
        rerun: {
          green: false,
          note: 'Red again — actual is now 1071. The value changed between runs.',
        },
        tag: 'flag',
      },
      {
        tier: 'Reasoning',
        role: 'reasoning',
        rationale:
          'The value changes every run — checking why. PlaceOrder stamps a sequence number based on the current time, so the result doesn’t depend on its inputs.',
        diff: [],
        gate: { ok: false, note: 'No fixed value can match something the code won’t reproduce.' },
        rerun: { green: false, note: 'Red — got 1098. Confirmed: different every run.' },
        tag: 'flag',
      },
      {
        tier: 'Escalation',
        role: 'escalation',
        rationale:
          'Confirmed: the value is time-based and different every run. A reliable test can’t match something the code won’t reproduce — no valid fix exists.',
        diff: [],
        gate: { ok: false, note: 'Faking a pass here would make the tests lie. Not doing it.' },
        rerun: null,
        tag: 'nofix',
      },
    ],
  },
}

export const SCENARIO_ORDER: RepairScenarioKey[] = ['easy', 'stubborn', 'unfixable']

/** The passing tests the loop must leave untouched (23 total, 1 failing). */
export const REPAIR_PASS_COUNT = 22
export const REPAIR_TOTAL = 23

/** Result-chip label + status colour keyed by terminal tag (design handoff §6). */
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

/**
 * The baseline suite rendered in the code panel during the demo. Mirrors the OrderService
 * example from the handoff: 23 characterisation tests over PlaceOrder / CalculateTotal /
 * ApplyDiscount. When the simple-fix arc succeeds the failing test is swapped in place
 * (name + expected value + comment all corrected) so the panel shows the repaired suite.
 */
export function buildBaselineCode(className: string, repaired: boolean): string {
  const cls = className || 'OrderService'
  const method = (name: string, body: string) =>
    `    [TestMethod]\n    public void ${name}()\n    {\n        var sut = new ${cls}();\n${body}\n    }`
  const failing = repaired
    ? `    [TestMethod]\n    public void PlaceOrder_TotalWithFraction_RoundsToInt()\n    {\n        var sut = new ${cls}();\n        // CInt banker-rounds 9.9 -> 10, so 3 + 10\n        int result = sut.PlaceOrder(3, 9.9m);\n        Assert.AreEqual(13, result);\n    }`
    : `    [TestMethod]\n    public void PlaceOrder_TotalWithFraction_TruncatesToInt()\n    {\n        var sut = new ${cls}();\n        int result = sut.PlaceOrder(3, 9.9m);\n        Assert.AreEqual(12, result);\n    }`
  return (
    `[TestClass]\npublic class ${cls}BaselineTests\n{\n` +
    `    // Records exactly how ${cls}.dll runs today.\n    // GREEN = behaviour unchanged. It does NOT mean correct.\n\n` +
    method(
      'PlaceOrder_TypicalValues_ReturnsSumOfCustomerIdAndCastTotal',
      '        int result = sut.PlaceOrder(5, 10m);\n        Assert.AreEqual(15, result);',
    ) +
    `\n\n` +
    failing +
    `\n\n` +
    method(
      'PlaceOrder_TotalWithFractionRoundsViaBankersRounding',
      "        // CInt in VB uses banker's rounding: 2.5 rounds to 2\n        int result = sut.PlaceOrder(0, 2.5m);\n        Assert.AreEqual(2, result);",
    ) +
    `\n\n` +
    method(
      'PlaceOrder_ZeroCustomerIdZeroTotal_ReturnsZero',
      '        int result = sut.PlaceOrder(0, 0m);\n        Assert.AreEqual(0, result);',
    ) +
    `\n\n` +
    method(
      'PlaceOrder_NegativeTotal_ReturnsCorrectSum',
      '        int result = sut.PlaceOrder(10, -3m);\n        Assert.AreEqual(7, result);',
    ) +
    `\n\n` +
    method(
      'PlaceOrder_TotalExceedsIntMax_ThrowsOverflowException',
      '        Assert.ThrowsException<OverflowException>(() => sut.PlaceOrder(0, (decimal)int.MaxValue + 1m));',
    ) +
    `\n\n` +
    method(
      'CalculateTotal_TypicalValues_ReturnsProduct',
      '        decimal result = sut.CalculateTotal(3, 9.99m);\n        Assert.AreEqual(29.97m, result);',
    ) +
    `\n\n` +
    method(
      'ApplyDiscount_KnownCodeSAVE10_AppliesTenPercentDiscount',
      '        decimal result = sut.ApplyDiscount(100m, "SAVE10");\n        Assert.AreEqual(90m, result);',
    ) +
    `\n\n` +
    method(
      'ApplyDiscount_UnknownCode_ReturnsSubtotalUnchanged',
      '        decimal result = sut.ApplyDiscount(100m, "BOGUS");\n        Assert.AreEqual(100m, result);',
    ) +
    `\n}`
  )
}
