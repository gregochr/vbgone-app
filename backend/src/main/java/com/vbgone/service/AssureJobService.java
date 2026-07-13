package com.vbgone.service;

import com.vbgone.ai.AiRequestOptions;
import com.vbgone.model.BaselineJobStatus;
import com.vbgone.model.BaselineTestsResult;
import com.vbgone.model.ClassRequest;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Async wrapper around {@link AssureService#runBaselineTests} for the Windows runner path. A
 * characterisation dispatched to a GitHub {@code windows-latest} runner takes minutes, so POST
 * starts a job and returns its id; the client polls for the result rather than holding the request
 * open. The Linux path stays synchronous (it's fast) — the frontend only routes here when the user
 * picked the Windows runner. Mirrors {@code MutationTestingService}'s job model.
 */
@Service
public class AssureJobService {

    private final AssureService assureService;
    private final Executor executor;
    private final ExecutorService owned; // non-null only when we created the executor
    private final ConcurrentHashMap<String, Job> jobs = new ConcurrentHashMap<>();

    @Autowired
    public AssureJobService(AssureService assureService) {
        this(assureService, null);
    }

    /** Test seam: pass a synchronous executor ({@code Runnable::run}) to run jobs inline. */
    AssureJobService(AssureService assureService, Executor executor) {
        this.assureService = assureService;
        if (executor != null) {
            this.executor = executor;
            this.owned = null;
        } else {
            ExecutorService es = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "assure-baseline-worker");
                t.setDaemon(true);
                return t;
            });
            this.executor = es;
            this.owned = es;
        }
    }

    @PreDestroy
    void shutdown() {
        if (owned != null) owned.shutdownNow();
    }

    /** Start a baseline-tests run on the worker thread; returns the initial (PENDING/RUNNING) status. */
    public BaselineJobStatus start(ClassRequest request) {
        Job job = new Job(UUID.randomUUID().toString());
        jobs.put(job.id, job);
        AiRequestOptions options = request.aiOptions();
        String sessionId = request.sessionId();
        String className = request.className();
        String runnerMode = request.runnerMode();
        executor.execute(() -> {
            job.state = "RUNNING";
            try {
                job.result = assureService.runBaselineTests(sessionId, className, options, runnerMode);
                job.state = "DONE";
            } catch (RuntimeException e) {
                job.error = (e.getMessage() == null) ? e.toString() : e.getMessage();
                job.state = "FAILED";
            }
        });
        return job.snapshot();
    }

    public Optional<BaselineJobStatus> getStatus(String jobId) {
        Job job = jobs.get(jobId);
        return (job == null) ? Optional.empty() : Optional.of(job.snapshot());
    }

    private static final class Job {
        final String id;
        volatile String state = "PENDING";
        volatile BaselineTestsResult result;
        volatile String error;

        Job(String id) {
            this.id = id;
        }

        BaselineJobStatus snapshot() {
            return new BaselineJobStatus(id, state, result, error);
        }
    }
}
