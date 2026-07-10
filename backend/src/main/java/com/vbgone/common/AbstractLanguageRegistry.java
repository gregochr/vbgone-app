package com.vbgone.common;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves a language-keyed component ({@link Keyed}) by target-language id, sharing the null/blank
 * → C# fallback and case-insensitive lookup that the per-domain registries (build runtime, prompt
 * strategy, language conventions) all previously duplicated. Subclasses are thin Spring
 * {@code @Component}s that hand up their injected implementations plus a noun for the not-found
 * message; the public {@link #forLanguage(String)} contract is unchanged, so callers are untouched.
 */
public abstract class AbstractLanguageRegistry<T extends Keyed> {

    /** Target language assumed when a request omits one. */
    protected static final String DEFAULT_LANGUAGE = "csharp";

    private final Map<String, T> byId = new HashMap<>();
    private final String elementNoun;

    protected AbstractLanguageRegistry(List<T> items, String elementNoun) {
        this.elementNoun = elementNoun;
        for (T item : items) {
            byId.put(item.id(), item);
        }
    }

    /** Resolve for a request's {@code targetLanguage}; null/blank falls back to C#. */
    public T forLanguage(String targetLanguage) {
        String key = (targetLanguage == null || targetLanguage.isBlank())
                ? DEFAULT_LANGUAGE
                : targetLanguage.toLowerCase();
        T item = byId.get(key);
        if (item == null) {
            throw new IllegalArgumentException("No " + elementNoun + " for: " + targetLanguage);
        }
        return item;
    }
}
