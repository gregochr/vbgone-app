package com.vbgone.model;

import java.util.List;
import java.util.Map;

public record ProjectAnalysis(
        String sessionId,
        List<ClassInfo> classes,
        List<String> suggestedMigrationOrder,
        Map<String, List<String>> dependencyGraph,
        String summary
) {}
