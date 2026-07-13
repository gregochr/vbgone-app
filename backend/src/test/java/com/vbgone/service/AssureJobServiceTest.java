package com.vbgone.service;

import com.vbgone.model.BaselineJobStatus;
import com.vbgone.model.BaselineTestsResult;
import com.vbgone.model.BuildResult;
import com.vbgone.model.BuildStatus;
import com.vbgone.model.ClassRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssureJobServiceTest {

    @Mock
    private AssureService assureService;

    private AssureJobService jobs;

    @BeforeEach
    void setUp() {
        // Inline executor: the job runs synchronously on start(), so the snapshot is already terminal.
        jobs = new AssureJobService(assureService, Runnable::run);
    }

    private ClassRequest windowsRequest() {
        return new ClassRequest("s1", "Settlement", "anthropic", "csharp", null, "windows");
    }

    @Test
    void start_runsBaselineTests_andReportsDoneWithResult() {
        BuildResult build = new BuildResult("s1", BuildStatus.GREEN, 7, 7, 0, List.of(), List.of());
        BaselineTestsResult result = new BaselineTestsResult(
                "s1", "Settlement", "SettlementBaselineTests", "code", 7, true, build, List.of());
        when(assureService.runBaselineTests(eq("s1"), eq("Settlement"), any(), eq("windows")))
                .thenReturn(result);

        BaselineJobStatus status = jobs.start(windowsRequest());

        assertThat(status.state()).isEqualTo("DONE");
        assertThat(status.result()).isSameAs(result);
        assertThat(status.error()).isNull();
        assertThat(jobs.getStatus(status.jobId())).contains(status);
    }

    @Test
    void start_capturesFailureAsFailedState() {
        when(assureService.runBaselineTests(anyString(), anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("dispatch boom"));

        BaselineJobStatus status = jobs.start(windowsRequest());

        assertThat(status.state()).isEqualTo("FAILED");
        assertThat(status.error()).contains("dispatch boom");
        assertThat(status.result()).isNull();
    }

    @Test
    void getStatus_unknownJob_isEmpty() {
        assertThat(jobs.getStatus("nope")).isEmpty();
    }
}
