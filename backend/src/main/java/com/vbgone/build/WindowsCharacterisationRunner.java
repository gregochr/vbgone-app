package com.vbgone.build;

import com.vbgone.model.BuildResult;
import com.vbgone.model.BuildStatus;
import com.vbgone.model.MigrationSession;
import com.vbgone.model.TestsResult;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Characterises a {@link com.vbgone.model.Bucket#WINDOWS_GATED} class — one that references .NET
 * Framework-only APIs (LINQ-to-SQL / ASMX / WCF / WebForms / COM+) and therefore cannot build on
 * the Linux sidecar — by generating a net48 project and running it on a Windows runner via
 * {@link WindowsRunnerTransport}.
 *
 * <p>The outcome comes from the identical {@link TrxParser} over the returned {@code .trx}, so the
 * {@link BuildResult} it produces is indistinguishable from {@link VbCharacterisationRunner}'s.
 * Line/branch coverage is not collected on this path yet (the Linux runner reads it from disk;
 * here it would mean parsing the coverage file out of the artifact — a later refinement).
 *
 * <p>The net48 templates are twins of {@link VbCharacterisationRunner}'s, proven by the Phase 0
 * spike (see {@code spikes/assure-windows-runner/}). The one non-obvious change is
 * {@code <LangVersion>latest</LangVersion>}: net48 defaults to C# 7.3, but the global {@code Using}
 * emits a {@code global using} directive (a C# 10 feature) — without the pin the build fails CS8370.
 */
@Component
public class WindowsCharacterisationRunner implements CharacterisationRunner {

    // net48 twin of VbCharacterisationRunner.VBPROJ. References the .NET Framework-only assemblies
    // the WINDOWS_GATED bucket depends on; referencing one the class doesn't use is harmless, so a
    // single template covers the whole bucket without per-class dependency detection (that can come
    // later if a class needs an assembly outside this set).
    static final String VBPROJ = """
            <Project Sdk="Microsoft.NET.Sdk">
              <PropertyGroup>
                <TargetFramework>net48</TargetFramework>
                <RootNamespace></RootNamespace>
                <Nullable>disable</Nullable>
              </PropertyGroup>
              <ItemGroup>
                <Reference Include="System.Web" />
                <Reference Include="System.Web.Services" />
                <Reference Include="System.Data.Linq" />
                <Reference Include="System.ServiceModel" />
                <Reference Include="System.EnterpriseServices" />
                <Reference Include="System.Configuration" />
              </ItemGroup>
            </Project>
            """;

    // net48 twin of BASELINE_CSPROJ_TEMPLATE. Same packages + global Using as the Linux template;
    // adds <LangVersion>latest</LangVersion> so the global-using trick works on net48 (see CS8370).
    static final String BASELINE_CSPROJ_TEMPLATE = """
            <Project Sdk="Microsoft.NET.Sdk">
              <PropertyGroup>
                <TargetFramework>net48</TargetFramework>
                <LangVersion>latest</LangVersion>
                <Nullable>disable</Nullable>
                <ImplicitUsings>enable</ImplicitUsings>
                <IsPackable>false</IsPackable>
                <IsTestProject>true</IsTestProject>
              </PropertyGroup>
              <ItemGroup>
                <PackageReference Include="Microsoft.NET.Test.Sdk" Version="17.11.1" />
                <PackageReference Include="MSTest.TestAdapter" Version="3.6.1" />
                <PackageReference Include="MSTest.TestFramework" Version="3.6.1" />
                <PackageReference Include="coverlet.collector" Version="6.0.2" />
              </ItemGroup>
              <ItemGroup>
                <Using Include="Microsoft.VisualStudio.TestTools.UnitTesting" />
              </ItemGroup>
              <ItemGroup>
                <ProjectReference Include="../%1$s.Vb/%1$s.vbproj" />
              </ItemGroup>
            </Project>
            """;

    /** Root under which the generated project is written; the workflow discovers the .csproj here. */
    static final String WORKSPACE = "runner-workspace/";

    private final WindowsRunnerTransport transport;

    public WindowsCharacterisationRunner(WindowsRunnerTransport transport) {
        this.transport = transport;
    }

    @Override
    public BuildResult run(MigrationSession session, String className, TestsResult suite) {
        String sessionId = session.getSessionId();
        Map<String, String> files = projectFiles(className, session.getVbContentForClass(className), suite);
        try {
            String trx = transport.characterise(jobId(className), files);
            TrxParser.Parsed parsed = TrxParser.parse(sessionId, trx);
            session.setFailureMessages(parsed.failureMessages());
            return parsed.result();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error(session, sessionId, "Windows runner interrupted");
        } catch (IOException e) {
            return error(session, sessionId, "Windows runner failed: " + e.getMessage());
        }
    }

    private static BuildResult error(MigrationSession session, String sessionId, String message) {
        session.setFailureMessages(Map.of());
        return new BuildResult(sessionId, BuildStatus.ERROR, 0, 0, 0, List.of(message), List.of());
    }

    /** The repo-relative files that make up the net48 characterisation project for {@code className}. */
    Map<String, String> projectFiles(String className, String vbSource, TestsResult suite) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(WORKSPACE + className + ".Vb/" + className + ".vbproj", VBPROJ);
        files.put(WORKSPACE + className + ".Vb/" + className + ".vb", vbSource == null ? "" : vbSource);
        files.put(WORKSPACE + className + ".Baseline/" + className + ".Baseline.csproj",
                String.format(BASELINE_CSPROJ_TEMPLATE, className));
        files.put(WORKSPACE + className + ".Baseline/" + suite.testClassName() + ".cs", suite.code());
        return files;
    }

    /** A unique, branch-safe job id: the lower-cased class name plus a short random suffix. */
    private static String jobId(String className) {
        String slug = className.toLowerCase().replaceAll("[^a-z0-9]", "");
        return slug + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
