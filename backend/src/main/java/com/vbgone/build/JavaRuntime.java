package com.vbgone.build;

import com.vbgone.lang.LanguageConventions;
import com.vbgone.lang.LanguageConventionsRegistry;
import com.vbgone.model.*;
import com.vbgone.service.ProcessOutput;
import com.vbgone.service.ProcessRunner;
import com.vbgone.session.SessionStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Java / JUnit 5 build runtime. Scaffolds a single-module Maven project under the
 * shared workspace volume, runs {@code mvn test} in the Maven/JDK sidecar, and
 * parses the resulting Surefire XML reports.
 */
@Service
public class JavaRuntime extends AbstractBuildRuntime {

    private static final String PACKAGE = "com.vbgone.generated";

    /** Minimal Maven project descriptor — shared with the GitHub PR path so Java PRs are buildable. */
    public static final String POM = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.vbgone</groupId>
              <artifactId>generated</artifactId>
              <version>1.0.0</version>
              <packaging>jar</packaging>
              <properties>
                <maven.compiler.release>21</maven.compiler.release>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
              </properties>
              <dependencies>
                <dependency>
                  <groupId>org.junit.jupiter</groupId>
                  <artifactId>junit-jupiter</artifactId>
                  <version>5.11.3</version>
                  <scope>test</scope>
                </dependency>
              </dependencies>
              <build>
                <plugins>
                  <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>3.5.2</version>
                  </plugin>
                </plugins>
              </build>
            </project>
            """;

    private final SessionStore sessionStore;
    private final String javaRunnerContainer;
    private final ProcessRunner processRunner;
    private final LanguageConventionsRegistry conventions;

    public JavaRuntime(SessionStore sessionStore,
                       @Value("${vbgone.workspace:/workspace}") String workspacePath,
                       @Value("${java.runner.container:vbgone-app-jdk-maven-runner-1}") String javaRunnerContainer,
                       ProcessRunner processRunner,
                       LanguageConventionsRegistry conventions) {
        super(Path.of(workspacePath));
        this.sessionStore = sessionStore;
        this.javaRunnerContainer = javaRunnerContainer;
        this.processRunner = processRunner;
        this.conventions = conventions;
    }

    @Override
    public String id() {
        return "java";
    }

    @Override
    void writeProjectFiles(Path sessionDir, String className,
                           InterfaceResult iface, TestsResult tests,
                           String implementationCode, List<String> dependencies) throws IOException {
        LanguageConventions conv = conventions.forLanguage("java");

        String packageDir = PACKAGE.replace('.', '/');
        Path mainSrc = sessionDir.resolve("src/main/java").resolve(packageDir);
        Path testSrc = sessionDir.resolve("src/test/java").resolve(packageDir);
        Files.createDirectories(mainSrc);
        Files.createDirectories(testSrc);

        String interfaceName = conv.interfaceName(className);   // == className for Java
        String implName = iface.implName();                     // className + "Impl"
        String testClassName = conv.testClassName(className);   // className + "Test"

        // Clean only THIS class's own previously-generated files, so re-running it can't leave a
        // stale duplicate — while sibling classes migrated into the same Java module survive. This
        // mirrors the C# path (DotNetRuntime), which cleans only the class's own dirs; a whole-module
        // wipe here would erase every other class in a multi-class Java session.
        Files.deleteIfExists(mainSrc.resolve(interfaceName + ".java"));
        Files.deleteIfExists(mainSrc.resolve(implName + ".java"));
        Files.deleteIfExists(testSrc.resolve(testClassName + ".java"));

        Files.writeString(sessionDir.resolve("pom.xml"), POM);

        Files.writeString(mainSrc.resolve(interfaceName + ".java"), iface.code());
        Files.writeString(mainSrc.resolve(implName + ".java"), implementationCode);
        Files.writeString(testSrc.resolve(testClassName + ".java"), tests.code());

        // Stub each user-defined dependency so the generated code compiles.
        for (String dep : dependencies) {
            String depInterface = "package " + PACKAGE + ";\n\npublic interface " + dep + " { }\n";
            Files.writeString(mainSrc.resolve(dep + ".java"), depInterface);
            String depImpl = "package " + PACKAGE + ";\n\npublic class " + dep + "Impl implements " + dep + " { }\n";
            Files.writeString(mainSrc.resolve(dep + "Impl.java"), depImpl);
        }
    }

    @Override
    ProcessOutput runTests(String sessionId, String className)
            throws IOException, InterruptedException {
        return processRunner.run(List.of(
                "docker", "exec", javaRunnerContainer,
                "mvn", "-q", "-f", "/workspace/" + sessionId + "/pom.xml", "test"
        ));
    }

    @Override
    boolean hasResults(Path sessionDir, String className) throws IOException {
        Path reportsDir = sessionDir.resolve("target").resolve("surefire-reports");
        if (!Files.isDirectory(reportsDir)) {
            return false;
        }
        try (var stream = Files.newDirectoryStream(reportsDir, "TEST-*.xml")) {
            return stream.iterator().hasNext();
        }
    }

    @Override
    BuildResult parseResults(String sessionId, Path sessionDir, String className) {
        return parseSurefire(sessionId, sessionDir.resolve("target").resolve("surefire-reports"));
    }

    @Override
    List<String> parseErrors(String sessionId, ProcessOutput output) {
        return parseJavaCompilationErrors(output.stderr(), output.stdout(), sessionId);
    }

    BuildResult parseSurefire(String sessionId, Path reportsDir) {
        try {
            int total = 0;
            int failed = 0;
            int skipped = 0;
            List<String> failedTests = new java.util.ArrayList<>();
            java.util.Map<String, String> failureMessages = new java.util.LinkedHashMap<>();

            var builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

            List<Path> reportFiles = new java.util.ArrayList<>();
            if (Files.isDirectory(reportsDir)) {
                try (var stream = Files.newDirectoryStream(reportsDir, "TEST-*.xml")) {
                    stream.forEach(reportFiles::add);
                }
            }

            for (Path reportFile : reportFiles) {
                var doc = builder.parse(reportFile.toFile());
                var suite = (org.w3c.dom.Element) doc.getElementsByTagName("testsuite").item(0);
                if (suite == null) continue;

                total += parseIntAttr(suite, "tests");
                failed += parseIntAttr(suite, "failures") + parseIntAttr(suite, "errors");
                skipped += parseIntAttr(suite, "skipped");

                var cases = doc.getElementsByTagName("testcase");
                for (int i = 0; i < cases.getLength(); i++) {
                    var testcase = (org.w3c.dom.Element) cases.item(i);
                    org.w3c.dom.Element problem = childElement(testcase, "failure");
                    if (problem == null) problem = childElement(testcase, "error");
                    if (problem != null) {
                        String testName = testcase.getAttribute("name");
                        failedTests.add(testName);
                        String message = problem.getAttribute("message");
                        if (message == null || message.isBlank()) {
                            message = problem.getTextContent().trim();
                        }
                        failureMessages.put(testName, message);
                    }
                }
            }

            int passed = total - failed - skipped;

            // Store failure messages on session for retry prompts
            sessionStore.get(sessionId).ifPresent(s -> s.setFailureMessages(failureMessages));

            BuildStatus status = (failed == 0) ? BuildStatus.GREEN : BuildStatus.RED;
            return new BuildResult(sessionId, status, total, passed, failed, List.of(), failedTests);

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Surefire results: " + e.getMessage(), e);
        }
    }

    private static int parseIntAttr(org.w3c.dom.Element el, String name) {
        String v = el.getAttribute(name);
        return (v == null || v.isBlank()) ? 0 : Integer.parseInt(v.trim());
    }

    /** First direct child element of the given tag name, or null. */
    private static org.w3c.dom.Element childElement(org.w3c.dom.Element parent, String tag) {
        var nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() > 0 ? (org.w3c.dom.Element) nodes.item(0) : null;
    }

    List<String> parseJavaCompilationErrors(String stderr, String stdout, String sessionId) {
        String combined = ((stderr != null ? stderr : "") + "\n" + (stdout != null ? stdout : "")).trim();
        if (combined.isBlank()) {
            return List.of("Build failed with no error output");
        }
        String prefix = "/workspace/" + sessionId + "/";
        List<String> errors = Arrays.stream(combined.split("\n"))
                .map(String::trim)
                .filter(line -> line.contains("[ERROR]") && line.contains(".java:"))
                .map(line -> line.replace(prefix, ""))
                .toList();
        return errors.isEmpty() ? List.of(combined.substring(0, Math.min(combined.length(), 500))) : errors;
    }
}
