package com.vbgone.model;

import com.vbgone.mutation.Mutant;

/**
 * A mutant the net failed to kill — the actionable output of mutation testing. Carries the display
 * fields only (not the mutated source), each one a concrete behaviour the characterisation net does
 * not pin.
 */
public record SurvivingMutant(int line, String operator, String before, String after, String description) {

    public static SurvivingMutant from(Mutant m) {
        return new SurvivingMutant(m.line(), m.operator(), m.before(), m.after(), m.description());
    }
}
