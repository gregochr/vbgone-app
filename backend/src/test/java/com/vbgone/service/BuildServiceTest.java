package com.vbgone.service;

import com.vbgone.model.*;
import com.vbgone.session.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuildServiceTest {

    private static final String GREEN_TRX = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TestRun xmlns="http://microsoft.com/schemas/VisualStudio/TeamTest/2010">
              <ResultSummary outcome="Completed">
                <Counters total="5" executed="5" passed="5" failed="0" error="0" />
              </ResultSummary>
            </TestRun>""";

    private static final String RED_TRX = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TestRun xmlns="http://microsoft.com/schemas/VisualStudio/TeamTest/2010">
              <ResultSummary outcome="Failed">
                <Counters total="5" executed="5" passed="2" failed="3" error="0" />
              </ResultSummary>
            </TestRun>""";

    @Mock
    private SessionStore sessionStore;

    @Mock
    private ProcessRunner processRunner;

    @TempDir
    Path tempDir;

    private BuildService service;

    @BeforeEach
    void setUp() {
        service = new BuildService(sessionStore, tempDir.toString(), processRunner);
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

    private MigrationSession sessionWithImplementation(String sessionId) {
        MigrationSession session = fullSession(sessionId);
        session.setImplementResult(new ImplementResult(
                sessionId, "Form1",
                "public class Form1 : IForm1 { public int Add(int a, int b) => a + b; }",
                ImplementMode.CLAUDE));
        return session;
    }

    private void writeTrxFile(String sessionId, String className, String trxContent) throws IOException {
        Path trxDir = tempDir.resolve(sessionId).resolve(className + ".Tests").resolve("TestResults");
        Files.createDirectories(trxDir);
        Files.writeString(trxDir.resolve("results.trx"), trxContent);
    }

    // ── GREEN scenario ──

    @Test
    void build_greenWhenAllTestsPass() throws Exception {
        MigrationSession session = sessionWithImplementation("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(processRunner.run(anyList())).thenAnswer(inv -> {
            writeTrxFile("s1", "Form1", GREEN_TRX);
            return new ProcessOutput(0, "Passed!", "");
        });

        BuildResult result = service.build("s1");

        assertThat(result.sessionId()).isEqualTo("s1");
        assertThat(result.buildStatus()).isEqualTo(BuildStatus.GREEN);
        assertThat(result.total()).isEqualTo(5);
        assertThat(result.passed()).isEqualTo(5);
        assertThat(result.failed()).isEqualTo(0);
        assertThat(result.errors()).isEmpty();
        assertThat(session.getGreenBuild()).isEqualTo(result);
    }

    // ── RED scenario ──

    @Test
    void build_redWhenTestsFail() throws Exception {
        MigrationSession session = fullSession("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(processRunner.run(anyList())).thenAnswer(inv -> {
            writeTrxFile("s1", "Form1", RED_TRX);
            return new ProcessOutput(1, "", "");
        });

        BuildResult result = service.build("s1");

        assertThat(result.buildStatus()).isEqualTo(BuildStatus.RED);
        assertThat(result.total()).isEqualTo(5);
        assertThat(result.passed()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(3);
        assertThat(result.errors()).isEmpty();
        assertThat(session.getRedBuild()).isEqualTo(result);
    }

    // ── ERROR scenario ──

    @Test
    void build_errorWhenCompilationFails() throws Exception {
        MigrationSession session = fullSession("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(processRunner.run(anyList())).thenReturn(new ProcessOutput(1, "",
                "Form1.cs(5,20): error CS1002: ; expected\nForm1.cs(10,1): error CS1513: } expected\n"));

        BuildResult result = service.build("s1");

        assertThat(result.buildStatus()).isEqualTo(BuildStatus.ERROR);
        assertThat(result.total()).isEqualTo(0);
        assertThat(result.passed()).isEqualTo(0);
        assertThat(result.failed()).isEqualTo(0);
        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors().get(0)).contains("CS1002");
        assertThat(result.errors().get(1)).contains("CS1513");
        assertThat(session.getRedBuild()).isEqualTo(result);
    }

    @Test
    void build_errorWithNoStderrReturnsGenericMessage() throws Exception {
        MigrationSession session = fullSession("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(processRunner.run(anyList())).thenReturn(new ProcessOutput(1, "", ""));

        BuildResult result = service.build("s1");

        assertThat(result.buildStatus()).isEqualTo(BuildStatus.ERROR);
        assertThat(result.errors()).containsExactly("Build failed with no error output");
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

    // ── File writing ──

    @Test
    void build_writesCorrectProjectStructure() throws Exception {
        MigrationSession session = fullSession("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(processRunner.run(anyList())).thenAnswer(inv -> {
            writeTrxFile("s1", "Form1", GREEN_TRX);
            return new ProcessOutput(0, "", "");
        });

        service.build("s1");

        Path sessionDir = tempDir.resolve("s1");
        assertThat(sessionDir.resolve("Form1/Form1.csproj")).exists();
        assertThat(sessionDir.resolve("Form1/IForm1.cs")).exists();
        assertThat(sessionDir.resolve("Form1/Form1.cs")).exists();
        assertThat(sessionDir.resolve("Form1.Tests/Form1.Tests.csproj")).exists();
        assertThat(sessionDir.resolve("Form1.Tests/Form1Tests.cs")).exists();

        String testCsproj = Files.readString(sessionDir.resolve("Form1.Tests/Form1.Tests.csproj"));
        assertThat(testCsproj).contains("../Form1/Form1.csproj");
        assertThat(testCsproj).contains("NUnit");
    }

    @Test
    void build_prefersImplementationOverStub() throws Exception {
        MigrationSession session = sessionWithImplementation("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));
        when(processRunner.run(anyList())).thenAnswer(inv -> {
            writeTrxFile("s1", "Form1", GREEN_TRX);
            return new ProcessOutput(0, "", "");
        });

        service.build("s1");

        String implCode = Files.readString(tempDir.resolve("s1/Form1/Form1.cs"));
        assertThat(implCode).contains("a + b");
        assertThat(implCode).doesNotContain("NotImplementedException");
    }

    // ── parseTrx unit tests ──

    @Test
    void parseTrx_greenWhenNoFailures() {
        BuildResult result = service.parseTrx("s1", GREEN_TRX);

        assertThat(result.buildStatus()).isEqualTo(BuildStatus.GREEN);
        assertThat(result.total()).isEqualTo(5);
        assertThat(result.passed()).isEqualTo(5);
        assertThat(result.failed()).isEqualTo(0);
    }

    @Test
    void parseTrx_redWhenFailuresExist() {
        BuildResult result = service.parseTrx("s1", RED_TRX);

        assertThat(result.buildStatus()).isEqualTo(BuildStatus.RED);
        assertThat(result.total()).isEqualTo(5);
        assertThat(result.passed()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(3);
    }

    @Test
    void parseTrx_throwsOnInvalidXml() {
        assertThatThrownBy(() -> service.parseTrx("s1", "not xml"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse .trx results");
    }

    // ── parseCompilationErrors unit tests ──

    @Test
    void parseCompilationErrors_extractsErrorLines() {
        String stderr = """
                Microsoft (R) Build Engine version 17.11
                Build started.
                Form1.cs(5,20): error CS1002: ; expected
                Some other output
                Form1.cs(10,1): error CS1513: } expected
                Build FAILED.""";

        List<String> errors = service.parseCompilationErrors(stderr);

        assertThat(errors).hasSize(2);
        assertThat(errors.get(0)).contains("CS1002");
        assertThat(errors.get(1)).contains("CS1513");
    }

    @Test
    void parseCompilationErrors_returnsFullStderrWhenNoErrorLines() {
        String stderr = "Unexpected build failure";

        List<String> errors = service.parseCompilationErrors(stderr);

        assertThat(errors).containsExactly("Unexpected build failure");
    }
}
