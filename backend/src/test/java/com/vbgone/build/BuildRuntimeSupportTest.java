package com.vbgone.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Direct coverage of the helpers shared by DotNetRuntime and VbCharacterisationRunner. */
class BuildRuntimeSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void deleteRecursively_removesNestedTree() throws IOException {
        Path root = tempDir.resolve("proj");
        Files.createDirectories(root.resolve("a/b"));
        Files.writeString(root.resolve("a/b/file.cs"), "x");
        Files.writeString(root.resolve("a/top.cs"), "y");

        BuildRuntimeSupport.deleteRecursively(root);

        assertThat(Files.exists(root)).isFalse();
    }

    @Test
    void deleteRecursively_isANoOpWhenAbsent() throws IOException {
        // Must not throw for a directory that was never created.
        BuildRuntimeSupport.deleteRecursively(tempDir.resolve("does-not-exist"));
    }

    @Test
    void dotnetTestCommand_isTheDockerExecDotnetTestInvocation() {
        List<String> cmd = BuildRuntimeSupport.dotnetTestCommand("sidecar", "/workspace/s1/Foo.Tests");
        assertThat(cmd).containsExactly(
                "docker", "exec", "sidecar",
                "dotnet", "test", "/workspace/s1/Foo.Tests",
                "--logger", "trx;LogFileName=results.trx",
                "--collect", "XPlat Code Coverage");
    }

    @Test
    void parseCompilationErrors_extractsErrorLines() {
        String stdout = """
                Determining projects to restore...
                /workspace/s1/Foo/Foo.cs(12,9): error CS0103: The name 'x' does not exist
                Build succeeded with warnings
                /workspace/s1/Foo/Foo.cs(18,5): error CS1002: ; expected
                """;
        List<String> errors = BuildRuntimeSupport.parseCompilationErrors("", stdout);
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0)).contains("CS0103");
        assertThat(errors.get(1)).contains("CS1002");
    }

    @Test
    void parseCompilationErrors_readsFromStdoutWhenStderrEmpty() {
        List<String> errors = BuildRuntimeSupport.parseCompilationErrors(
                "", "/x/Foo.cs(1,1): error CS0000: boom");
        assertThat(errors).containsExactly("/x/Foo.cs(1,1): error CS0000: boom");
    }

    @Test
    void parseCompilationErrors_blankOutputFallsBack() {
        assertThat(BuildRuntimeSupport.parseCompilationErrors(null, null))
                .containsExactly("Build failed with no error output");
        assertThat(BuildRuntimeSupport.parseCompilationErrors("  ", ""))
                .containsExactly("Build failed with no error output");
    }

    @Test
    void parseCompilationErrors_noErrorLineReturnsTruncatedOutput() {
        String noisy = "some maven-like failure with no compiler diagnostics";
        assertThat(BuildRuntimeSupport.parseCompilationErrors(noisy, "")).containsExactly(noisy);
    }
}
