package com.vbgone.controller;

import com.vbgone.model.MutationJobStatus;
import com.vbgone.model.MutationTestRequest;
import com.vbgone.service.MutationTestingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mutation-testing endpoints (ADR-0001, Path B). Async: POST starts a job and returns its id;
 * the client polls GET for progress and the final score. Lives under {@code /api/assure} — not
 * rate-limited, so polling is fine.
 */
@RestController
@RequestMapping("/api/assure/mutation-test")
public class MutationController {

    private final MutationTestingService service;

    public MutationController(MutationTestingService service) {
        this.service = service;
    }

    /** Start a run against the green baseline suite. Returns the job's initial status (with its id). */
    @PostMapping
    public MutationJobStatus start(@RequestBody MutationTestRequest request) {
        String jobId = service.startJob(request);
        // The job may still be PENDING/RUNNING; the snapshot always exists right after creation.
        return service.getStatus(jobId).orElseThrow();
    }

    /** Poll progress and the final result. 404 once the (in-memory) job is gone or never existed. */
    @GetMapping("/{jobId}")
    public ResponseEntity<MutationJobStatus> status(@PathVariable String jobId) {
        return service.getStatus(jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
