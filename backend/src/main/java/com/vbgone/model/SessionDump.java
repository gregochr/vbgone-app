package com.vbgone.model;

import java.util.List;
import java.util.Map;

public record SessionDump(
        String sessionId,
        String vbContent,
        Map<String, String> classSources,
        InterfaceResult interfaceResult,
        TestsResult testsResult,
        StubResult stubResult,
        ImplementResult implementResult,
        BuildResult redBuild,
        BuildResult greenBuild,
        List<TokenUsage> tokenUsages
) {}
