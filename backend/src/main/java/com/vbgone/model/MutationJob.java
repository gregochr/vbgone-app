package com.vbgone.model;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mutable server-side handle for an async mutation-testing run. Progress fields are updated from
 * the worker thread and read by polling requests, so they are volatile / atomic. Exposed to clients
 * as an immutable {@link MutationJobStatus} snapshot.
 */
public class MutationJob {

    private final String id;
    private final String sessionId;
    private final String className;

    private volatile MutationJobState state = MutationJobState.PENDING;
    private volatile int total = 0;
    private final AtomicInteger done = new AtomicInteger(0);
    private volatile MutationResult result;
    private volatile String error;

    public MutationJob(String id, String sessionId, String className) {
        this.id = id;
        this.sessionId = sessionId;
        this.className = className;
    }

    public String id() { return id; }
    public String sessionId() { return sessionId; }
    public String className() { return className; }
    public MutationJobState state() { return state; }
    public int total() { return total; }
    public int done() { return done.get(); }
    public MutationResult result() { return result; }
    public String error() { return error; }

    public void setState(MutationJobState state) { this.state = state; }
    public void setTotal(int total) { this.total = total; }
    public int incrementDone() { return done.incrementAndGet(); }

    public void complete(MutationResult result) {
        this.result = result;
        this.state = MutationJobState.DONE;
    }

    public void fail(String error) {
        this.error = error;
        this.state = MutationJobState.FAILED;
    }

    public MutationJobStatus snapshot() {
        return new MutationJobStatus(id, state.name(), done.get(), total, result, error);
    }
}
