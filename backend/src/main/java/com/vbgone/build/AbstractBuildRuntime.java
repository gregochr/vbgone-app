package com.vbgone.build;

import com.vbgone.model.BuildResult;
import com.vbgone.model.BuildStatus;
import com.vbgone.model.InterfaceResult;
import com.vbgone.model.MigrationSession;
import com.vbgone.model.TestsResult;
import com.vbgone.service.ProcessOutput;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Template-method base for the language build runtimes. Owns the {@code build()} skeleton the two
 * implementations shared verbatim: derive the session dir, write the project, run the tests, then
 * gate on the outcome — a non-zero exit with no results file means a compile failure (ERROR via
 * {@link #parseErrors}), otherwise parse the results into GREEN/RED — all inside one interrupt-aware
 * catch. Concrete runtimes supply the five language-specific hooks.
 *
 * <p>Only {@link JavaRuntime} and {@link DotNetRuntime} share this shape. {@code VbCharacterisationRunner}
 * is intentionally NOT a {@link BuildRuntime} (different entry points, session side effects) and
 * composes the {@link BuildRuntimeSupport} helpers instead of extending this base.
 */
abstract class AbstractBuildRuntime implements BuildRuntime {

    protected final Path workspacePath;

    protected AbstractBuildRuntime(Path workspacePath) {
        this.workspacePath = workspacePath;
    }

    @Override
    public final BuildResult build(MigrationSession session, InterfaceResult iface, TestsResult tests,
                                   String implementationCode, List<String> dependencies) {
        String sessionId = session.getSessionId();
        String className = iface.className();
        Path sessionDir = workspacePath.resolve(sessionId);

        try {
            writeProjectFiles(sessionDir, className, iface, tests, implementationCode, dependencies);
            ProcessOutput output = runTests(sessionId, className);

            if (output.exitCode() != 0 && !hasResults(sessionDir, className)) {
                return new BuildResult(sessionId, BuildStatus.ERROR, 0, 0, 0,
                        parseErrors(sessionId, output), List.of());
            }
            return parseResults(sessionId, sessionDir, className);

        } catch (IOException | InterruptedException e) {
            throw BuildRuntimeSupport.wrapProcessFailure("Build failed", e);
        }
    }

    /** Scaffold the project + source files for the generated code under {@code sessionDir}. */
    abstract void writeProjectFiles(Path sessionDir, String className, InterfaceResult iface,
                                    TestsResult tests, String implementationCode,
                                    List<String> dependencies) throws IOException;

    /** Run the language's test command in its sidecar container. */
    abstract ProcessOutput runTests(String sessionId, String className)
            throws IOException, InterruptedException;

    /**
     * Whether the run produced a machine-readable results file. Distinguishes a compile failure
     * (no file → ERROR) from red tests (file present, some failed).
     */
    abstract boolean hasResults(Path sessionDir, String className) throws IOException;

    /** Parse the results file into a GREEN/RED {@link BuildResult} (and record failure messages). */
    abstract BuildResult parseResults(String sessionId, Path sessionDir, String className)
            throws IOException;

    /** Extract compilation-error lines when the build produced no results file. */
    abstract List<String> parseErrors(String sessionId, ProcessOutput output);
}
