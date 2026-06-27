package com.vbgone.ai.anthropic;

import com.anthropic.models.messages.Model;
import com.vbgone.ai.AiProvider;
import com.vbgone.ai.AiResponse;
import com.vbgone.service.ClaudeClient;
import org.springframework.stereotype.Component;

/**
 * Anthropic provider. Delegates to {@link ClaudeClient} to preserve prompt
 * caching and token accounting. Maps canonical model ids to SDK Model enums.
 */
@Component
public class AnthropicProvider implements AiProvider {

    public static final String ID = "anthropic";

    private final ClaudeClient claudeClient;

    public AnthropicProvider(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public AiResponse generate(String modelId, String systemPrompt, String userMessage, long maxTokens) {
        Model model = toSdkModel(modelId);
        ClaudeClient.ClaudeResponse response =
                claudeClient.sendWithCachedSystemPrompt(systemPrompt, userMessage, model, maxTokens);
        return new AiResponse(
                response.text(),
                response.inputTokens(),
                response.outputTokens(),
                modelId,
                ID);
    }

    /** Maps a canonical Anthropic model id to the SDK enum. */
    static Model toSdkModel(String modelId) {
        return switch (modelId) {
            case "claude-opus-4-6" -> Model.CLAUDE_OPUS_4_6;
            case "claude-sonnet-4-6" -> Model.CLAUDE_SONNET_4_6;
            case "claude-haiku-4-5" -> Model.CLAUDE_HAIKU_4_5;
            default -> throw new IllegalArgumentException("Unknown Anthropic model id: " + modelId);
        };
    }
}
