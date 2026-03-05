package com.vbgone.service;

import com.vbgone.model.PullRequestResult;
import org.springframework.stereotype.Service;

@Service
public class GitHubService {

    public PullRequestResult raisePR(String sessionId, String repoOwner, String repoName, String branchName) {
        throw new UnsupportedOperationException("Not yet implemented — Phase 2");
    }
}
