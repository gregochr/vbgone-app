package com.vbgone.model;

/**
 * Assure step 4 result — the generated MSTest characterisation suite plus the outcome of
 * running it against the original VB.NET on the CLR.
 *
 * <p>{@code netFaithful} is the success signal: true when every assertion holds against the
 * untouched original (green/required). False means the net drifted into aspiration — an
 * assertion describes behaviour the code doesn't have — which is an error to correct, NOT a
 * sign the code is broken.
 */
import java.util.List;

public record BaselineTestsResult(
        String sessionId,
        String className,
        String testClassName,
        String code,
        int testCount,
        boolean netFaithful,
        BuildResult build,
        /** Failing assertions when the net isn't faithful — name + message. Empty when green. */
        List<TestFailure> failures
) {}
