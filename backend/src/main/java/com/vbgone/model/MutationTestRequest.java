package com.vbgone.model;

/**
 * Start a mutation-testing job. The green characterisation suite is passed in (like
 * {@code rerun-baseline-tests}) so the run is grounded on exactly what the user is looking at,
 * without depending on server-side suite persistence.
 */
public record MutationTestRequest(String sessionId, String className, String suiteCode) {}
