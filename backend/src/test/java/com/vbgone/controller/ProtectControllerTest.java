package com.vbgone.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vbgone.config.RateLimitFilter;
import com.vbgone.model.*;
import com.vbgone.service.ProtectService;
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

@WebMvcTest(value = ProtectController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class))
class ProtectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProtectService protectService;

    private static final String SESSION_ID = "s-protect";

    @Test
    void baseline_returns200WithPinnedSurface() throws Exception {
        when(protectService.generateBaseline(eq(SESSION_ID), eq("OrderProcessor"), any(), any(), any()))
                .thenReturn(new BaselineResult(SESSION_ID, "OrderProcessor",
                        "OrderProcessor.dll · public surface",
                        List.of(
                                new BaselineMember("decimal CalculateTotal(IReadOnlyList<LineItem> items)", null),
                                new BaselineMember("decimal SplitPerHead(decimal total, int headcount)",
                                        "throws DivideByZeroException when headcount = 0"))));

        mockMvc.perform(post("/api/protect/baseline")
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
        when(protectService.runBaselineTests(eq(SESSION_ID), eq("OrderProcessor"), any(), any(), any()))
                .thenReturn(new BaselineTestsResult(SESSION_ID, "OrderProcessor", "OrderProcessorBaseline",
                        "[TestClass] public class OrderProcessorBaseline {}", 43, true, build, List.of()));

        mockMvc.perform(post("/api/protect/baseline-tests")
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
        when(protectService.rerunBaselineTests(eq(SESSION_ID), eq("OrderProcessor"), anyString()))
                .thenReturn(new BaselineTestsResult(SESSION_ID, "OrderProcessor", "OrderProcessorBaseline",
                        "[TestClass] public class OrderProcessorBaseline {}", 43, false, build,
                        List.of(new TestFailure("ApplyDiscount_UnknownCode_ReturnsSubtotalUnchanged",
                                "Expected: 100 but was: 90"))));

        mockMvc.perform(post("/api/protect/rerun-baseline-tests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new BaselineRerunRequest(SESSION_ID, "OrderProcessor", "edited code"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netFaithful").value(false))
                .andExpect(jsonPath("$.failures[0].name")
                        .value("ApplyDiscount_UnknownCode_ReturnsSubtotalUnchanged"))
                .andExpect(jsonPath("$.failures[0].message").value("Expected: 100 but was: 90"));
    }
}
