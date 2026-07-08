package com.vbgone.session;

import com.vbgone.model.MutationJob;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** In-memory registry of mutation-testing jobs, keyed by jobId (mirrors {@link SessionStore}). */
@Component
public class MutationJobStore {

    private final ConcurrentMap<String, MutationJob> jobs = new ConcurrentHashMap<>();

    public MutationJob create(String sessionId, String className) {
        String id = UUID.randomUUID().toString();
        MutationJob job = new MutationJob(id, sessionId, className);
        jobs.put(id, job);
        return job;
    }

    public Optional<MutationJob> get(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }
}
