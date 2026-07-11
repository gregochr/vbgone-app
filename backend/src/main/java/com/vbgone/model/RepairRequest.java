package com.vbgone.model;

import com.vbgone.ai.AiRequestOptions;

import java.util.Map;

/**
 * One attempt of the Assure baseline auto-repair loop. When the generated MSTest suite fails
 * against the untouched original VB.NET, a red test can only mean the test is wrong (not the
 * code) — so this asks the model at the given {@code tier} to rewrite just the failing test to
 * match the real observed behaviour, then re-runs it.
 *
 * <p>The frontend orchestrates the escalation: it calls this once per tier (1 → mechanical,
 * 2 → reasoning, 3 → escalation), feeding the previous attempt's {@code code} forward, and
 * stops when an attempt comes back green (or quarantines after three).
 *
 * @param code        the current suite (carries the previous attempt's rewrite on tiers 2–3)
 * @param failingTest the test method to repair
 * @param tier        1-based escalation tier
 */
public record RepairRequest(
        String sessionId,
        String className,
        String provider,
        String targetLanguage,
        Map<String, String> modelOverrides,
        String code,
        String failingTest,
        int tier
) {
    /** Bundles the flat AI-provider fields into the value object the services consume. */
    public AiRequestOptions aiOptions() {
        return AiRequestOptions.of(provider, targetLanguage, modelOverrides);
    }
}
