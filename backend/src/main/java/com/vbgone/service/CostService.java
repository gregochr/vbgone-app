package com.vbgone.service;

import com.vbgone.model.CostResult;
import com.vbgone.model.MigrationSession;
import com.vbgone.model.TokenUsage;
import com.vbgone.session.SessionStore;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CostService {

    /**
     * Per-canonical-model-id pricing in USD per million tokens: {input, output}.
     * Covers both providers. Unknown ids fall back to the legacy contains()
     * heuristic below.
     */
    static final Map<String, double[]> PRICING = Map.ofEntries(
            // ── Anthropic ──
            Map.entry("claude-opus-4-6", new double[]{15.0, 75.0}),
            Map.entry("claude-sonnet-4-6", new double[]{3.0, 15.0}),
            Map.entry("claude-haiku-4-5", new double[]{0.80, 4.0}),
            // ── GitHub Models (Copilot) — publisher-namespaced catalog ids ──
            // Only the models reachable by personal accounts on the free tier.
            // PLACEHOLDER GitHub Models pricing — verify against tenant.
            Map.entry("openai/gpt-4.1", new double[]{2.0, 8.0}),
            Map.entry("openai/gpt-4o", new double[]{2.5, 10.0}),
            Map.entry("openai/gpt-4.1-mini", new double[]{0.40, 1.60}),
            Map.entry("openai/gpt-4o-mini", new double[]{0.15, 0.60})
    );

    private final SessionStore sessionStore;

    public CostService(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    public CostResult getCost(String sessionId) {
        MigrationSession session = sessionStore.get(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        double totalCost = session.getTokenUsages().stream()
                .mapToDouble(TokenUsage::cost)
                .sum();

        return new CostResult(sessionId, session.getTokenUsages(), totalCost);
    }

    /**
     * Calculate cost in USD based on model and token counts.
     * Prices per million tokens (as of 2025):
     *   Opus 4.6:   $15 input, $75 output
     *   Sonnet 4.6: $3 input, $15 output
     *   Haiku 4.5:  $0.80 input, $4 output
     */
    public static double calculateCost(String modelId, long inputTokens, long outputTokens) {
        double inputPricePerMillion;
        double outputPricePerMillion;

        double[] explicit = modelId == null ? null : PRICING.get(modelId);
        if (explicit != null) {
            inputPricePerMillion = explicit[0];
            outputPricePerMillion = explicit[1];
        } else if (modelId != null && modelId.contains("haiku")) {
            inputPricePerMillion = 0.80;
            outputPricePerMillion = 4.0;
        } else if (modelId != null && modelId.contains("opus")) {
            inputPricePerMillion = 15.0;
            outputPricePerMillion = 75.0;
        } else {
            // Sonnet pricing
            inputPricePerMillion = 3.0;
            outputPricePerMillion = 15.0;
        }

        return (inputTokens * inputPricePerMillion + outputTokens * outputPricePerMillion) / 1_000_000.0;
    }
}
