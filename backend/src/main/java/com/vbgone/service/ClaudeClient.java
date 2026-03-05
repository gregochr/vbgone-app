package com.vbgone.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClaudeClient {

    private final AnthropicClient client;

    public ClaudeClient(AnthropicClient client) {
        this.client = client;
    }

    public String sendWithCachedSystemPrompt(String systemPrompt, String userMessage, Model model, long maxTokens) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .systemOfTextBlockParams(List.of(
                        TextBlockParam.builder()
                                .text(systemPrompt)
                                .cacheControl(CacheControlEphemeral.builder().build())
                                .build()))
                .addUserMessage(userMessage)
                .build();

        Message response = client.messages().create(params);

        return response.content().stream()
                .filter(ContentBlock::isText)
                .map(ContentBlock::asText)
                .map(TextBlock::text)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No text response from Claude"));
    }
}
