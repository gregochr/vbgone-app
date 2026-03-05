package com.vbgone.model;

import java.util.List;

public record AnalysisResult(
        String sessionId,
        List<ClassInfo> classes,
        List<String> suggestedMigrationOrder,
        String summary
) {}
