package com.vbgone.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vbgone.config.RateLimitFilter;
import com.vbgone.model.*;
import com.vbgone.service.AssureAssessmentService;
import com.vbgone.service.AssureService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .thenReturn(new BaselineTestsResult(SESSION_ID, "OrderProcessor", "OrderProcessorBaseline",
                        "[TestClass] public class OrderProcessorBaseline {}", 43, true, build, List.of()));

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
    void rerunBaselineTests_returns200WithFailingAssertions() throws Exception {
        BuildResult build = new BuildResult(SESSION_ID, BuildStatus.RED, 43, 41, 2, List.of(),
                List.of("ApplyDiscount_UnknownCode_ReturnsSubtotalUnchanged"));
        when(assureService.rerunBaselineTests(eq(SESSION_ID), eq("OrderProcessor"), anyString()))
                .thenReturn(new BaselineTestsResult(SESSION_ID, "OrderProcessor", "OrderProcessorBaseline",
                        "[TestClass] public class OrderProcessorBaseline {}", 43, false, build,
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
    void repair_returns200WithAnAttemptCard() throws Exception {
        RepairAttempt attempt = new RepairAttempt("Mechanical", "mechanical", "claude-haiku-4-5",
                "CInt banker-rounds 9.9 to 10, so the truncates premise was wrong.",
                List.of(new RepairAttempt.DiffLine("-", "Assert.AreEqual(12, result);"),
                        new RepairAttempt.DiffLine("+", "Assert.AreEqual(13, result);")),
                new RepairAttempt.Gate(true, "Still calls PlaceOrder and still checks the return value."),
                new RepairAttempt.Rerun(true, "23 / 23 passing against your untouched VB.NET."),
                "green", "[TestClass] public class OrderProcessorBaseline {}", true);
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
}
