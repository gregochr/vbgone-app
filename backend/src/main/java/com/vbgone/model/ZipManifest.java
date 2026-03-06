package com.vbgone.model;

import java.util.List;

public record ZipManifest(
        String sessionId,
        List<VbSourceFile> files,
        int totalFiles
) {}
