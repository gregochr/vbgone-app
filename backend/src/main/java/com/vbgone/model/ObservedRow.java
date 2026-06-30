package com.vbgone.model;

/**
 * One edge-case row in Protect's Observed Behaviour block: a condition, the outcome the
 * legacy code produces today, and the kind of outcome (colours it in the UI).
 *
 * @param cond    the input condition, e.g. "headcount = 0"
 * @param outcome what the code does today, e.g. "throws DivideByZeroException"
 * @param kind    "throws" (exception), "fault" (silent wrong result), or "returns" (benign)
 */
public record ObservedRow(
        String cond,
        String outcome,
        String kind
) {}
