package com.vbgone.model;

public record PullRequestRequest(
        String sessionId,
        String repoOwner,
        String repoName,
        String branchName
) {}
