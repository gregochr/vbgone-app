package com.vbgone.service;

import com.vbgone.build.VbCharacterisationRunner;
import com.vbgone.model.BuildResult;
import com.vbgone.model.BuildStatus;
import com.vbgone.model.MigrationSession;
import com.vbgone.model.MutationJobStatus;
import com.vbgone.model.MutationTestRequest;
import com.vbgone.mutation.VbMutator;
import com.vbgone.session.MutationJobStore;
import com.vbgone.session.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MutationTestingServiceTest {

    // A tiny headless class with one boundary op (`<= 10`) plus a few integer literals to mutate.
    private static final String VB = """
            Public Class C
              Public Function F(x As Integer) As Integer
                If x <= 10 Then Return 1
                Return 2
              End Function
            End Class
            """;

    @Mock
    private SessionStore sessionStore;
    @Mock
    private VbCharacterisationRunner runner;

    private MutationJobStore jobStore;
    private MutationTestingService service;

    private static final Executor SYNC = Runnable::run; // run jobs inline for deterministic asserts

    private BuildResult green() { return new BuildResult("s1", BuildStatus.GREEN, 3, 3, 0, List.of(), List.of()); }
    private BuildResult red()   { return new BuildResult("s1", BuildStatus.RED, 3, 2, 1, List.of(), List.of("T")); }
    private BuildResult error() { return new BuildResult("s1", BuildStatus.ERROR, 0, 0, 0, List.of("boom"), List.of()); }

    @BeforeEach
    void setUp() {
        jobStore = new MutationJobStore();
        service = new MutationTestingService(sessionStore, runner, new VbMutator(), jobStore, SYNC);
        // getOrThrow delegates to the (stubbed) get(); let the real method run over the mock.
        lenient().when(sessionStore.getOrThrow(anyString())).thenCallRealMethod();

        MigrationSession session = new MigrationSession("s1");
        session.setVbContent(VB);
        lenient().when(sessionStore.get("s1")).thenReturn(Optional.of(session));
    }

    private MutationJobStatus run() {
        String jobId = service.startJob(new MutationTestRequest("s1", "C", "suite code"));
        return service.getStatus(jobId).orElseThrow();
    }

    @Test
    void killsMutantsTheNetCatchesAndReportsSurvivorsItMisses() {
        // Baseline green; the boundary mutant (x < 10) makes the net go red (killed); everything
        // else stays green (survived).
        when(runner.runAgainstSource(any(), any(), anyString(), any())).thenAnswer(inv -> {
            String src = inv.getArgument(2);
            if (src.equals(VB)) return green();              // baseline
            if (src.contains("x < 10")) return red();        // boundary mutant caught
            return green();                                  // other mutants slip through
        });

        MutationJobStatus st = run();

        assertThat(st.state()).isEqualTo("DONE");
        assertThat(st.result().killed()).isEqualTo(1);
        assertThat(st.result().survived()).isGreaterThanOrEqualTo(1);
        assertThat(st.result().score()).isNotNull();
        assertThat(st.done()).isEqualTo(st.total());
        assertThat(st.result().survivors()).isNotEmpty()
                .allSatisfy(s -> assertThat(s.description()).isNotBlank());
    }

    @Test
    void countsNonCompilingMutantsAsSkippedNotSurvived() {
        when(runner.runAgainstSource(any(), any(), anyString(), any())).thenAnswer(inv -> {
            String src = inv.getArgument(2);
            if (src.equals(VB)) return green();  // baseline
            return error();                      // every mutant "fails to compile"
        });

        MutationJobStatus st = run();

        assertThat(st.state()).isEqualTo("DONE");
        assertThat(st.result().survived()).isZero();
        assertThat(st.result().skipped()).isEqualTo(st.total());
        assertThat(st.result().score()).isNull(); // no judged mutants → no score
    }

    @Test
    void failsWhenBaselineIsNotGreen() {
        when(runner.runAgainstSource(any(), any(), anyString(), any())).thenReturn(red());

        MutationJobStatus st = run();

        assertThat(st.state()).isEqualTo("FAILED");
        assertThat(st.error()).contains("not GREEN");
        assertThat(st.result()).isNull();
    }

    @Test
    void rejectsUnknownSession() {
        when(sessionStore.get("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.startJob(new MutationTestRequest("nope", "C", "suite")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session not found");
    }

    @Test
    void rejectsMissingSuiteCode() {
        assertThatThrownBy(() -> service.startJob(new MutationTestRequest("s1", "C", "  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("suiteCode");
    }

    @Test
    void getStatusEmptyForUnknownJob() {
        assertThat(service.getStatus("no-such-job")).isEmpty();
    }
}
