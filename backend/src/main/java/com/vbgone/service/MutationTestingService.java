package com.vbgone.service;

import com.vbgone.build.VbCharacterisationRunner;
import com.vbgone.model.BuildResult;
import com.vbgone.model.BuildStatus;
import com.vbgone.model.MigrationSession;
import com.vbgone.model.MutationJob;
import com.vbgone.model.MutationJobStatus;
import com.vbgone.model.MutationResult;
import com.vbgone.model.MutationTestRequest;
import com.vbgone.model.SurvivingMutant;
import com.vbgone.model.TestsResult;
import com.vbgone.mutation.Mutant;
import com.vbgone.mutation.MutantGenerator;
import com.vbgone.session.MutationJobStore;
import com.vbgone.session.SessionStore;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs mutation testing over an Assure characterisation net (ADR-0001, Path B): verify the net is
 * green on the original, then mutate the VB one operator at a time and re-run the net against each
 * mutant. RED = killed (net caught it), GREEN = survived (a blind spot), ERROR = didn't compile
 * (skipped). Runs asynchronously — a class of any size is minutes of {@code dotnet test} runs — and
 * is purely informational: it never gates GREEN/RED.
 */
@Service
public class MutationTestingService {

    private static final Logger log = LoggerFactory.getLogger(MutationTestingService.class);

    /** Bound the run so a large class can't spawn hundreds of minute-long compiles. */
    static final int MAX_MUTANTS = 50;

    private final SessionStore sessionStore;
    private final VbCharacterisationRunner runner;
    private final MutantGenerator mutator;
    private final MutationJobStore jobStore;
    private final Executor executor;
    private final ExecutorService owned; // non-null only when we created the executor

    @org.springframework.beans.factory.annotation.Autowired
    public MutationTestingService(SessionStore sessionStore, VbCharacterisationRunner runner,
                                  MutantGenerator mutator, MutationJobStore jobStore) {
        // One worker: mutant runs for a class share the session's workspace dir, so they must not
        // overlap. A single thread also keeps the .NET sidecar from being swamped.
        this(sessionStore, runner, mutator, jobStore, null);
    }

    /** Test seam: pass a synchronous executor ({@code Runnable::run}) to run jobs inline. */
    MutationTestingService(SessionStore sessionStore, VbCharacterisationRunner runner,
                           MutantGenerator mutator, MutationJobStore jobStore, Executor executor) {
        this.sessionStore = sessionStore;
        this.runner = runner;
        this.mutator = mutator;
        this.jobStore = jobStore;
        if (executor != null) {
            this.executor = executor;
            this.owned = null;
        } else {
            ExecutorService es = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "mutation-worker");
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

    /** Accepts a job and starts it on the worker thread; returns the job id to poll. */
    public String startJob(MutationTestRequest request) {
        MigrationSession session = sessionStore.get(request.sessionId())
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + request.sessionId()));
        if (request.suiteCode() == null || request.suiteCode().isBlank()) {
            throw new IllegalArgumentException("suiteCode is required — pass the green baseline suite.");
        }
        String vbSource = resolveSource(session);
        if (vbSource == null || vbSource.isBlank()) {
            throw new IllegalArgumentException("No VB source to mutate for this session.");
        }
        TestsResult suite = new TestsResult(request.sessionId(), request.className(),
                request.className() + "BaselineTests", request.suiteCode(), 0);

        MutationJob job = jobStore.create(request.sessionId(), request.className());
        executor.execute(() -> runJob(job, vbSource, suite));
        return job.id();
    }

    public Optional<MutationJobStatus> getStatus(String jobId) {
        return jobStore.get(jobId).map(MutationJob::snapshot);
    }

    // ── Worker ──

    private void runJob(MutationJob job, String vbSource, TestsResult suite) {
        try {
            job.setState(com.vbgone.model.MutationJobState.RUNNING);

            // 1. The net must faithfully pin the original before we can trust survivors.
            BuildResult baseline = runner.runAgainstSource(job.sessionId(), job.className(), vbSource, suite);
            if (baseline.buildStatus() != BuildStatus.GREEN) {
                job.fail("Baseline is " + baseline.buildStatus() + ", not GREEN — the net must pass "
                        + "against the original before mutation testing. Get the baseline green first.");
                return;
            }

            // 2. Generate mutants (capped).
            List<Mutant> all = mutator.generate(vbSource);
            if (all.isEmpty()) {
                job.fail("No mutable operators found in the source — nothing to mutate.");
                return;
            }
            List<Mutant> mutants = all.size() > MAX_MUTANTS ? all.subList(0, MAX_MUTANTS) : all;
            if (all.size() > MAX_MUTANTS) {
                log.info("Mutation: capping {} at {} of {} generated mutants",
                        job.className(), MAX_MUTANTS, all.size());
            }
            job.setTotal(mutants.size());

            // 3. Run the net against each mutant.
            int killed = 0, survived = 0, skipped = 0;
            List<SurvivingMutant> survivors = new ArrayList<>();
            for (Mutant m : mutants) {
                BuildResult r = runner.runAgainstSource(job.sessionId(), job.className(), m.mutatedSource(), suite);
                switch (r.buildStatus()) {
                    case RED -> killed++;
                    case GREEN -> { survived++; survivors.add(SurvivingMutant.from(m)); }
                    case ERROR -> skipped++; // non-compiling mutant is not a behaviour change
                }
                job.incrementDone();
            }

            MutationResult result = MutationResult.of(killed, survived, skipped, survivors);
            log.info("Mutation: {} scored {} ({} killed / {} survived / {} skipped)",
                    job.className(), result.score(), killed, survived, skipped);
            job.complete(result);

        } catch (RuntimeException e) {
            log.warn("Mutation job {} failed", job.id(), e);
            job.fail("Mutation testing failed: " + e.getMessage());
        }
    }

    /** The same source the characterisation runner compiles: the net-ready subset, else the raw VB. */
    private String resolveSource(MigrationSession session) {
        String assurable = session.getAssurableSource();
        return (assurable == null || assurable.isBlank()) ? session.getVbContent() : assurable;
    }
}
