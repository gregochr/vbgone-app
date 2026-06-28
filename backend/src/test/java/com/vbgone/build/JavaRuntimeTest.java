package com.vbgone.build;

import com.vbgone.lang.CSharpConventions;
import com.vbgone.lang.JavaConventions;
import com.vbgone.lang.LanguageConventionsRegistry;
import com.vbgone.model.*;
import com.vbgone.service.ProcessOutput;
import com.vbgone.service.ProcessRunner;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JavaRuntimeTest {

    private static final String GREEN_REPORT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="com.vbgone.generated.Form1Test" tests="5" failures="0" errors="0" skipped="0">
              <testcase name="add_positive" classname="com.vbgone.generated.Form1Test"/>
              <testcase name="add_negative" classname="com.vbgone.generated.Form1Test"/>
              <testcase name="subtract" classname="com.vbgone.generated.Form1Test"/>
              <testcase name="multiply" classname="com.vbgone.generated.Form1Test"/>
              <testcase name="divide" classname="com.vbgone.generated.Form1Test"/>
            </testsuite>""";

    private static final String RED_REPORT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="com.vbgone.generated.Form1Test" tests="5" failures="2" errors="1" skipped="0">
              <testcase name="add_positive" classname="com.vbgone.generated.Form1Test"/>
              <testcase name="subtract" classname="com.vbgone.generated.Form1Test">
                <failure message="expected: &lt;7&gt; but was: &lt;6&gt;">org.opentest4j.AssertionFailedError</failure>
              </testcase>
              <testcase name="multiply" classname="com.vbgone.generated.Form1Test">
                <failure message="expected: &lt;20&gt; but was: &lt;0&gt;">org.opentest4j.AssertionFailedError</failure>
              </testcase>
              <testcase name="divide_by_zero" classname="com.vbgone.generated.Form1Test">
                <error message="unexpected null">java.lang.NullPointerException</error>
              </testcase>
              <testcase name="divide" classname="com.vbgone.generated.Form1Test"/>
            </testsuite>""";

    @Mock
    private SessionStore sessionStore;

    @Mock
    private ProcessRunner processRunner;

    @TempDir
    Path tempDir;

    private JavaRuntime runtime;

    @BeforeEach
    void setUp() {
        LanguageConventionsRegistry conventions =
                new LanguageConventionsRegistry(List.of(new CSharpConventions(), new JavaConventions()));
        runtime = new JavaRuntime(sessionStore, tempDir.toString(), "jdk-maven-runner",
                processRunner, conventions);
    }

    private MigrationSession javaSession(String sessionId) {
        MigrationSession session = new MigrationSession(sessionId);
        session.setTargetLanguage("java");
        session.setVbContent("Public Class Form1...");
        // Java conventions: interface == className, impl == className + "Impl"
        session.setInterfaceResult(new InterfaceResult(
                sessionId, "Form1", "Form1",
                "package com.vbgone.generated;\n\npublic interface Form1 { int add(int a, int b); }",
                "Form1Impl"));
        session.setTestsResult(new TestsResult(
                sessionId, "Form1", "Form1Test",
                "package com.vbgone.generated;\n\nclass Form1Test { }", 1));
        session.setStubResult(new StubResult(
                sessionId, "Form1",
                "package com.vbgone.generated;\n\npublic class Form1Impl implements Form1 { "
                        + "public int add(int a, int b) { throw new UnsupportedOperationException(); } }"));
        return session;
    }

    private BuildResult build(MigrationSession session, List<String> dependencies) {
        String implCode = session.getImplementResult() != null
                ? session.getImplementResult().code()
                : session.getStubResult().code();
        return runtime.build(session, session.getInterfaceResult(), session.getTestsResult(), implCode, dependencies);
    }

    private void writeReport(String sessionId, String fileName, String content) throws IOException {
        Path reportsDir = tempDir.resolve(sessionId).resolve("target").resolve("surefire-reports");
        Files.createDirectories(reportsDir);
        Files.writeString(reportsDir.resolve(fileName), content);
    }

    // ── Scaffold ──

    @Test
    void build_scaffoldsMavenProjectWithConventionNames() throws Exception {
        MigrationSession session = javaSession("s1");
        when(processRunner.run(anyList())).thenAnswer(inv -> {
            writeReport("s1", "TEST-com.vbgone.generated.Form1Test.xml", GREEN_REPORT);
            return new ProcessOutput(0, "BUILD SUCCESS", "");
        });

        build(session, List.of());

        Path sessionDir = tempDir.resolve("s1");
        Path pkg = sessionDir.resolve("src/main/java/com/vbgone/generated");
        Path testPkg = sessionDir.resolve("src/test/java/com/vbgone/generated");

        // The three files at the right Maven paths with convention names
        assertThat(pkg.resolve("Form1.java")).exists();        // interface (no I prefix)
        assertThat(pkg.resolve("Form1Impl.java")).exists();    // impl (Impl suffix)
        assertThat(testPkg.resolve("Form1Test.java")).exists(); // test (Test suffix)

        String pom = Files.readString(sessionDir.resolve("pom.xml"));
        assertThat(pom).contains("junit-jupiter");
        assertThat(pom).contains("maven-surefire-plugin");
        assertThat(pom).contains("<maven.compiler.release>21</maven.compiler.release>");
    }

    @Test
    void build_stubsDependencies() throws Exception {
        MigrationSession session = javaSession("s1");
        when(processRunner.run(anyList())).thenAnswer(inv -> {
            writeReport("s1", "TEST-com.vbgone.generated.Form1Test.xml", GREEN_REPORT);
            return new ProcessOutput(0, "", "");
        });

        build(session, List.of("OrderCalculator"));

        Path pkg = tempDir.resolve("s1/src/main/java/com/vbgone/generated");
        assertThat(pkg.resolve("OrderCalculator.java")).exists();
        assertThat(pkg.resolve("OrderCalculatorImpl.java")).exists();
        assertThat(Files.readString(pkg.resolve("OrderCalculator.java"))).contains("interface OrderCalculator");
        assertThat(Files.readString(pkg.resolve("OrderCalculatorImpl.java")))
                .contains("class OrderCalculatorImpl implements OrderCalculator");
    }

    // ── parseSurefire ──

    @Test
    void parseSurefire_greenWhenNoFailures() throws Exception {
        writeReport("s1", "TEST-com.vbgone.generated.Form1Test.xml", GREEN_REPORT);
        Path reportsDir = tempDir.resolve("s1/target/surefire-reports");

        BuildResult result = runtime.parseSurefire("s1", reportsDir);

        assertThat(result.buildStatus()).isEqualTo(BuildStatus.GREEN);
        assertThat(result.total()).isEqualTo(5);
        assertThat(result.passed()).isEqualTo(5);
        assertThat(result.failed()).isEqualTo(0);
        assertThat(result.failedTests()).isEmpty();
    }

    @Test
    void parseSurefire_redCountsFailuresAndErrors() throws Exception {
        writeReport("s1", "TEST-com.vbgone.generated.Form1Test.xml", RED_REPORT);
        Path reportsDir = tempDir.resolve("s1/target/surefire-reports");

        BuildResult result = runtime.parseSurefire("s1", reportsDir);

        assertThat(result.buildStatus()).isEqualTo(BuildStatus.RED);
        assertThat(result.total()).isEqualTo(5);
        assertThat(result.failed()).isEqualTo(3); // 2 failures + 1 error
        assertThat(result.passed()).isEqualTo(2);
    }

    @Test
    void parseSurefire_extractsFailedTestNames() throws Exception {
        writeReport("s1", "TEST-com.vbgone.generated.Form1Test.xml", RED_REPORT);
        Path reportsDir = tempDir.resolve("s1/target/surefire-reports");

        BuildResult result = runtime.parseSurefire("s1", reportsDir);

        assertThat(result.failedTests()).containsExactly("subtract", "multiply", "divide_by_zero");
    }

    @Test
    void parseSurefire_storesFailureMessagesOnSession() throws Exception {
        MigrationSession session = new MigrationSession("s1");
        when(sessionStore.get("s1")).thenReturn(java.util.Optional.of(session));
        writeReport("s1", "TEST-com.vbgone.generated.Form1Test.xml", RED_REPORT);
        Path reportsDir = tempDir.resolve("s1/target/surefire-reports");

        runtime.parseSurefire("s1", reportsDir);

        assertThat(session.getFailureMessages())
                .containsKeys("subtract", "multiply", "divide_by_zero");
        assertThat(session.getFailureMessages().get("subtract")).contains("expected");
        assertThat(session.getFailureMessages().get("divide_by_zero")).contains("unexpected null");
    }

    @Test
    void parseSurefire_aggregatesAcrossMultipleSuites() throws Exception {
        String suiteA = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="A" tests="3" failures="1" errors="0" skipped="0">
                  <testcase name="a1"/>
                  <testcase name="a2"><failure message="boom">x</failure></testcase>
                  <testcase name="a3"/>
                </testsuite>""";
        String suiteB = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="B" tests="2" failures="0" errors="0" skipped="0">
                  <testcase name="b1"/>
                  <testcase name="b2"/>
                </testsuite>""";
        writeReport("s1", "TEST-A.xml", suiteA);
        writeReport("s1", "TEST-B.xml", suiteB);
        Path reportsDir = tempDir.resolve("s1/target/surefire-reports");

        BuildResult result = runtime.parseSurefire("s1", reportsDir);

        assertThat(result.total()).isEqualTo(5);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.passed()).isEqualTo(4);
        assertThat(result.failedTests()).containsExactly("a2");
    }

    @Test
    void parseSurefire_throwsOnInvalidXml() throws Exception {
        writeReport("s1", "TEST-bad.xml", "not xml");
        Path reportsDir = tempDir.resolve("s1/target/surefire-reports");

        assertThatThrownBy(() -> runtime.parseSurefire("s1", reportsDir))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse Surefire results");
    }

    // ── parseJavaCompilationErrors ──

    @Test
    void parseJavaCompilationErrors_filtersErrorLinesAndStripsPrefix() {
        String stdout = """
                [INFO] Scanning for projects...
                [INFO] Building generated 1.0.0
                [ERROR] /workspace/s1/src/main/java/com/vbgone/generated/Form1Impl.java:[12,9] cannot find symbol
                [INFO] some info line
                [ERROR] /workspace/s1/src/main/java/com/vbgone/generated/Form1Impl.java:[18,5] ';' expected
                [ERROR] BUILD FAILURE""";

        List<String> errors = runtime.parseJavaCompilationErrors("", stdout, "s1");

        assertThat(errors).hasSize(2);
        assertThat(errors.get(0)).contains("cannot find symbol");
        assertThat(errors.get(0)).doesNotContain("/workspace/s1/");
        assertThat(errors.get(0)).contains("src/main/java/com/vbgone/generated/Form1Impl.java");
        assertThat(errors.get(1)).contains("';' expected");
    }

    @Test
    void parseJavaCompilationErrors_emptyOutputFallback() {
        List<String> errors = runtime.parseJavaCompilationErrors("", "", "s1");
        assertThat(errors).containsExactly("Build failed with no error output");
    }

    @Test
    void parseJavaCompilationErrors_returnsTruncatedOutputWhenNoErrorLines() {
        List<String> errors = runtime.parseJavaCompilationErrors("Unexpected maven failure", "", "s1");
        assertThat(errors).containsExactly("Unexpected maven failure");
    }

    // ── build dispatch (exit-code + report presence) ──

    @Test
    void build_returnsErrorWhenMavenFailsWithNoReports() throws Exception {
        MigrationSession session = javaSession("s1");
        when(processRunner.run(anyList())).thenReturn(new ProcessOutput(1, """
                [ERROR] /workspace/s1/src/main/java/com/vbgone/generated/Form1Impl.java:[5,1] error
                """, ""));

        BuildResult result = build(session, List.of());

        assertThat(result.buildStatus()).isEqualTo(BuildStatus.ERROR);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).doesNotContain("/workspace/s1/");
    }

    @Test
    void build_parsesReportsEvenWhenMavenExitsNonZero() throws Exception {
        // mvn test exits non-zero on test failures, but reports exist → RED, not ERROR.
        MigrationSession session = javaSession("s1");
        when(processRunner.run(anyList())).thenAnswer(inv -> {
            writeReport("s1", "TEST-com.vbgone.generated.Form1Test.xml", RED_REPORT);
            return new ProcessOutput(1, "BUILD FAILURE", "");
        });

        BuildResult result = build(session, List.of());

        assertThat(result.buildStatus()).isEqualTo(BuildStatus.RED);
        assertThat(result.failed()).isEqualTo(3);
    }
}
