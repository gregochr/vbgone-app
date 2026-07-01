package com.vbgone.model;

import java.util.List;

/**
 * One class's readiness: its rolled-up bucket (worst-case across its methods), a reason,
 * and the per-method breakdown.
 *
 * @param name    class name
 * @param file    source path the class was found in
 * @param bucket  worst-case rollup of {@code methods}
 * @param reason  one-line plain-language summary
 * @param methods per-method verdicts
 */
public record ClassReadiness(
        String name,
        String file,
        Bucket bucket,
        String reason,
        List<MethodReadiness> methods
) {}
