package com.vbgone.service;

import com.vbgone.model.*;
import com.vbgone.session.SessionStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@Service
public class BuildService {

    static final String MAIN_CSPROJ = """
            <Project Sdk="Microsoft.NET.Sdk">
              <PropertyGroup>
                <TargetFramework>net8.0</TargetFramework>
                <Nullable>enable</Nullable>
                <ImplicitUsings>enable</ImplicitUsings>
              </PropertyGroup>
            </Project>
            """;

    static final String TEST_CSPROJ_TEMPLATE = """
            <Project Sdk="Microsoft.NET.Sdk">
              <PropertyGroup>
                <TargetFramework>net8.0</TargetFramework>
                <Nullable>enable</Nullable>
                <ImplicitUsings>enable</ImplicitUsings>
                <IsPackable>false</IsPackable>
                <IsTestProject>true</IsTestProject>
              </PropertyGroup>
              <ItemGroup>
                <PackageReference Include="NUnit" Version="4.1.0" />
                <PackageReference Include="NUnit3TestAdapter" Version="4.6.0" />
                <PackageReference Include="Microsoft.NET.Test.Sdk" Version="17.11.1" />
              </ItemGroup>
              <ItemGroup>
                <ProjectReference Include="../%s/%s.csproj" />
              </ItemGroup>
            </Project>
            """;

    private final SessionStore sessionStore;
    private final Path workspacePath;
    private final String containerName;
    private final ProcessRunner processRunner;

    public BuildService(SessionStore sessionStore,
                        @Value("${vbgone.workspace:/workspace}") String workspacePath,
                        @Value("${dotnet.runner.container:vbgone-app-dotnet-runner-1}") String containerName,
                        ProcessRunner processRunner) {
        this.sessionStore = sessionStore;
        this.workspacePath = Path.of(workspacePath);
        this.containerName = containerName;
        this.processRunner = processRunner;
    }

    public BuildResult build(String sessionId) {
        MigrationSession session = getSession(sessionId);

        InterfaceResult iface = session.getInterfaceResult();
        if (iface == null) {
            throw new IllegalStateException("Interface must be generated before building");
        }
        TestsResult tests = session.getTestsResult();
        if (tests == null) {
            throw new IllegalStateException("Tests must be generated before building");
        }

        String className = iface.className();
        String implementationCode = getImplementationCode(session);
        List<String> dependencies = getDependencies(session, className);

        Path sessionDir = workspacePath.resolve(sessionId);

        try {
            writeProjectFiles(sessionDir, className, iface, tests, implementationCode, dependencies);

            ProcessOutput output = executeDotnetTest(sessionId, className);

            Path trxPath = sessionDir.resolve(className + ".Tests")
                    .resolve("TestResults").resolve("results.trx");

            BuildResult result;
            if (output.exitCode() != 0 && !Files.exists(trxPath)) {
                List<String> errors = parseCompilationErrors(output.stderr(), output.stdout());
                result = new BuildResult(sessionId, BuildStatus.ERROR, 0, 0, 0, errors, List.of());
            } else {
                String trxContent = Files.readString(trxPath);
                result = parseTrx(sessionId, trxContent);
            }

            if (result.buildStatus() == BuildStatus.GREEN) {
                session.setGreenBuild(result);
            } else {
                session.setRedBuild(result);
            }

            return result;

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Build failed: " + e.getMessage(), e);
        }
    }

    private String getImplementationCode(MigrationSession session) {
        if (session.getImplementResult() != null) {
            return session.getImplementResult().code();
        }
        if (session.getStubResult() != null) {
            return session.getStubResult().code();
        }
        throw new IllegalStateException("Stub or implementation must exist before building");
    }

    void writeProjectFiles(Path sessionDir, String className,
                           InterfaceResult iface, TestsResult tests,
                           String implementationCode, List<String> dependencies) throws IOException {
        Path mainDir = sessionDir.resolve(className);
        Path testDir = sessionDir.resolve(className + ".Tests");

        // Clean previous build artifacts for this class to prevent stale files
        if (Files.exists(mainDir)) {
            try (var walk = Files.walk(mainDir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
        if (Files.exists(testDir)) {
            try (var walk = Files.walk(testDir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }

        Files.createDirectories(mainDir);
        Files.createDirectories(testDir);

        // Ensure dependency project directories exist with at least a stub .csproj and interface
        for (String dep : dependencies) {
            Path depDir = sessionDir.resolve(dep);
            if (!Files.exists(depDir.resolve(dep + ".csproj"))) {
                Files.createDirectories(depDir);
                Files.writeString(depDir.resolve(dep + ".csproj"), MAIN_CSPROJ);
                // Write a minimal stub interface so the dependency compiles
                String depInterface = "public interface I" + dep + " { }";
                Files.writeString(depDir.resolve("I" + dep + ".cs"), depInterface);
                // Write a minimal stub class
                String depStub = "public class " + dep + " : I" + dep + " { }";
                Files.writeString(depDir.resolve(dep + ".cs"), depStub);
            }
        }

        String mainCsproj = buildMainCsproj(dependencies);
        Files.writeString(mainDir.resolve(className + ".csproj"), mainCsproj);
        Files.writeString(mainDir.resolve(iface.interfaceName() + ".cs"), iface.code());
        Files.writeString(mainDir.resolve(className + ".cs"), implementationCode);

        String testCsproj = String.format(TEST_CSPROJ_TEMPLATE, className, className);
        Files.writeString(testDir.resolve(className + ".Tests.csproj"), testCsproj);
        Files.writeString(testDir.resolve(tests.testClassName() + ".cs"), tests.code());
    }

    private List<String> getDependencies(MigrationSession session, String className) {
        AnalysisResult analysis = session.getAnalysisResult();
        if (analysis == null) return List.of();
        return analysis.classes().stream()
                .filter(c -> c.name().equals(className))
                .findFirst()
                .map(ClassInfo::dependencies)
                .orElse(List.of());
    }

    private String buildMainCsproj(List<String> dependencies) {
        if (dependencies.isEmpty()) return MAIN_CSPROJ;
        StringBuilder refs = new StringBuilder();
        for (String dep : dependencies) {
            refs.append("    <ProjectReference Include=\"../").append(dep).append("/").append(dep).append(".csproj\" />\n");
        }
        return """
                <Project Sdk="Microsoft.NET.Sdk">
                  <PropertyGroup>
                    <TargetFramework>net8.0</TargetFramework>
                    <Nullable>enable</Nullable>
                    <ImplicitUsings>enable</ImplicitUsings>
                  </PropertyGroup>
                  <ItemGroup>
                """ + refs + """
                  </ItemGroup>
                </Project>
                """;
    }

    private ProcessOutput executeDotnetTest(String sessionId, String className)
            throws IOException, InterruptedException {
        String containerTestPath = "/workspace/" + sessionId + "/" + className + ".Tests";
        return processRunner.run(List.of(
                "docker", "exec", containerName,
                "dotnet", "test", containerTestPath,
                "--logger", "trx;LogFileName=results.trx"
        ));
    }

    BuildResult parseTrx(String sessionId, String trxContent) {
        try {
            var doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new ByteArrayInputStream(trxContent.getBytes(StandardCharsets.UTF_8)));

            var counters = (org.w3c.dom.Element) doc.getElementsByTagName("Counters").item(0);
            int total = Integer.parseInt(counters.getAttribute("total"));
            int passed = Integer.parseInt(counters.getAttribute("passed"));
            int failed = Integer.parseInt(counters.getAttribute("failed"));

            List<String> failedTests = new java.util.ArrayList<>();
            java.util.Map<String, String> failureMessages = new java.util.LinkedHashMap<>();
            var results = doc.getElementsByTagName("UnitTestResult");
            for (int i = 0; i < results.getLength(); i++) {
                var el = (org.w3c.dom.Element) results.item(i);
                if ("Failed".equals(el.getAttribute("outcome"))) {
                    String testName = el.getAttribute("testName");
                    failedTests.add(testName);
                    // Extract assertion failure message from Output/ErrorInfo/Message
                    var outputNodes = el.getElementsByTagName("Message");
                    if (outputNodes.getLength() > 0) {
                        failureMessages.put(testName, outputNodes.item(0).getTextContent().trim());
                    }
                }
            }

            // Store failure messages on session for retry prompts
            sessionStore.get(sessionId).ifPresent(s -> s.setFailureMessages(failureMessages));

            BuildStatus status = (failed == 0) ? BuildStatus.GREEN : BuildStatus.RED;
            return new BuildResult(sessionId, status, total, passed, failed, List.of(), failedTests);

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse .trx results: " + e.getMessage(), e);
        }
    }

    List<String> parseCompilationErrors(String stderr, String stdout) {
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

    private MigrationSession getSession(String sessionId) {
        return sessionStore.get(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
    }
}
