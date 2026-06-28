package com.vbgone.integration;

import com.vbgone.service.ProcessOutput;
import com.vbgone.service.ProcessRunner;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

/**
 * A {@link ProcessRunner} for integration tests that copies files to/from the
 * Maven/JDK sidecar Docker container — the Maven analog of {@link DockerProcessRunner}.
 *
 * <p>Since the host temp dir isn't visible inside the container, this runner:
 * <ol>
 *   <li>Before mvn test: copies the session dir into the container's /workspace</li>
 *   <li>Runs mvn test via docker exec</li>
 *   <li>After mvn test: copies target/surefire-reports back to the host temp dir so
 *       {@link com.vbgone.build.JavaRuntime#parseSurefire} (which reads the host
 *       {@code target/surefire-reports}) finds the TEST-*.xml reports</li>
 * </ol>
 */
class DockerMavenProcessRunner implements ProcessRunner {

    private final String containerName;
    private final Path hostWorkspace;

    DockerMavenProcessRunner(String containerName, Path hostWorkspace) {
        this.containerName = containerName;
        this.hostWorkspace = hostWorkspace;
    }

    @Override
    public ProcessOutput run(List<String> command) throws IOException, InterruptedException {
        // The command looks like: docker exec <container> mvn -q -f /workspace/<sessionId>/pom.xml test
        // Extract the session path from the command (the /workspace/<sessionId>/pom.xml argument).
        String pomPath = null;
        for (String arg : command) {
            if (arg.startsWith("/workspace/")) {
                pomPath = arg;
                break;
            }
        }

        String sessionId = null;
        if (pomPath != null) {
            // Extract session ID from /workspace/<sessionId>/pom.xml
            String relativePath = pomPath.substring("/workspace/".length());
            sessionId = relativePath.split("/")[0];

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

        if (sessionId != null) {
            // Copy surefire reports back from the container to the host so JavaRuntime
            // can read target/surefire-reports/TEST-*.xml on the host filesystem.
            try {
                copyFromContainer(
                        "/workspace/" + sessionId + "/target/surefire-reports",
                        hostWorkspace.resolve(sessionId).resolve("target").resolve("surefire-reports"));
            } catch (Exception ignored) {
                // surefire-reports may not exist if compilation failed
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
