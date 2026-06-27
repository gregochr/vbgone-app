package com.vbgone.ai.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vbgone.ai.AiProvider;
import com.vbgone.ai.AiResponse;
import com.vbgone.ai.ProviderUnavailableException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * GitHub Models provider (exposed to the frontend as "copilot"). Calls the
 * OpenAI-compatible chat/completions endpoint with a bearer token from the
 * {@code GITHUB_MODELS_TOKEN} environment variable.
 *
 * <p>The bean always constructs cleanly — a missing token does NOT crash app
 * startup; only {@link #generate} fails with {@link ProviderUnavailableException}.
 */
@Component
public class GitHubModelsProvider implements AiProvider {

    public static final String ID = "copilot";

    private static final MediaType JSON = MediaType.get("application/json");

    private final String token;
    private final String baseUrl;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GitHubModelsProvider(
            @Value("${GITHUB_MODELS_TOKEN:}") String token,
            ObjectMapper objectMapper) {
        this(token, "https://models.github.ai/inference", new OkHttpClient(), objectMapper);
    }

    /** Constructor for tests — allows overriding the base URL and HTTP client. */
    GitHubModelsProvider(String token, String baseUrl, OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.token = token;
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public AiResponse generate(String modelId, String systemPrompt, String userMessage, long maxTokens) {
        if (token == null || token.isBlank()) {
            throw new ProviderUnavailableException(
                    "GitHub Models provider is not configured. Set GITHUB_MODELS_TOKEN to use Copilot.");
        }

        String requestJson = buildRequestBody(modelId, systemPrompt, userMessage, maxTokens);

        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Accept", "application/json")
                .post(RequestBody.create(requestJson, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            String responseText = body != null ? body.string() : "";
            if (!response.isSuccessful()) {
                throw new ProviderUnavailableException(
                        "GitHub Models request failed (HTTP " + response.code() + "): " + summarise(responseText));
            }
            return parseResponse(modelId, responseText);
        } catch (IOException e) {
            throw new ProviderUnavailableException(
                    "GitHub Models request failed: " + e.getMessage(), e);
        }
    }

    private String buildRequestBody(String modelId, String systemPrompt, String userMessage, long maxTokens) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", modelId);
        ArrayNode messages = root.putArray("messages");
        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", systemPrompt);
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", userMessage);
        root.put("max_tokens", maxTokens);
        return root.toString();
    }

    private AiResponse parseResponse(String modelId, String responseText) {
        try {
            JsonNode root = objectMapper.readTree(responseText);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new ProviderUnavailableException(
                        "GitHub Models response contained no choices: " + summarise(responseText));
            }
            String text = choices.get(0).path("message").path("content").asText("");
            JsonNode usage = root.path("usage");
            long inputTokens = usage.path("prompt_tokens").asLong(0);
            long outputTokens = usage.path("completion_tokens").asLong(0);
            return new AiResponse(text, inputTokens, outputTokens, modelId, ID);
        } catch (IOException e) {
            throw new ProviderUnavailableException(
                    "Failed to parse GitHub Models response: " + e.getMessage(), e);
        }
    }

    private static String summarise(String body) {
        if (body == null) return "";
        String trimmed = body.strip();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) + "..." : trimmed;
    }
}
