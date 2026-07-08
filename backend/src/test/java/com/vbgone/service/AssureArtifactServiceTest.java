package com.vbgone.service;

import com.vbgone.model.MigrationSession;
import com.vbgone.model.TestsResult;
import com.vbgone.session.SessionStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssureArtifactServiceTest {

    private final SessionStore sessionStore = new SessionStore();
    private final AssureArtifactService service = new AssureArtifactService(sessionStore);

    private static TestsResult suite(String sid, String className, String code, int count) {
        return new TestsResult(sid, className, className + "Baseline", code, count);
    }

    @Test
    void classTestFile_returnsRealStoredSuite() {
        MigrationSession s = sessionStore.create();
        String code = "[TestClass] public class OrderServiceBaseline { /* real */ }";
        s.putBaselineSuite("OrderService", suite(s.getSessionId(), "OrderService", code, 5));

        AssureArtifactService.TestFileArtifact artifact =
                service.classTestFile(s.getSessionId(), "OrderService");

        assertThat(artifact.filename()).isEqualTo("OrderServiceTests.cs");
        assertThat(artifact.content()).isEqualTo(code);
    }

    @Test
    void classTestFile_unknownSession_is404() {
        assertThatThrownBy(() -> service.classTestFile("nope", "OrderService"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void classTestFile_unassuredClass_is404() {
        MigrationSession s = sessionStore.create();
        s.putBaselineSuite("OrderService", suite(s.getSessionId(), "OrderService", "// code", 1));
        // A class that exists but was never assured has no suite → not downloadable.
        assertThatThrownBy(() -> service.classTestFile(s.getSessionId(), "InvoiceService"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void bundleZip_assemblesEveryClassPlusProjectAndReadme() throws Exception {
        MigrationSession s = sessionStore.create();
        String sid = s.getSessionId();
        String orderCode = "[TestClass] public class OrderServiceBaseline { }";
        String invoiceCode = "[TestClass] public class InvoiceServiceBaseline { }";
        s.putBaselineSuite("OrderService", suite(sid, "OrderService", orderCode, 3));
        s.putBaselineSuite("InvoiceService", suite(sid, "InvoiceService", invoiceCode, 1));

        Map<String, String> entries = unzip(service.bundleZip(sid));

        // One real .cs per assured class, under tests/, with the exact stored code.
        assertThat(entries).containsKeys(
                "tests/OrderServiceTests.cs",
                "tests/InvoiceServiceTests.cs",
                "VBGone.Assure.Tests.csproj",
                "README.md");
        assertThat(entries.get("tests/OrderServiceTests.cs")).isEqualTo(orderCode);
        assertThat(entries.get("tests/InvoiceServiceTests.cs")).isEqualTo(invoiceCode);

        // csproj: net8.0 MSTest project listing the assured classes.
        String csproj = entries.get("VBGone.Assure.Tests.csproj");
        assertThat(csproj)
                .contains("<TargetFramework>net8.0</TargetFramework>")
                .contains("MSTest.TestAdapter")
                .contains("2 assured class(es): OrderService, InvoiceService");

        // README: counts (singular/plural) + run instructions.
        String readme = entries.get("README.md");
        assertThat(readme)
                .contains("# VBGone Assure — baseline test suite")
                .contains("2 class(es) assured.")
                .contains("- `OrderServiceTests.cs` — 3 baseline tests")
                .contains("- `InvoiceServiceTests.cs` — 1 baseline test")
                .contains("dotnet test VBGone.Assure.Tests.csproj");
    }

    @Test
    void bundleZip_deterministicAcrossBuilds() {
        MigrationSession s = sessionStore.create();
        s.putBaselineSuite("OrderService", suite(s.getSessionId(), "OrderService", "// x", 1));

        assertThat(service.bundleZip(s.getSessionId()))
                .isEqualTo(service.bundleZip(s.getSessionId()));
    }

    @Test
    void bundleZip_noAssuredClasses_is404() {
        MigrationSession s = sessionStore.create(); // no suites
        assertThatThrownBy(() -> service.bundleZip(s.getSessionId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void bundleZip_unknownSession_is404() {
        assertThatThrownBy(() -> service.bundleZip("nope"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static Map<String, String> unzip(byte[] zip) throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                zis.transferTo(bos);
                out.put(entry.getName(), bos.toString(StandardCharsets.UTF_8));
            }
        }
        return out;
    }
}
