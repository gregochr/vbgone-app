package com.vbgone.service;

import com.vbgone.ai.ModelRole;

/**
 * The escalating auto-repair tiers. Each attempt maps to a model {@link ModelRole}, the display
 * name shown on the attempt card, and the guidance appended to the repair prompt. This replaces
 * three parallel {@code switch(int tier)} blocks plus the {@code tier >= 3} "is this the last
 * attempt" magic number that were scattered through {@link AssureService#repairAttempt}.
 */
enum RepairTier {

    MECHANICAL(ModelRole.MECHANICAL, "Mechanical",
            "Cheap mechanical tier: only a deterministic single-value swap is allowed — "
                    + "change the expected literal to the observed value and fix a now-misleading "
                    + "name/comment. If the code THREW where a value was expected, there is nothing "
                    + "to swap: set noEdit true so a stronger model can restructure the test."),

    REASONING(ModelRole.REASONING, "Reasoning",
            "Restructure the assertion using the method source and the failure output "
                    + "(for example, a value assertion becomes Assert.ThrowsException<T> for the "
                    + "exact exception type actually thrown)."),

    ESCALATION(ModelRole.ESCALATION, "Escalation",
            "Final attempt with full context. Tighten to the EXACT runtime exception type "
                    + "or value from the output. If the value is different on every run, no fixed "
                    + "test can match it — set noEdit true; never fake a pass.");

    private final ModelRole role;
    private final String displayName;
    private final String guidance;

    RepairTier(ModelRole role, String displayName, String guidance) {
        this.role = role;
        this.displayName = displayName;
        this.guidance = guidance;
    }

    ModelRole role() {
        return role;
    }

    String displayName() {
        return displayName;
    }

    String guidance() {
        return guidance;
    }

    /** The last tier — no stronger model to escalate to, so a non-fix here is terminal ({@code nofix}). */
    boolean isFinal() {
        return this == ESCALATION;
    }

    /** Map a 1-based attempt number to its tier: 1 → mechanical, 2 → reasoning, 3+ → escalation. */
    static RepairTier of(int tier) {
        if (tier <= 1) return MECHANICAL;
        if (tier >= 3) return ESCALATION;
        return REASONING;
    }
}
