package com.vbgone.model;

import java.util.List;

/**
 * Per-method observed behaviour, captured by Assure-mode analysis. Describes what a
 * method does today — faults included — without prescribing any fix.
 *
 * @param method the method name
 * @param cls    the declaring class
 * @param rows   one row per edge case (condition → outcome)
 */
public record ObservedBehaviour(
        String method,
        String cls,
        List<ObservedRow> rows
) {}
