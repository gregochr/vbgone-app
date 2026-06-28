package com.vbgone.build;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves a {@link BuildRuntime} by target-language id, mirroring
 * {@code AiProviderRegistry}. Spring injects all implementations.
 */
@Component
public class BuildRuntimeRegistry {

    private static final String DEFAULT_LANGUAGE = "csharp";

    private final Map<String, BuildRuntime> byId = new HashMap<>();

    public BuildRuntimeRegistry(List<BuildRuntime> runtimes) {
        for (BuildRuntime rt : runtimes) {
            byId.put(rt.id(), rt);
        }
    }

    /** Resolve for a session's targetLanguage; null/blank falls back to C#. */
    public BuildRuntime forLanguage(String targetLanguage) {
        String key = (targetLanguage == null || targetLanguage.isBlank())
                ? DEFAULT_LANGUAGE
                : targetLanguage.toLowerCase();
        BuildRuntime rt = byId.get(key);
        if (rt == null) {
            throw new IllegalArgumentException("No build runtime for: " + targetLanguage);
        }
        return rt;
    }
}
