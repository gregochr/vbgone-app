package com.vbgone.build;

import com.vbgone.model.*;
import com.vbgone.service.ProcessOutput;
import com.vbgone.service.ProcessRunner;
import com.vbgone.session.SessionStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * C# / NUnit build runtime. Scaffolds a .NET solution (main + test csproj) under
 * the shared workspace volume, runs {@code dotnet test} in the .NET SDK sidecar,
 * and parses the resulting {@code .trx}. Behaviour is identical to the original
 * {@code BuildService} build body.
 */
@Service
public class DotNetRuntime extends AbstractBuildRuntime {

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
                <PackageReference Include="coverlet.collector" Version="6.0.2" />
              </ItemGroup>
              <ItemGroup>
                <ProjectReference Include="../%s/%s.csproj" />
              </ItemGroup>
            </Project>
            """;

    private final SessionStore sessionStore;
    private final String containerName;
    private final ProcessRunner processRunner;

    public DotNetRuntime(SessionStore sessionStore,
                         @Value("${vbgone.workspace:/workspace}") String workspacePath,
                         @Value("${dotnet.runner.container:vbgone-app-dotnet-runner-1}") String containerName,
                         ProcessRunner processRunner) {
        super(Path.of(workspacePath));
        this.sessionStore = sessionStore;
        this.containerName = containerName;
        this.processRunner = processRunner;
    }

    @Override
    public String id() {
        return "csharp";
    }

    @Override
    void writeProjectFiles(Path sessionDir, String className,
                           InterfaceResult iface, TestsResult tests,
                           String implementationCode, List<String> dependencies) throws IOException {
        Path mainDir = sessionDir.resolve(className);
        Path testDir = sessionDir.resolve(className + ".Tests");

        // Clean previous build artifacts for this class to prevent stale files
        BuildRuntimeSupport.deleteRecursively(mainDir);
        BuildRuntimeSupport.deleteRecursively(testDir);

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

    private static Path trxPath(Path sessionDir, String className) {
        return sessionDir.resolve(className + ".Tests").resolve("TestResults").resolve("results.trx");
    }

    @Override
    ProcessOutput runTests(String sessionId, String className)
            throws IOException, InterruptedException {
        String containerTestPath = "/workspace/" + sessionId + "/" + className + ".Tests";
        return processRunner.run(BuildRuntimeSupport.dotnetTestCommand(containerName, containerTestPath));
    }

    @Override
    boolean hasResults(Path sessionDir, String className) {
        return Files.exists(trxPath(sessionDir, className));
    }

    @Override
    BuildResult parseResults(String sessionId, Path sessionDir, String className) throws IOException {
        String trxContent = Files.readString(trxPath(sessionDir, className));
        BuildResult result = parseTrx(sessionId, trxContent);
        // Coverage of the implementation assembly (named after the class).
        Path testResults = sessionDir.resolve(className + ".Tests").resolve("TestResults");
        CoverageParser.Coverage cov = CoverageParser.parse(testResults, className);
        return result.withCoverage(cov.linePercent(), cov.branchPercent());
    }

    @Override
    List<String> parseErrors(String sessionId, ProcessOutput output) {
        return parseCompilationErrors(output.stderr(), output.stdout());
    }

    BuildResult parseTrx(String sessionId, String trxContent) {
        TrxParser.Parsed parsed = TrxParser.parse(sessionId, trxContent);
        // Store failure messages on session for retry prompts.
        sessionStore.get(sessionId).ifPresent(s -> s.setFailureMessages(parsed.failureMessages()));
        return parsed.result();
    }

    List<String> parseCompilationErrors(String stderr, String stdout) {
        return BuildRuntimeSupport.parseCompilationErrors(stderr, stdout);
    }
}
