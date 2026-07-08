package com.vbgone.build;

import com.vbgone.model.BuildResult;
import com.vbgone.model.BuildStatus;
import com.vbgone.model.MigrationSession;
import com.vbgone.model.TestsResult;
import com.vbgone.service.ProcessOutput;
import com.vbgone.service.ProcessRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Assure's characterisation runner. Compiles the user's ORIGINAL VB.NET into an assembly
 * and runs a C# MSTest suite against it on the .NET SDK sidecar — the same {@code docker exec
 * dotnet test} + {@code .trx} machinery as {@link DotNetRuntime}, but the system-under-test is
 * the untouched original, not generated code.
 *
 * <p><b>Constraint:</b> the sidecar is Linux .NET SDK, which compiles VB and runs MSTest but
 * cannot execute WinForms (Windows-only). This works for business-logic classes (the
 * {@code OrderProcessor} demo path); UI-coupled VB fails to compile and surfaces as
 * {@link BuildStatus#ERROR}.
 */
@Component
public class VbCharacterisationRunner {

    static final String VBPROJ = """
            <Project Sdk="Microsoft.NET.Sdk">
              <PropertyGroup>
                <TargetFramework>net8.0</TargetFramework>
                <RootNamespace></RootNamespace>
                <Nullable>disable</Nullable>
              </PropertyGroup>
            </Project>
            """;

    // MSTest project. A global Using makes [TestClass]/[TestMethod]/Assert available without
    // a 'using' line, so the suite compiles whether or not the model emitted one. References
    // the original VB project so tests run against the real assembly.
    static final String BASELINE_CSPROJ_TEMPLATE = """
            <Project Sdk="Microsoft.NET.Sdk">
              <PropertyGroup>
                <TargetFramework>net8.0</TargetFramework>
                <Nullable>disable</Nullable>
                <ImplicitUsings>enable</ImplicitUsings>
                <IsPackable>false</IsPackable>
                <IsTestProject>true</IsTestProject>
              </PropertyGroup>
              <ItemGroup>
                <PackageReference Include="Microsoft.NET.Test.Sdk" Version="17.11.1" />
                <PackageReference Include="MSTest.TestAdapter" Version="3.6.1" />
                <PackageReference Include="MSTest.TestFramework" Version="3.6.1" />
              </ItemGroup>
              <ItemGroup>
                <Using Include="Microsoft.VisualStudio.TestTools.UnitTesting" />
              </ItemGroup>
              <ItemGroup>
                <ProjectReference Include="../%1$s.Vb/%1$s.vbproj" />
              </ItemGroup>
            </Project>
            """;

    private final Path workspacePath;
    private final String containerName;
    private final ProcessRunner processRunner;

    public VbCharacterisationRunner(
            @Value("${vbgone.workspace:/workspace}") String workspacePath,
            @Value("${dotnet.runner.container:vbgone-app-dotnet-runner-1}") String containerName,
            ProcessRunner processRunner) {
        this.workspacePath = Path.of(workspacePath);
        this.containerName = containerName;
        this.processRunner = processRunner;
    }

    /**
     * Compiles the original VB for {@code className} and runs the MSTest characterisation
     * {@code suite} against it. Returns GREEN when every assertion holds (the net is faithful),
     * RED when assertions fail, ERROR when the VB or suite fails to compile.
     */
    public BuildResult run(MigrationSession session, String className, TestsResult suite) {
        String sessionId = session.getSessionId();
        // Compile the headless-compilable subset (the net-ready classes concatenated), NOT the
        // whole estate: an uploaded solution's WinForms classes can't compile on the Linux CLR
        // and would poison the build even when the class under test is itself clean. The subset
        // keeps the class under test plus its net-ready siblings (so cross-references resolve).
        // Falls back to the full source for older/single-file sessions with no subset pinned.
        String vbSource = session.getAssurableSource();
        if (vbSource == null || vbSource.isBlank()) {
            vbSource = session.getVbContent();
        }
        Path assureDir = workspacePath.resolve(sessionId).resolve("assure");

        try {
            writeProjectFiles(assureDir, className, vbSource, suite);

            ProcessOutput output = executeDotnetTest(sessionId, className);

            Path trxPath = assureDir.resolve(className + ".Baseline")
                    .resolve("TestResults").resolve("results.trx");

            if (output.exitCode() != 0 && !Files.exists(trxPath)) {
                List<String> errors = parseCompilationErrors(output.stderr(), output.stdout());
                session.setFailureMessages(java.util.Map.of());
                return new BuildResult(sessionId, BuildStatus.ERROR, 0, 0, 0, errors, List.of());
            }
            String trxContent = Files.readString(trxPath);
            TrxParser.Parsed parsed = TrxParser.parse(sessionId, trxContent);
            // Surface the per-assertion failure messages so the UI can show which drifted.
            session.setFailureMessages(parsed.failureMessages());
            return parsed.result();

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Characterisation run failed: " + e.getMessage(), e);
        }
    }

    void writeProjectFiles(Path assureDir, String className, String vbSource, TestsResult suite)
            throws IOException {
        Path vbDir = assureDir.resolve(className + ".Vb");
        Path baselineDir = assureDir.resolve(className + ".Baseline");

        deleteRecursively(vbDir);
        deleteRecursively(baselineDir);
        Files.createDirectories(vbDir);
        Files.createDirectories(baselineDir);

        Files.writeString(vbDir.resolve(className + ".vbproj"), VBPROJ);
        Files.writeString(vbDir.resolve(className + ".vb"), vbSource == null ? "" : vbSource);

        Files.writeString(baselineDir.resolve(className + ".Baseline.csproj"),
                String.format(BASELINE_CSPROJ_TEMPLATE, className));
        Files.writeString(baselineDir.resolve(suite.testClassName() + ".cs"), suite.code());
    }

    private void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        }
    }

    private ProcessOutput executeDotnetTest(String sessionId, String className)
            throws IOException, InterruptedException {
        String baselinePath = "/workspace/" + sessionId + "/assure/" + className + ".Baseline";
        return processRunner.run(List.of(
                "docker", "exec", containerName,
                "dotnet", "test", baselinePath,
                "--logger", "trx;LogFileName=results.trx"
        ));
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
}
