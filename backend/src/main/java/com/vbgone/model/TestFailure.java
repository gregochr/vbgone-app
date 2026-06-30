package com.vbgone.model;

/**
 * A single failing characterisation assertion: the test name and the runner's failure
 * message. In the "net not faithful" state these tell the user exactly which assertion
 * drifted into aspiration so they can correct the net.
 */
public record TestFailure(
        String name,
        String message
) {}
