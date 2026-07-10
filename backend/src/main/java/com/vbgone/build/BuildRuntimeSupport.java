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
 * dotnet test} command, and compilation-error parsing. These were byte-identical copies in both
 * classes; centralising them follows the same precedent as {@link TrxParser} / {@code CoverageParser}.
 * Composition, not inheritance — {@code VbCharacterisationRunner} is not a {@link BuildRuntime} and
 * {@code JavaRuntime} uses {@code mvn}, so a shared base class would not fit.
 */
final class BuildRuntimeSupport {

    private BuildRuntimeSupport() {
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
