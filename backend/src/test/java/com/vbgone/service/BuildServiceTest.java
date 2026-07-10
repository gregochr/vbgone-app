package com.vbgone.service;

import com.vbgone.build.BuildRuntime;
import com.vbgone.build.BuildRuntimeRegistry;
import com.vbgone.model.*;
import com.vbgone.session.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuildServiceTest {

    @Mock
    private SessionStore sessionStore;

    private BuildService service;

    /** Records the dependencies it was dispatched with so the dispatch test can assert on them. */
    private static class RecordingRuntime implements BuildRuntime {
        BuildResult toReturn;
        List<String> capturedDependencies;
        InterfaceResult capturedIface;
        TestsResult capturedTests;
        String capturedImpl;

        @Override
        public String id() {
            return "csharp";
        }

        @Override
        public BuildResult build(MigrationSession session, InterfaceResult iface, TestsResult tests,
                                 String implementationCode, List<String> dependencies) {
            this.capturedIface = iface;
            this.capturedTests = tests;
            this.capturedImpl = implementationCode;
            this.capturedDependencies = new ArrayList<>(dependencies);
            return toReturn;
        }
    }

    private RecordingRuntime runtime;

    @BeforeEach
    void setUp() {
        runtime = new RecordingRuntime();
        BuildRuntimeRegistry registry = new BuildRuntimeRegistry(List.of(runtime));
        service = new BuildService(sessionStore, registry);
        // getOrThrow delegates to the (stubbed) get(); let the real method run over the mock.
        lenient().when(sessionStore.getOrThrow(anyString())).thenCallRealMethod();
    }

    private MigrationSession fullSession(String sessionId) {
        MigrationSession session = new MigrationSession(sessionId);
        session.setVbContent("Public Class Form1...");
        session.setInterfaceResult(new InterfaceResult(
                sessionId, "Form1", "IForm1",
                "public interface IForm1 { int Add(int a, int b); }"));
        session.setTestsResult(new TestsResult(
                sessionId, "Form1", "Form1Tests",
                "[TestFixture] public class Form1Tests { [Test] public void Add_Works() {} }",
                1));
        session.setStubResult(new StubResult(
                sessionId, "Form1",
                "public class Form1 : IForm1 { public int Add(int a, int b) => throw new NotImplementedException(); }"));
        return session;
    }

    // ── Dispatch: resolves runtime by session language, sets green/red ──

    @Test
    void build_dispatchesToRuntimeAndSetsGreenBuild() {
        MigrationSession session = fullSession("s1");
        session.setImplementResult(new ImplementResult("s1", "Form1",
                "public class Form1 : IForm1 { public int Add(int a, int b) => a + b; }", ImplementMode.CLAUDE));
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        BuildResult green = new BuildResult("s1", BuildStatus.GREEN, 5, 5, 0, List.of(), List.of());
        runtime.toReturn = green;

        BuildResult result = service.build("s1");

        assertThat(result).isEqualTo(green);
        assertThat(session.getGreenBuild()).isEqualTo(green);
        assertThat(session.getRedBuild()).isNull();
        // Implementation is preferred over the stub when both are present
        assertThat(runtime.capturedImpl).contains("a + b");
        assertThat(runtime.capturedIface).isEqualTo(session.getInterfaceResult());
        assertThat(runtime.capturedTests).isEqualTo(session.getTestsResult());
    }

    @Test
    void build_dispatchesToRuntimeAndSetsRedBuild() {
        MigrationSession session = fullSession("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        BuildResult red = new BuildResult("s1", BuildStatus.RED, 5, 2, 3, List.of(), List.of("Add_Works"));
        runtime.toReturn = red;

        BuildResult result = service.build("s1");

        assertThat(result).isEqualTo(red);
        assertThat(session.getRedBuild()).isEqualTo(red);
        assertThat(session.getGreenBuild()).isNull();
        // Falls back to the stub code when no implementation exists
        assertThat(runtime.capturedImpl).contains("NotImplementedException");
    }

    @Test
    void build_passesResolvedDependenciesToRuntime() {
        MigrationSession session = new MigrationSession("s1");
        session.setVbContent("Public Class Form1...");
        session.setAnalysisResult(new AnalysisResult("s1",
                List.of(new ClassInfo("Form1", List.of("Add"),
                        List.of("System.Windows.Forms.Label", "OrderCalculator"),
                        Complexity.LOW, null, null, null, null)),
                List.of("Form1"), "Test"));
        session.setInterfaceResult(new InterfaceResult("s1", "Form1", "IForm1", "public interface IForm1 { }"));
        session.setTestsResult(new TestsResult("s1", "Form1", "Form1Tests", "...", 1));
        session.setStubResult(new StubResult("s1", "Form1", "public class Form1 : IForm1 { }"));
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        runtime.toReturn = new BuildResult("s1", BuildStatus.RED, 1, 0, 1, List.of(), List.of());

        service.build("s1");

        // Framework deps (dotted) filtered out; user-defined dep passed through
        assertThat(runtime.capturedDependencies).containsExactly("OrderCalculator");
    }

    // ── Precondition tests ──

    @Test
    void build_throwsWhenSessionNotFound() {
        when(sessionStore.get("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.build("bad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session not found");
    }

    @Test
    void build_throwsWhenNoInterface() {
        MigrationSession session = new MigrationSession("s1");
        session.setVbContent("...");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.build("s1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Interface must be generated");
    }

    @Test
    void build_throwsWhenNoTests() {
        MigrationSession session = new MigrationSession("s1");
        session.setInterfaceResult(new InterfaceResult("s1", "Form1", "IForm1", "..."));
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.build("s1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tests must be generated");
    }

    @Test
    void build_throwsWhenNoStubOrImplementation() {
        MigrationSession session = new MigrationSession("s1");
        session.setInterfaceResult(new InterfaceResult("s1", "Form1", "IForm1", "..."));
        session.setTestsResult(new TestsResult("s1", "Form1", "Form1Tests", "...", 1));
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.build("s1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Stub or implementation must exist");
    }

    // ── getDependencies (language-agnostic, stays in BuildService) ──

    @Test
    void getDependencies_filtersOutDottedNames() {
        MigrationSession session = new MigrationSession("s1");
        session.setAnalysisResult(new AnalysisResult("s1",
                List.of(new ClassInfo("Form1", List.of(), List.of(
                        "System.Windows.Forms.Label", "Microsoft.Extensions.Logging.ILogger",
                        "OrderCalculator", "ShippingService"),
                        Complexity.LOW, null, null, null, null)),
                List.of("Form1"), "Test"));

        List<String> deps = service.getDependencies(session, "Form1");

        assertThat(deps).containsExactly("OrderCalculator", "ShippingService");
        assertThat(deps).doesNotContain("System.Windows.Forms.Label");
        assertThat(deps).doesNotContain("Microsoft.Extensions.Logging.ILogger");
    }

    @Test
    void getDependencies_emptyWhenNoAnalysis() {
        MigrationSession session = new MigrationSession("s1");

        assertThat(service.getDependencies(session, "Form1")).isEmpty();
    }
}
