package com.vbgone.ai;

import com.vbgone.ai.anthropic.AnthropicProvider;
import com.vbgone.ai.github.GitHubModelsProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vbgone.service.ClaudeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class AiProviderRegistryTest {

    @Mock
    private ClaudeClient claudeClient;

    private AiProviderRegistry registry;

    @BeforeEach
    void setUp() {
        AnthropicProvider anthropic = new AnthropicProvider(claudeClient);
        GitHubModelsProvider copilot = new GitHubModelsProvider("", new ObjectMapper());
        registry = new AiProviderRegistry(List.of(anthropic, copilot));
    }

    // ── default resolution per provider/role ──

    @Test
    void modelFor_anthropicDefaults() {
        assertThat(registry.modelFor("anthropic", ModelRole.REASONING, Map.of())).isEqualTo("claude-sonnet-4-6");
        assertThat(registry.modelFor("anthropic", ModelRole.MECHANICAL, Map.of())).isEqualTo("claude-haiku-4-5");
        assertThat(registry.modelFor("anthropic", ModelRole.IMPLEMENTATION, Map.of())).isEqualTo("claude-sonnet-4-6");
        assertThat(registry.modelFor("anthropic", ModelRole.ESCALATION, Map.of())).isEqualTo("claude-opus-4-6");
    }

    @Test
    void modelFor_copilotDefaults() {
        assertThat(registry.modelFor("copilot", ModelRole.REASONING, Map.of())).isEqualTo("openai/o3");
        assertThat(registry.modelFor("copilot", ModelRole.MECHANICAL, Map.of())).isEqualTo("openai/gpt-4.1");
        assertThat(registry.modelFor("copilot", ModelRole.IMPLEMENTATION, Map.of())).isEqualTo("openai/o3");
        assertThat(registry.modelFor("copilot", ModelRole.ESCALATION, Map.of())).isEqualTo("openai/gpt-5");
    }

    @Test
    void modelFor_nullProviderDefaultsToAnthropic() {
        assertThat(registry.modelFor(null, ModelRole.REASONING, Map.of())).isEqualTo("claude-sonnet-4-6");
        assertThat(registry.modelFor("", ModelRole.MECHANICAL, Map.of())).isEqualTo("claude-haiku-4-5");
    }

    // ── overrides ──

    @Test
    void modelFor_overrideWinsOverDefault() {
        Map<String, String> overrides = Map.of("reasoning", "openai/gpt-4o", "mechanical", "openai/o4-mini");
        assertThat(registry.modelFor("copilot", ModelRole.REASONING, overrides)).isEqualTo("openai/gpt-4o");
        assertThat(registry.modelFor("copilot", ModelRole.MECHANICAL, overrides)).isEqualTo("openai/o4-mini");
        // Role with no override falls back to default
        assertThat(registry.modelFor("copilot", ModelRole.ESCALATION, overrides)).isEqualTo("openai/gpt-5");
    }

    @Test
    void modelFor_blankOverrideIgnored() {
        Map<String, String> overrides = Map.of("reasoning", "  ");
        assertThat(registry.modelFor("anthropic", ModelRole.REASONING, overrides)).isEqualTo("claude-sonnet-4-6");
    }

    @Test
    void modelFor_nullOverridesUsesDefaults() {
        assertThat(registry.modelFor("anthropic", ModelRole.REASONING, null)).isEqualTo("claude-sonnet-4-6");
    }

    // ── provider switch ──

    @Test
    void provider_returnsCorrectBean() {
        assertThat(registry.provider("anthropic").id()).isEqualTo("anthropic");
        assertThat(registry.provider("copilot").id()).isEqualTo("copilot");
    }

    @Test
    void provider_nullDefaultsToAnthropic() {
        assertThat(registry.provider(null).id()).isEqualTo("anthropic");
    }

    @Test
    void provider_unknownThrows() {
        assertThatThrownBy(() -> registry.provider("openai"))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("Unknown AI provider");
    }

    @Test
    void modelFor_unknownProviderThrows() {
        assertThatThrownBy(() -> registry.modelFor("openai", ModelRole.REASONING, Map.of()))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("Unknown AI provider");
    }

    // ── validModelIds ──

    @Test
    void validModelIds_listsModelsPerProvider() {
        assertThat(registry.validModelIds("anthropic"))
                .containsExactlyInAnyOrder("claude-opus-4-6", "claude-sonnet-4-6", "claude-haiku-4-5");
        assertThat(registry.validModelIds("copilot"))
                .containsExactlyInAnyOrder(
                        "openai/gpt-5", "openai/o3", "openai/gpt-4.1", "openai/gpt-4o", "openai/o4-mini", "openai/gpt-5-mini");
    }
}
