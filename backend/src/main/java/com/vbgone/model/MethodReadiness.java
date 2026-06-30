package com.vbgone.model;

/**
 * A single method's readiness verdict from the static classifier.
 *
 * @param name       method name
 * @param visibility "public" | "private" | "friend" | "protected"
 * @param bucket     the readiness bucket
 * @param reason     a one-line plain-language justification
 */
public record MethodReadiness(
        String name,
        String visibility,
        Bucket bucket,
        String reason
) {}
