package com.vbgone.integration;

import com.vbgone.service.ProcessOutput;
import com.vbgone.service.ProcessRunner;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

/**
 * A ProcessRunner for integration tests that copies files to/from the Docker container.
 *
 * <p>Since the host temp dir isn't visible inside the container, this runner:
 * <ol>
 *   <li>Before dotnet test: copies the session dir into the container's /workspace</li>
 *   <li>Runs dotnet test via docker exec</li>
 *   <li>After dotnet test: copies TestResults back to the host temp dir</li>
 * </ol>
 */
class DockerProcessRunner implements ProcessRunner {

    private final String containerName;
    private final Path hostWorkspace;

    DockerProcessRunner(String containerName, Path hostWorkspace) {
        this.containerName = containerName;
        this.hostWorkspace = hostWorkspace;
    }

    @Override
    public ProcessOutput run(List<String> command) throws IOException, InterruptedException {
        // The command looks like: docker exec <container> dotnet test /workspace/<sessionId>/<class>.Tests ...
        // Extract the session path from the command
        String testPath = null;
        for (String arg : command) {
            if (arg.startsWith("/workspace/")) {
                testPath = arg;
                break;
            }
        }

        if (testPath != null) {
            // Extract session ID and class from /workspace/<sessionId>/<class>.Tests
            String relativePath = testPath.substring("/workspace/".length());
            String sessionId = relativePath.split("/")[0];

            // Copy the host session dir into the container
            Path hostSessionDir = hostWorkspace.resolve(sessionId);
            copyToContainer(hostSessionDir, "/workspace/" + sessionId);
        }

        // Execute the actual docker exec command
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        Process process = pb.start();

        String stdout = new String(process.getInputStream().readAllBytes());
        String stderr = new String(process.getErrorStream().readAllBytes());
        int exitCode = process.waitFor();

        if (testPath != null) {
            // Copy TestResults back from the container to the host
            String relativePath = testPath.substring("/workspace/".length());
            String sessionId = relativePath.split("/")[0];
            String testDir = relativePath; // e.g. sessionId/Class.Tests

            try {
                copyFromContainer("/workspace/" + testDir + "/TestResults",
                        hostWorkspace.resolve(testDir).resolve("TestResults"));
            } catch (Exception ignored) {
                // TestResults may not exist if compilation failed
            }
        }

        return new ProcessOutput(exitCode, stdout, stderr);
    }

    private void copyToContainer(Path hostDir, String containerPath) throws IOException, InterruptedException {
        // Remove old content first
        new ProcessBuilder("docker", "exec", containerName, "rm", "-rf", containerPath)
                .start().waitFor();

        // docker cp copies contents of hostDir into containerPath
        Process p = new ProcessBuilder("docker", "cp",
                hostDir.toString(), containerName + ":" + containerPath)
                .start();
        p.waitFor();
    }

    private void copyFromContainer(String containerPath, Path hostDir) throws IOException, InterruptedException {
        Files.createDirectories(hostDir);
        Process p = new ProcessBuilder("docker", "cp",
                containerName + ":" + containerPath + "/.", hostDir.toString())
                .start();
        p.waitFor();
    }
}
