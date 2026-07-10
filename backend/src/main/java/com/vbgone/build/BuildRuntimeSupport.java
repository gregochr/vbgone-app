package com.vbgone.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Static helpers shared by the C#/dotnet build paths ({@link DotNetRuntime} and
 * {@link VbCharacterisationRunner}): a best-effort recursive delete, the {@code docker exec …
 * dotnet test} command, compilation-error parsing, and the interrupt-aware process-failure wrap.
 * These were byte-identical copies across those classes; centralising them follows the same
 * precedent as {@link TrxParser} / {@code CoverageParser}.
 *
 * <p>{@link JavaRuntime} and {@link DotNetRuntime} additionally share the {@code build()} skeleton
 * via {@link AbstractBuildRuntime}. {@code VbCharacterisationRunner} is not a {@link BuildRuntime}
 * (different entry points and session side effects), so it composes these helpers rather than
 * extending that base.
 */
final class BuildRuntimeSupport {

    private BuildRuntimeSupport() {
    }

    /**
     * Wrap a process-execution failure in a {@link RuntimeException}, re-setting the thread's
     * interrupt flag first when the cause is an {@link InterruptedException}. Centralises the
     * interrupt-aware catch that the build runtimes and the characterisation runner all repeat.
     * Returns (rather than throws) so callers read {@code throw wrapProcessFailure(...)} and the
     * compiler still sees a definite throw.
     */
    static RuntimeException wrapProcessFailure(String context, Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        return new RuntimeException(context + ": " + e.getMessage(), e);
    }

    /**
     * Recursively delete {@code dir} if it exists, ignoring individual delete failures — a
     * best-effort clean of stale build artifacts, not a guarantee.
     */
    static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }

    /**
     * The {@code docker exec <container> dotnet test <path>} command with {@code .trx} logging and
     * XPlat coverage. The {@code --collect} flag is required — without it Coverlet writes no report,
     * so {@code CoverageParser} finds nothing and the coverage badge silently hides.
     */
    static List<String> dotnetTestCommand(String container, String containerTestPath) {
        return List.of(
                "docker", "exec", container,
                "dotnet", "test", containerTestPath,
                "--logger", "trx;LogFileName=results.trx",
                "--collect", "XPlat Code Coverage"
        );
    }

    /**
     * Parse dotnet build/test output into the {@code ": error "} lines, falling back to a fixed
     * message when the output is blank and to the first 500 chars when no error line is present.
     */
    static List<String> parseCompilationErrors(String stderr, String stdout) {
        String combined = ((stderr != null ? stderr : "") + "\n" + (stdout != null ? stdout : "")).trim();
        if (combined.isBlank()) {
            return List.of("Build failed with no error output");
        }
        List<String> errors = Arrays.stream(combined.split("\n"))
                .filter(line -> line.contains(": error "))
                .map(String::trim)
                .toList();
        return errors.isEmpty() ? List.of(combined.substring(0, Math.min(combined.length(), 500))) : errors;
    }
}
