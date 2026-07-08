package com.vbgone.model;

import java.util.List;

/**
 * Assure step 3 result — the concrete class's actual public surface, pinned against the
 * real assemblies (not synthesised). Members the analysis flagged carry a defect tag.
 *
 * @param surfaceFile display label for the code-block header, e.g. "OrderProcessor.dll · public surface"
 */
public record BaselineResult(
        String sessionId,
        String className,
        String surfaceFile,
        List<BaselineMember> members
) {}
