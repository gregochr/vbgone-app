package com.vbgone.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vbgone.config.RateLimitFilter;
import com.vbgone.model.*;
import com.vbgone.service.AssureArtifactService;
import com.vbgone.service.AssureAssessmentService;
import com.vbgone.service.AssureService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = AssureController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class))
class AssureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AssureService assureService;

    @MockitoBean
    private AssureAssessmentService assessmentService;

    @MockitoBean
    private com.vbgone.service.ZipExtractorService zipExtractorService;

    @MockitoBean
    private com.vbgone.service.RepoIngestService repoIngestService;

    @MockitoBean
    private AssureArtifactService artifactService;

    private static final String SESSION_ID = "s-assure";

    @Test
    void assess_returns200WithReadinessReport() throws Exception {
        ReadinessReport report = new ReadinessReport(
                SESSION_ID,
                new ReadinessReport.ReadinessTotals(2, 5, 1, 0, 1, 3, 0, 2),
                "static",
                List.of(new ClassReadiness("OrderProcessor", "OrderProcessor.vb", Bucket.NET_READY,
                        "public, no WinForms references",
                        List.of(new MethodReadiness("CalculateTotal", "public", Bucket.NET_READY,
                                "params in, value out")))),
                List.of());
        when(assessmentService.assess(eq("OrderProcessor.vb"), anyString())).thenReturn(report);

        mockMvc.perform(post("/api/assure/assess")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AssessRequest("OrderProcessor.vb", "Public Class OrderProcessor..."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confidence").value("static"))
                .andExpect(jsonPath("$.totals.netReady").value(1))
                .andExpect(jsonPath("$.classes[0].bucket").value("net-ready"))
                .andExpect(jsonPath("$.classes[0].methods[0].bucket").value("net-ready"));
    }

    @Test
    void ingestRepo_returns200WithReadinessReport() throws Exception {
        ZipManifest manifest = new ZipManifest(SESSION_ID,
                List.of(new VbSourceFile("Services/OrderService.vb", "OrderService.vb",
                        "Public Class OrderService")), 1);
        ReadinessReport report = new ReadinessReport(
                SESSION_ID,
                new ReadinessReport.ReadinessTotals(2, 5, 1, 0, 1, 3, 0, 2),
                "static",
                List.of(new ClassReadiness("OrderService", "Services/OrderService.vb", Bucket.NET_READY,
                        "public, no WinForms references",
                        List.of(new MethodReadiness("CalculateTotal", "public", Bucket.NET_READY,
                                "params in, value out")))),
                List.of());
        when(repoIngestService.ingest(any(IngestRepoRequest.class))).thenReturn(manifest);
        when(assessmentService.assessProject(manifest)).thenReturn(report);

        mockMvc.perform(post("/api/assure/ingest-repo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new IngestRepoRequest("https://github.com/org/legacy-app"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confidence").value("static"))
                .andExpect(jsonPath("$.totals.netReady").value(1))
                .andExpect(jsonPath("$.classes[0].file").value("Services/OrderService.vb"))
                .andExpect(jsonPath("$.classes[0].bucket").value("net-ready"));
    }

    @Test
    void ingestRepo_invalidUrl_maps400WithErrorBody() throws Exception {
        when(repoIngestService.ingest(any(IngestRepoRequest.class)))
                .thenThrow(new IllegalArgumentException(
                        "Only github.com repositories are supported right now."));

        mockMvc.perform(post("/api/assure/ingest-repo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new IngestRepoRequest("https://gitlab.com/org/legacy-app"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Only github.com repositories are supported right now."));
    }

    @Test
    void baseline_returns200WithPinnedSurface() throws Exception {
        when(assureService.generateBaseline(eq(SESSION_ID), eq("OrderProcessor"), any(), any(), any()))
                .thenReturn(new BaselineResult(SESSION_ID, "OrderProcessor",
                        "OrderProcessor.dll · public surface",
                        List.of(
                                new BaselineMember("decimal CalculateTotal(IReadOnlyList<LineItem> items)", null),
                                new BaselineMember("decimal SplitPerHead(decimal total, int headcount)",
                                        "throws DivideByZeroException when headcount = 0"))));

        mockMvc.perform(post("/api/assure/baseline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ClassRequest(SESSION_ID, "OrderProcessor"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.surfaceFile").value("OrderProcessor.dll · public surface"))
                .andExpect(jsonPath("$.members[1].defect").value("throws DivideByZeroException when headcount = 0"));
    }

    @Test
    void baselineTests_returns200WithNetResult() throws Exception {
        BuildResult build = new BuildResult(SESSION_ID, BuildStatus.GREEN, 43, 43, 0, List.of(), List.of());
        when(assureService.runBaselineTests(eq(SESSION_ID), eq("OrderProcessor"), any(), any(), any()))
                .thenReturn(new BaselineTestsResult(SESSION_ID, "OrderProcessor", "OrderProcessorBaselineTests",
                        "[TestClass] public class OrderProcessorBaselineTests {}", 43, true, build, List.of()));

        mockMvc.perform(post("/api/assure/baseline-tests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ClassRequest(SESSION_ID, "OrderProcessor"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netFaithful").value(true))
                .andExpect(jsonPath("$.testCount").value(43))
                .andExpect(jsonPath("$.build.buildStatus").value("GREEN"));
    }

    @Test
    void augmentBaselineTests_returns200WithTheExtendedNet() throws Exception {
        BuildResult build = new BuildResult(SESSION_ID, BuildStatus.GREEN, 60, 60, 0, List.of(), List.of(),
                88.0, 90.0);
        when(assureService.augmentBaselineTests(eq(SESSION_ID), eq("OrderProcessor"), anyString(),
                any(), any(), any(), any()))
                .thenReturn(new BaselineTestsResult(SESSION_ID, "OrderProcessor", "OrderProcessorBaselineTests",
                        "[TestClass] public class OrderProcessorBaselineTests {}", 60, true, build, List.of()));

        mockMvc.perform(post("/api/assure/augment-baseline-tests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AugmentBaselineRequest(
                                SESSION_ID, "OrderProcessor", "current suite", 45.5, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netFaithful").value(true))
                .andExpect(jsonPath("$.testCount").value(60))
                .andExpect(jsonPath("$.build.coveragePercent").value(88.0));
    }

    @Test
    void rerunBaselineTests_returns200WithFailingAssertions() throws Exception {
        BuildResult build = new BuildResult(SESSION_ID, BuildStatus.RED, 43, 41, 2, List.of(),
                List.of("ApplyDiscount_UnknownCode_ReturnsSubtotalUnchanged"));
        when(assureService.rerunBaselineTests(eq(SESSION_ID), eq("OrderProcessor"), anyString()))
                .thenReturn(new BaselineTestsResult(SESSION_ID, "OrderProcessor", "OrderProcessorBaselineTests",
                        "[TestClass] public class OrderProcessorBaselineTests {}", 43, false, build,
                        List.of(new TestFailure("ApplyDiscount_UnknownCode_ReturnsSubtotalUnchanged",
                                "Expected: 100 but was: 90"))));

        mockMvc.perform(post("/api/assure/rerun-baseline-tests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new BaselineRerunRequest(SESSION_ID, "OrderProcessor", "edited code"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netFaithful").value(false))
                .andExpect(jsonPath("$.failures[0].name")
                        .value("ApplyDiscount_UnknownCode_ReturnsSubtotalUnchanged"))
                .andExpect(jsonPath("$.failures[0].message").value("Expected: 100 but was: 90"));
    }

    @Test
    void quarantineBaseline_returns200WithGreenSuite() throws Exception {
        BuildResult build = new BuildResult(SESSION_ID, BuildStatus.GREEN, 42, 42, 0, List.of(), List.of());
        when(assureService.quarantineBaseline(eq(SESSION_ID), eq("OrderProcessor"), anyString(),
                eq(List.of("Bad"))))
                .thenReturn(new BaselineTestsResult(SESSION_ID, "OrderProcessor", "OrderProcessorBaselineTests",
                        "[TestClass] public class OrderProcessorBaselineTests { [Ignore(\"quarantined\")] }",
                        42, true, build, List.of()));

        mockMvc.perform(post("/api/assure/quarantine-baseline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QuarantineRequest(
                                SESSION_ID, "OrderProcessor", "code", List.of("Bad")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netFaithful").value(true))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.containsString("[Ignore(")));
    }

    @Test
    void repair_returns200WithAnAttemptCard() throws Exception {
        RepairAttempt attempt = new RepairAttempt("Mechanical", "mechanical", "claude-haiku-4-5",
                "CInt banker-rounds 9.9 to 10, so the truncates premise was wrong.",
                List.of(new RepairAttempt.DiffLine("-", "Assert.AreEqual(12, result);"),
                        new RepairAttempt.DiffLine("+", "Assert.AreEqual(13, result);")),
                new RepairAttempt.Gate(true, "Still calls PlaceOrder and still checks the return value."),
                new RepairAttempt.Rerun(true, "23 / 23 passing against your untouched VB.NET."),
                "green", "[TestClass] public class OrderProcessorBaselineTests {}", true);
        when(assureService.repairAttempt(any())).thenReturn(attempt);

        mockMvc.perform(post("/api/assure/repair")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RepairRequest(SESSION_ID,
                                "OrderProcessor", "anthropic", "csharp", java.util.Map.of(),
                                "[TestClass]...", "PlaceOrder_TotalWithFraction_TruncatesToInt", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("Mechanical"))
                .andExpect(jsonPath("$.tag").value("green"))
                .andExpect(jsonPath("$.netFaithful").value(true))
                .andExpect(jsonPath("$.gate.ok").value(true))
                .andExpect(jsonPath("$.rerun.green").value(true))
                .andExpect(jsonPath("$.diff[1].op").value("+"));
    }

    @Test
    void classTests_returnsCsFileAsAttachment() throws Exception {
        String code = "[TestClass] public class OrderProcessorBaseline { }";
        when(artifactService.classTestFile(SESSION_ID, "OrderProcessor"))
                .thenReturn(new AssureArtifactService.TestFileArtifact("OrderProcessorTests.cs", code));

        mockMvc.perform(get("/api/assure/{sessionId}/tests/{className}", SESSION_ID, "OrderProcessor"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("OrderProcessorTests.cs")))
                .andExpect(content().string(code));
    }

    @Test
    void classTests_unknownClass_is404() throws Exception {
        when(artifactService.classTestFile(SESSION_ID, "Ghost"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "no suite"));

        mockMvc.perform(get("/api/assure/{sessionId}/tests/{className}", SESSION_ID, "Ghost"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testsBundle_returnsZipAsAttachment() throws Exception {
        byte[] zip = "PK-fake-zip-bytes".getBytes(StandardCharsets.UTF_8);
        when(artifactService.bundleZip(SESSION_ID)).thenReturn(zip);
        when(artifactService.bundleFileName()).thenReturn("VBGone-Assure-Tests.zip");

        mockMvc.perform(get("/api/assure/{sessionId}/tests.zip", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.parseMediaType("application/zip")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("VBGone-Assure-Tests.zip")))
                .andExpect(content().bytes(zip));
    }

    @Test
    void testsBundle_noAssuredClasses_is404() throws Exception {
        when(artifactService.bundleZip(SESSION_ID))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "nothing assured"));

        mockMvc.perform(get("/api/assure/{sessionId}/tests.zip", SESSION_ID))
                .andExpect(status().isNotFound());
    }
}
