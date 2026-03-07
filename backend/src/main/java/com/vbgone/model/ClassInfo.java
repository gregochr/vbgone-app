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
        List<String> vbAntiPatterns
) {}
