package com.vbgone.ai;

/**
 * Thin chat abstraction over an AI provider. Intentionally minimal: the prompt
 * templates live in the services so both providers reuse identical prompts —
 * the single shared asset of the migration pipeline.
 */
public interface AiProvider {

    /** Stable provider identifier — "anthropic" or "copilot". */
    String id();

    /**
     * Generate a single completion.
     *
     * @param modelId      canonical model id (provider-specific)
     * @param systemPrompt system prompt (cached where the provider supports it)
     * @param userMessage  user message
     * @param maxTokens    maximum output tokens
     */
    AiResponse generate(String modelId, String systemPrompt, String userMessage, long maxTokens);
}
