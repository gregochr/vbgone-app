package com.vbgone.model;

import java.util.List;

public record PullRequestResult(
        String sessionId,
        String prUrl,
        String branchName,
        List<String> filesCommitted
) {}
