package com.vbgone.model;

import java.util.List;

/**
 * The result of a single auto-repair attempt (one tier). Mirrors the design's attempt card:
 * a rationale, the diff applied, a validity-gate verdict, the re-run outcome, and a terminal
 * {@code tag}. Also carries the updated suite {@code code} (so the next tier builds on it) and
 * {@code netFaithful} so the frontend knows whether the loop can stop.
 *
 * <p>The validity gate is what stops the loop cheating: a green re-run is not enough — the
 * rewrite must still call the same method and make a real assertion, never a meaningless
 * always-pass test. A rewrite that fails the gate is rejected, not counted as a win.
 *
 * <p>{@code tag} values: {@code green} (fixed), {@code red} (still failing → escalate),
 * {@code escalated} (no valid edit at this tier → escalate), {@code flag} (the value changed
 * between runs — nondeterministic), {@code nofix} (no valid fix exists → quarantine).
 */
public record RepairAttempt(
        String tier,
        String role,
        String model,
        String rationale,
        List<DiffLine> diff,
        Gate gate,
        Rerun rerun,
        String tag,
        String code,
        boolean netFaithful
) {
    /** One diff line: {@code op} is "+", "-" or " " (context). */
    public record DiffLine(String op, String text) {}

    /** Validity-gate verdict. {@code ok=false} means the rewrite was rejected or none was possible. */
    public record Gate(boolean ok, String note) {}

    /** Re-run outcome, or null when the attempt produced no edit to re-run. */
    public record Rerun(boolean green, String note) {}
}
