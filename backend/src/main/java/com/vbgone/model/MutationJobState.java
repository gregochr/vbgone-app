package com.vbgone.model;

/** Lifecycle of an async mutation-testing job. */
public enum MutationJobState {
    /** Accepted, not yet started. */
    PENDING,
    /** Baseline verified green; running mutants. */
    RUNNING,
    /** Finished; {@code result} is populated. */
    DONE,
    /** Aborted — e.g. the baseline wasn't green, or the source produced no mutants. */
    FAILED
}
