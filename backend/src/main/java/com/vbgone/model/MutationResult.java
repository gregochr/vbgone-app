package com.vbgone.model;

import java.util.List;

/**
 * Outcome of a mutation-testing run over an Assure net.
 *
 * @param total    mutants run (killed + survived + skipped)
 * @param killed   mutants the net caught (a test went red) — good
 * @param survived mutants the net missed (stayed green) — blind spots
 * @param skipped  mutants that didn't compile (not a behaviour change, excluded from the score)
 * @param score    mutation score as a percentage 0–100 = killed / (killed + survived), or null when
 *                 there were no compilable behaviour-changing mutants to judge
 * @param survivors the surviving mutants — the actionable list of gaps in the net
 */
public record MutationResult(
        int total,
        int killed,
        int survived,
        int skipped,
        Integer score,
        List<SurvivingMutant> survivors
) {

    public static MutationResult of(int killed, int survived, int skipped, List<SurvivingMutant> survivors) {
        int judged = killed + survived;
        Integer score = judged == 0 ? null : (int) Math.round(100.0 * killed / judged);
        return new MutationResult(killed + survived + skipped, killed, survived, skipped, score, survivors);
    }
}
