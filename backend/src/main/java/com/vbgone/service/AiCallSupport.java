package com.vbgone.service;

import com.vbgone.ai.AiProvider;
import com.vbgone.ai.AiProviderRegistry;
import com.vbgone.ai.AiRequestOptions;
import com.vbgone.ai.AiResponse;
import com.vbgone.ai.ModelRole;
import com.vbgone.model.MigrationSession;
import com.vbgone.model.TokenUsage;
import org.springframework.stereotype.Component;

/**
 * Shared AI call + token accounting for the generation services.
 *
 * <p>Resolves the concrete model + provider for a {@link ModelRole}, calls the provider,
 * records the resulting {@link TokenUsage} (with cost) on the session, and returns the raw
 * response. Extracted from what were byte-for-byte identical private {@code call(...)} methods
 * in {@code GenerationService}, {@code AssureService} and {@code AnalysisService}, so the
 * cost/token-accounting logic now lives in exactly one place.
 */
@Component
public class AiCallSupport {

    private final AiProviderRegistry registry;

    public AiCallSupport(AiProviderRegistry registry) {
        this.registry = registry;
    }

    public AiResponse call(AiRequestOptions options, ModelRole role, String systemPrompt,
                           String userMessage, long maxTokens, String step, MigrationSession session) {
        String modelId = registry.modelFor(options.provider(), role, options.modelOverrides());
        AiProvider aiProvider = registry.provider(options.provider());
        AiResponse response = aiProvider.generate(modelId, systemPrompt, userMessage, maxTokens);
        double cost = CostService.calculateCost(modelId, response.inputTokens(), response.outputTokens());
        session.addTokenUsage(new TokenUsage(step, modelId, response.inputTokens(),
                response.outputTokens(), cost, response.provider()));
        return response;
    }
}
