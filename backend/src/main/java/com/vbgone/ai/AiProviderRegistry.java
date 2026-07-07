package com.vbgone.ai;

import com.vbgone.ai.anthropic.AnthropicProvider;
import com.vbgone.ai.github.GitHubModelsProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for provider/model/default configuration.
 *
 * <p>Resolving a model is: {@code modelFor(provider, role, overrides)} =
 * {@code overrides[role] ?? defaults[provider][role]}. All provider, model and
 * default wiring lives here so adding a model or changing a default is a
 * one-line change.
 */
@Component
public class AiProviderRegistry {

    public static final String DEFAULT_PROVIDER = AnthropicProvider.ID;

    // ── Canonical model ids per provider ──
    private static final Set<String> ANTHROPIC_MODELS = Set.of(
            "claude-opus-4-6", "claude-sonnet-4-6", "claude-haiku-4-5");

    // GitHub Models inference catalog ids are publisher-namespaced (models.github.ai).
    // Anthropic/Gemini are NOT exposed on this endpoint — OpenAI models only.
    private static final Set<String> COPILOT_MODELS = Set.of(
            "openai/gpt-5", "openai/o3", "openai/gpt-4.1", "openai/gpt-4o", "openai/o4-mini", "openai/gpt-5-mini");

    // ── Provider x Role -> default model id ──
    private static final Map<String, Map<ModelRole, String>> DEFAULTS = Map.of(
            AnthropicProvider.ID, Map.of(
                    ModelRole.REASONING, "claude-sonnet-4-6",
                    ModelRole.MECHANICAL, "claude-haiku-4-5",
                    ModelRole.IMPLEMENTATION, "claude-sonnet-4-6",
                    ModelRole.ESCALATION, "claude-opus-4-6"),
            GitHubModelsProvider.ID, Map.of(
                    ModelRole.REASONING, "openai/o3",
                    ModelRole.MECHANICAL, "openai/gpt-4.1",
                    ModelRole.IMPLEMENTATION, "openai/o3",
                    ModelRole.ESCALATION, "openai/gpt-5"));

    private final Map<String, AiProvider> providers = new HashMap<>();

    public AiProviderRegistry(List<AiProvider> providerBeans) {
        for (AiProvider p : providerBeans) {
            providers.put(p.id(), p);
        }
    }

    /**
     * Resolve a concrete model id for the given provider and role, honouring
     * any per-role override.
     */
    public String modelFor(String provider, ModelRole role, Map<String, String> overrides) {
        String providerId = normaliseProvider(provider);
        String roleKey = roleKey(role);
        if (overrides != null) {
            String override = overrides.get(roleKey);
            if (override != null && !override.isBlank()) {
                return override;
            }
        }
        Map<ModelRole, String> defaults = DEFAULTS.get(providerId);
        if (defaults == null) {
            throw new ProviderUnavailableException("Unknown AI provider: " + provider);
        }
        return defaults.get(role);
    }

    /** Returns the provider bean for the given id, or throws if unknown. */
    public AiProvider provider(String providerId) {
        String id = normaliseProvider(providerId);
        AiProvider p = providers.get(id);
        if (p == null) {
            throw new ProviderUnavailableException("Unknown AI provider: " + providerId);
        }
        return p;
    }

    /** Valid canonical model ids for a provider (useful for validation). */
    public Set<String> validModelIds(String provider) {
        String id = normaliseProvider(provider);
        return switch (id) {
            case AnthropicProvider.ID -> new HashSet<>(ANTHROPIC_MODELS);
            case GitHubModelsProvider.ID -> new HashSet<>(COPILOT_MODELS);
            default -> throw new ProviderUnavailableException("Unknown AI provider: " + provider);
        };
    }

    private static String normaliseProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return DEFAULT_PROVIDER;
        }
        return provider;
    }

    /** The override-map key for a role: lowercase enum name. */
    private static String roleKey(ModelRole role) {
        return switch (role) {
            case REASONING -> "reasoning";
            case MECHANICAL -> "mechanical";
            case IMPLEMENTATION -> "implementation";
            case ESCALATION -> "escalation";
        };
    }
}
