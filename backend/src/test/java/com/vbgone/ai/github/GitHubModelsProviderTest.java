package com.vbgone.ai.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vbgone.ai.AiResponse;
import com.vbgone.ai.ProviderUnavailableException;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubModelsProviderTest {

    private MockWebServer server;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private GitHubModelsProvider provider(String token) {
        String baseUrl = server.url("/inference").toString();
        // strip trailing slash so the provider appends /chat/completions cleanly
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        return new GitHubModelsProvider(token, baseUrl, new OkHttpClient(), objectMapper);
    }

    @Test
    void id_isCopilot() {
        assertThat(provider("tok").id()).isEqualTo("copilot");
    }

    @Test
    void generate_noToken_throwsProviderUnavailable() {
        GitHubModelsProvider p = new GitHubModelsProvider("", objectMapper);
        assertThatThrownBy(() -> p.generate("openai/gpt-5", "sys", "user", 100))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("GITHUB_MODELS_TOKEN");
    }

    @Test
    void generate_nullToken_throwsProviderUnavailable() {
        GitHubModelsProvider p = new GitHubModelsProvider(null, objectMapper);
        assertThatThrownBy(() -> p.generate("openai/gpt-5", "sys", "user", 100))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void generate_happyPath_parsesContentAndUsage() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "choices": [
                            { "message": { "role": "assistant", "content": "public class Foo {}" } }
                          ],
                          "usage": { "prompt_tokens": 123, "completion_tokens": 45 }
                        }"""));

        AiResponse response = provider("tok").generate("openai/o3", "system prompt", "user message", 4096);

        assertThat(response.text()).isEqualTo("public class Foo {}");
        assertThat(response.inputTokens()).isEqualTo(123);
        assertThat(response.outputTokens()).isEqualTo(45);
        assertThat(response.modelId()).isEqualTo("openai/o3");
        assertThat(response.provider()).isEqualTo("copilot");

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getPath()).endsWith("/chat/completions");
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer tok");

        JsonNode body = objectMapper.readTree(recorded.getBody().readUtf8());
        assertThat(body.get("model").asText()).isEqualTo("openai/o3");
        assertThat(body.get("max_tokens").asLong()).isEqualTo(4096);
        assertThat(body.get("messages").get(0).get("role").asText()).isEqualTo("system");
        assertThat(body.get("messages").get(0).get("content").asText()).isEqualTo("system prompt");
        assertThat(body.get("messages").get(1).get("role").asText()).isEqualTo("user");
        assertThat(body.get("messages").get(1).get("content").asText()).isEqualTo("user message");
    }

    @Test
    void generate_httpError_throwsProviderUnavailable() {
        server.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\":\"bad credentials\"}"));

        assertThatThrownBy(() -> provider("tok").generate("openai/o3", "s", "u", 100))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("HTTP 401");
    }
}
