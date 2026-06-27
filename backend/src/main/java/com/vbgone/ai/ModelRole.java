package com.vbgone.ai;

/**
 * The role a model plays for a given wizard step. Each provider maps a role
 * to a concrete model id (e.g. anthropic REASONING -> claude-sonnet-4-6).
 */
public enum ModelRole {
    REASONING,
    MECHANICAL,
    IMPLEMENTATION,
    ESCALATION
}
