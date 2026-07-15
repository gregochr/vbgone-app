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
    // later if a class needs an assembly outside this set). System.Windows.Forms + System.Drawing are
    // essential, not optional: the bucket is dominated by "pure logic trapped in a WinForms class"
    // (a class inheriting System.Windows.Forms.Form), which won't compile without them (BC30002).
    static final String VBPROJ = """
            <Project Sdk="Microsoft.NET.Sdk">
              <PropertyGroup>
                <TargetFramework>net48</TargetFramework>
                <RootNamespace></RootNamespace>
                <Nullable>disable</Nullable>
                <!-- Full PDB next to the DLL so Microsoft's native Code Coverage engine can instrument
                     the VB assembly (its dynamic instrumentation needs the symbols beside the module). -->
                <DebugType>full</DebugType>
                <DebugSymbols>true</DebugSymbols>
              </PropertyGroup>
              <ItemGroup>
                <Reference Include="System.Windows.Forms" />
                <Reference Include="System.Drawing" />
                <Reference Include="System.Web" />
                <Reference Include="System.Web.Services" />
                <Reference Include="System.Data.Linq" />
                <Reference Include="System.ServiceModel" />
                <Reference Include="System.ServiceModel.Web" />
                <Reference Include="System.EnterpriseServices" />
                <Reference Include="System.Configuration" />
              </ItemGroup>
              <ItemGroup>
                <!-- Project-level VB imports. Legacy classes routinely inherit or reference an
                     UNQUALIFIED type (e.g. `Inherits Form`) that relied on a classic WinForms
                     project's default namespace imports — which SDK-style VB does not add. Referencing
                     the assembly alone isn't enough: without the namespace import, `Form` won't resolve
                     (BC30002 'Type Form is not defined'). These make the WinForms types resolve
                     unqualified, matching how the original project compiled. -->
                <Import Include="System.Windows.Forms" />
                <Import Include="System.Drawing" />
                <!-- LINQ-to-SQL entities reference EntitySet/EntityRef, the mapping attributes and
                     INotifyPropertyChanging/ed UNQUALIFIED, relying on the DataContext project's default
                     imports (BC30002 'EntitySet'/'PropertyChangingEventArgs'/'AutoSync' otherwise). -->
                <Import Include="System.ComponentModel" />
                <Import Include="System.Data.Linq" />
                <Import Include="System.Data.Linq.Mapping" />
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
                <!-- No coverlet: coverage comes from the native Microsoft Code Coverage collector, which
                     flows transitively from Microsoft.NET.Test.Sdk and is far more robust on net48. -->
              </ItemGroup>
              <ItemGroup>
                <Using Include="Microsoft.VisualStudio.TestTools.UnitTesting" />
              </ItemGroup>
              <ItemGroup>
                <!-- net48 does NOT flow the VB ProjectReference's framework <Reference>s transitively,
                     and the AI MSTest suites name framework types FULLY QUALIFIED (e.g.
                     typeof(System.Windows.Forms.Form)) — so this test project must carry the SAME
                     framework references as the VB project it references. Keep the two lists in lockstep.
                     (No <Import> here: that's a VB-only project-level namespace import; the C# tests are
                     fully qualified, so an assembly reference is all they need.) -->
                <Reference Include="System.Windows.Forms" />
                <Reference Include="System.Drawing" />
                <Reference Include="System.Web" />
                <Reference Include="System.Web.Services" />
                <Reference Include="System.Data.Linq" />
                <Reference Include="System.ServiceModel" />
                <Reference Include="System.ServiceModel.Web" />
                <Reference Include="System.EnterpriseServices" />
                <Reference Include="System.Configuration" />
              </ItemGroup>
              <ItemGroup>
                <ProjectReference Include="../%1$s.Vb/%1$s.vbproj" />
              </ItemGroup>
            </Project>
            """;

    // MSTest .runsettings. WINDOWS_GATED is dominated by `Inherits Form` classes; MSTest on net48 runs the
    // test thread MTA by default, and constructing a WinForms control that does OLE work in its constructor
    // (Clipboard, drag-drop, ActiveX/WebBrowser host) throws ThreadStateException on MTA. STA matches how the
    // original app ran and is a no-op for non-OLE forms and for the WCF/LINQ/ASMX buckets. TestSessionTimeout
    // bounds a headless hang (a ctor/method that pops MessageBox.Show / Application.Run) below the 15-min job
    // timeout, so the job fails loudly with a missing .trx (→ BuildResult.ERROR) rather than the hard kill.
    static final String RUNSETTINGS = """
            <?xml version="1.0" encoding="utf-8"?>
            <RunSettings>
              <RunConfiguration>
                <ExecutionThreadApartmentState>STA</ExecutionThreadApartmentState>
                <TestSessionTimeout>600000</TestSessionTimeout>
              </RunConfiguration>
              <DataCollectionRunSettings>
                <DataCollectors>
                  <DataCollector friendlyName="Code Coverage"
                                 uri="datacollector://Microsoft/CodeCoverage/2.0">
                    <Configuration>
                      <Format>Cobertura</Format>
                      <CodeCoverage>
                        <ModulePaths>
                          <Exclude>
                            <!-- Drop the MSTest project (<class>.Baseline.dll) so the report's root
                                 aggregate equals the VB module under test - a casing-proof fallback if
                                 the <package name="..."> match ever fails. Class-agnostic: every
                                 generated test assembly is named *.Baseline.dll. -->
                            <ModulePath>.*\\.Baseline\\.dll$</ModulePath>
                          </Exclude>
                        </ModulePaths>
                      </CodeCoverage>
                    </Configuration>
                  </DataCollector>
                </DataCollectors>
              </DataCollectionRunSettings>
            </RunSettings>
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
            WindowsRunnerTransport.Artifacts artifacts = transport.characterise(jobId(className), files);
            TrxParser.Parsed parsed = TrxParser.parse(sessionId, artifacts.trx());
            session.setFailureMessages(parsed.failureMessages());
            // Coverage of the legacy VB assembly (its module name equals className), when the runner
            // collected it — the same Cobertura report shape the Linux path produces.
            CoverageParser.Coverage cov = CoverageParser.parseXml(artifacts.coverageXml(), className);
            return parsed.result().withCoverage(cov.linePercent(), cov.branchPercent());
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
        // Class-independent, so a single fixed path; the workflow passes it via `dotnet test --settings`.
        files.put(WORKSPACE + "characterisation.runsettings", RUNSETTINGS);
        return files;
    }

    /** A unique, branch-safe job id: the lower-cased class name plus a short random suffix. */
    private static String jobId(String className) {
        String slug = className.toLowerCase().replaceAll("[^a-z0-9]", "");
        return slug + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
