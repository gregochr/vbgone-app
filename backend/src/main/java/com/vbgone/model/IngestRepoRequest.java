package com.vbgone.model;

/**
 * Public GitHub repo ingestion request. The user pastes a repo URL on the Upload screen; VBGone
 * clones it server-side, keeps only {@code .vb} sources, and runs the same static readiness pass
 * as an uploaded {@code .zip} estate. Public repositories only — there is deliberately no auth.
 *
 * @param url a public GitHub repo URL or {@code org/repo} shorthand
 */
public record IngestRepoRequest(String url) {}
