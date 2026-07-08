package com.vbgone.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vbgone.model.MutationJobStatus;
import com.vbgone.model.MutationResult;
import com.vbgone.model.MutationTestRequest;
import com.vbgone.service.MutationTestingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MutationControllerTest {

    private final MutationTestingService service = mock(MutationTestingService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new MutationController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private MutationJobStatus jobStatus(String state, MutationResult result) {
        return new MutationJobStatus("job-1", state, result == null ? 0 : result.total(),
                result == null ? 0 : result.total(), result, null);
    }

    @Test
    void startReturnsTheJobStatusWithItsId() throws Exception {
        when(service.startJob(any())).thenReturn("job-1");
        when(service.getStatus("job-1")).thenReturn(Optional.of(jobStatus("RUNNING", null)));

        mvc.perform(post("/api/assure/mutation-test")
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(new MutationTestRequest("s1", "C", "suite"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-1"))
                .andExpect(jsonPath("$.state").value("RUNNING"));
    }

    @Test
    void pollReturnsTheResultWhenDone() throws Exception {
        MutationResult result = MutationResult.of(8, 2, 1, List.of());
        when(service.getStatus("job-1")).thenReturn(Optional.of(jobStatus("DONE", result)));

        mvc.perform(get("/api/assure/mutation-test/job-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DONE"))
                .andExpect(jsonPath("$.result.killed").value(8))
                .andExpect(jsonPath("$.result.survived").value(2))
                .andExpect(jsonPath("$.result.score").value(80)); // 8 / (8+2)
    }

    @Test
    void pollReturns404ForUnknownJob() throws Exception {
        when(service.getStatus("gone")).thenReturn(Optional.empty());

        mvc.perform(get("/api/assure/mutation-test/gone"))
                .andExpect(status().isNotFound());
    }

    @Test
    void startReturns400ForBadRequest() throws Exception {
        when(service.startJob(any())).thenThrow(new IllegalArgumentException("Session not found: s9"));

        mvc.perform(post("/api/assure/mutation-test")
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(new MutationTestRequest("s9", "C", "suite"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Session not found: s9"));
    }
}
