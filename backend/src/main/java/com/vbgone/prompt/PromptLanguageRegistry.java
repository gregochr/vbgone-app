package com.vbgone.prompt;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves a {@link PromptLanguage} by target-language id, mirroring
 * {@code LanguageConventionsRegistry}. Spring injects all implementations.
 */
@Component
public class PromptLanguageRegistry {

    private static final String DEFAULT_LANGUAGE = "csharp";

    private final Map<String, PromptLanguage> byId = new HashMap<>();

    public PromptLanguageRegistry(List<PromptLanguage> prompts) {
        for (PromptLanguage p : prompts) {
            byId.put(p.id(), p);
        }
    }

    /** Resolve for a request's targetLanguage; null/blank falls back to C#. */
    public PromptLanguage forLanguage(String targetLanguage) {
        String key = (targetLanguage == null || targetLanguage.isBlank())
                ? DEFAULT_LANGUAGE
                : targetLanguage.toLowerCase();
        PromptLanguage p = byId.get(key);
        if (p == null) {
            throw new IllegalArgumentException("No prompt language for: " + targetLanguage);
        }
        return p;
    }
}
