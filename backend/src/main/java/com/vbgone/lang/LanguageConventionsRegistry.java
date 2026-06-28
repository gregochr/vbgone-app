package com.vbgone.lang;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * Resolves {@link LanguageConventions} by target-language id, mirroring
 * {@code AiProviderRegistry}. Spring injects all implementations.
 */
@Component
public class LanguageConventionsRegistry {

    private static final String DEFAULT_LANGUAGE = "csharp";

    private final Map<String, LanguageConventions> byId = new HashMap<>();

    public LanguageConventionsRegistry(List<LanguageConventions> conventions) {
        for (LanguageConventions c : conventions) {
            byId.put(c.id(), c);
        }
    }

    /** Resolve for a request's targetLanguage; null/blank falls back to C#. */
    public LanguageConventions forLanguage(String targetLanguage) {
        String key = (targetLanguage == null || targetLanguage.isBlank())
                ? DEFAULT_LANGUAGE
                : targetLanguage.toLowerCase();
        LanguageConventions c = byId.get(key);
        if (c == null) {
            throw new IllegalArgumentException("No language conventions for: " + targetLanguage);
        }
        return c;
    }
}
