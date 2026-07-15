package com.vbgone.build;

import java.io.IOException;
import java.util.Map;

/**
 * Transports a generated net48 characterisation project to a Windows runner and returns the run's
 * artifacts (the MSTest {@code results.trx} plus, when collected, a Cobertura coverage report). The
 * production implementation ({@link GitHubActionsTransport}) dispatches to a GitHub-hosted
 * {@code windows-latest} runner; tests substitute a fake so {@link WindowsCharacterisationRunner}'s
 * parse path can be exercised without any network.
 */
public interface WindowsRunnerTransport {

    /**
     * What the runner produced: the {@code results.trx} (required) and the Cobertura coverage XML
     * ({@code coverageXml}, {@code null} when coverage wasn't collected or couldn't be found).
     */
    record Artifacts(String trx, String coverageXml) {}

    /**
     * Runs the generated project on the Windows runner and returns its artifacts.
     *
     * @param jobId unique label for this run (also names the {@code runner/<jobId>} branch)
     * @param files repo-relative path → file content for the generated net48 project
     * @return the {@code .trx} (identical in shape to the Linux runner's) + optional coverage XML
     * @throws IOException          on dispatch/download failure, a timeout, or a missing {@code .trx}
     *                              (e.g. the VB failed to compile, so no test results were produced)
     * @throws InterruptedException if the wait for the run is interrupted
     */
    Artifacts characterise(String jobId, Map<String, String> files) throws IOException, InterruptedException;
}
