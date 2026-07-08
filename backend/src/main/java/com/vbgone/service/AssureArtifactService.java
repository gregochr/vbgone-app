package com.vbgone.service;

import com.vbgone.model.MigrationSession;
import com.vbgone.model.TestsResult;
import com.vbgone.session.SessionStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Assembles the downloadable artifacts of the Assure baseline-tests step: the real MSTest {@code
 * .cs} suites recorded per assured class, plus a generated {@code .csproj} and {@code README} so
 * the whole set drops into the user's own CI as a runnable MSTest project.
 *
 * <p>The {@code .cs} files are <b>not</b> regenerated here — they are the exact suites that ran
 * green against the untouched VB.NET, retained on the session by {@link AssureService}. This is
 * deliberately server-side: the browser must not re-zip generated text from mock data.
 */
@Service
public class AssureArtifactService {

    static final String CSPROJ_NAME = "VBGone.Assure.Tests.csproj";
    static final String BUNDLE_NAME = "VBGone-Assure-Tests.zip";
    /** Fixed DOS-era timestamp so the assembled zip is byte-for-byte reproducible. */
    private static final long FIXED_ENTRY_TIME = 946684800000L; // 2000-01-01T00:00:00Z

    private final SessionStore sessionStore;

    public AssureArtifactService(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    /**
     * One assured class's MSTest suite — the real generated {@code .cs} that ran green against the
     * original VB.NET. 404 if the session or that class's suite is absent.
     */
    public TestFileArtifact classTestFile(String sessionId, String className) {
        MigrationSession session = session(sessionId);
        TestsResult suite = session.getBaselineSuites().get(className);
        if (suite == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No baseline test suite for class '" + className + "' in session " + sessionId);
        }
        return new TestFileArtifact(testFileName(className), suite.code());
    }

    /**
     * Every assured class's suite assembled into a runnable MSTest project zip
     * ({@code tests/*.cs} + {@code .csproj} + {@code README.md}). 404 if nothing is assured yet.
     */
    public byte[] bundleZip(String sessionId) {
        MigrationSession session = session(sessionId);
        Map<String, TestsResult> suites = session.getBaselineSuites();
        if (suites.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No assured baseline suites to download for session " + sessionId);
        }
        List<String> classNames = new ArrayList<>(suites.keySet());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Map.Entry<String, TestsResult> e : suites.entrySet()) {
                writeEntry(zos, "tests/" + testFileName(e.getKey()), e.getValue().code());
            }
            writeEntry(zos, CSPROJ_NAME, csproj(classNames));
            writeEntry(zos, "README.md", readme(suites));
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to assemble the test-suite bundle", ex);
        }
        return bos.toByteArray();
    }

    public String bundleFileName() {
        return BUNDLE_NAME;
    }

    // ── helpers ──

    private MigrationSession session(String sessionId) {
        return sessionStore.get(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Session not found: " + sessionId));
    }

    static String testFileName(String className) {
        return className + "Tests.cs";
    }

    private void writeEntry(ZipOutputStream zos, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(FIXED_ENTRY_TIME);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    static String csproj(List<String> classNames) {
        return String.join("\n",
                "<Project Sdk=\"Microsoft.NET.Sdk\">",
                "  <PropertyGroup>",
                "    <TargetFramework>net8.0</TargetFramework>",
                "    <IsPackable>false</IsPackable>",
                "  </PropertyGroup>",
                "  <ItemGroup>",
                "    <PackageReference Include=\"Microsoft.NET.Test.Sdk\" Version=\"17.10.0\" />",
                "    <PackageReference Include=\"MSTest.TestAdapter\" Version=\"3.4.3\" />",
                "    <PackageReference Include=\"MSTest.TestFramework\" Version=\"3.4.3\" />",
                "  </ItemGroup>",
                "  <!-- " + classNames.size() + " assured class(es): "
                        + String.join(", ", classNames) + " -->",
                "  <!-- Add a reference to your original VB.NET project here. -->",
                "</Project>",
                "");
    }

    static String readme(Map<String, TestsResult> suites) {
        List<String> fileLines = new ArrayList<>();
        for (Map.Entry<String, TestsResult> e : suites.entrySet()) {
            int count = e.getValue().testCount();
            fileLines.add("- `" + testFileName(e.getKey()) + "` — " + count
                    + " baseline test" + (count == 1 ? "" : "s"));
        }
        return String.join("\n",
                "# VBGone Assure — baseline test suite",
                "",
                "Generated " + LocalDate.now() + ". " + suites.size() + " class(es) assured.",
                "",
                "These MSTest tests pin the **current** behaviour of your untouched VB.NET.",
                "A green run means behaviour is unchanged after you patch a dependency — it does",
                "**not** mean the behaviour is correct. Known bugs are captured on purpose.",
                "",
                "## Files",
                String.join("\n", fileLines),
                "",
                "## Run",
                "```",
                "dotnet test " + CSPROJ_NAME,
                "```",
                "");
    }

    /** A single downloadable test file: its download name and its C# content. */
    public record TestFileArtifact(String filename, String content) {}
}
