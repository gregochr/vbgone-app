package com.vbgone.model;

import java.util.List;

public record BuildResult(
        String sessionId,
        BuildStatus buildStatus,
        int total,
        int passed,
        int failed,
        List<String> errors,
        List<String> failedTests
) {}
