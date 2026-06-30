package com.vbgone.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClassInfo(
        String name,
        List<String> methods,
        List<String> dependencies,
        Complexity complexity,
        CodeQuality codeQuality,
        List<String> codeSmells,
        List<String> refactoringSuggestions,
        List<String> vbAntiPatterns,
        /** Protect mode only — what each method does today, faults included. Null in Migrate. */
        List<ObservedBehaviour> observedBehaviour
) {
    /** Back-compat constructor for Migrate-mode analysis (no observed behaviour). */
    public ClassInfo(
            String name,
            List<String> methods,
            List<String> dependencies,
            Complexity complexity,
            CodeQuality codeQuality,
            List<String> codeSmells,
            List<String> refactoringSuggestions,
            List<String> vbAntiPatterns
    ) {
        this(name, methods, dependencies, complexity, codeQuality, codeSmells,
                refactoringSuggestions, vbAntiPatterns, null);
    }
}
