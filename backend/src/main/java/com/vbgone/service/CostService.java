package com.vbgone.service;

import com.anthropic.models.messages.Model;
import com.vbgone.model.CostResult;
import com.vbgone.model.MigrationSession;
import com.vbgone.model.TokenUsage;
import com.vbgone.session.SessionStore;
import org.springframework.stereotype.Service;

@Service
public class CostService {

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
     *   Sonnet 4.6: $3 input, $15 output
     *   Haiku 4.5:  $0.80 input, $4 output
     */
    public static double calculateCost(String modelId, long inputTokens, long outputTokens) {
        double inputPricePerMillion;
        double outputPricePerMillion;

        if (modelId.contains("haiku")) {
            inputPricePerMillion = 0.80;
            outputPricePerMillion = 4.0;
        } else {
            // Sonnet pricing
            inputPricePerMillion = 3.0;
            outputPricePerMillion = 15.0;
        }

        return (inputTokens * inputPricePerMillion + outputTokens * outputPricePerMillion) / 1_000_000.0;
    }
}
