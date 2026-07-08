package com.vbgone.mutation;

/**
 * One first-order mutation of a VB source: a single token swapped at a single site.
 *
 * @param line         1-based line the mutation lands on (for display)
 * @param operator     short operator id, e.g. "relational-boundary" or "boolean-connective"
 * @param before       the original token, e.g. "&lt;="
 * @param after        the replacement token, e.g. "&lt;"
 * @param description  human-readable one-liner for the UI
 * @param mutatedSource the full VB source with exactly this one token replaced — what gets compiled
 */
public record Mutant(
        int line,
        String operator,
        String before,
        String after,
        String description,
        String mutatedSource
) {}
