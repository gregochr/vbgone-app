package com.vbgone.controller;

import com.vbgone.model.*;
import com.vbgone.service.AnalysisService;
import com.vbgone.service.BuildService;
import com.vbgone.service.GenerationService;
import com.vbgone.service.GitHubService;
import com.vbgone.session.SessionStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/migrate")
public class MigrationController {

    private final AnalysisService analysisService;
    private final GenerationService generationService;
    private final BuildService buildService;
    private final GitHubService gitHubService;
    private final SessionStore sessionStore;

    public MigrationController(AnalysisService analysisService,
                               GenerationService generationService,
                               BuildService buildService,
                               GitHubService gitHubService,
                               SessionStore sessionStore) {
        this.analysisService = analysisService;
        this.generationService = generationService;
        this.buildService = buildService;
        this.gitHubService = gitHubService;
        this.sessionStore = sessionStore;
    }

    @PostMapping("/analyse")
    public AnalysisResult analyse(@RequestBody AnalyseRequest request) {
        return analysisService.analyse(request.filename(), request.content());
    }

    @PostMapping("/interface")
    public InterfaceResult generateInterface(@RequestBody ClassRequest request) {
        return generationService.generateInterface(request.sessionId(), request.className());
    }

    @PostMapping("/tests")
    public TestsResult generateTests(@RequestBody ClassRequest request) {
        return generationService.generateTests(request.sessionId(), request.className());
    }

    @PostMapping("/stub")
    public StubResult generateStub(@RequestBody ClassRequest request) {
        return generationService.generateStub(request.sessionId(), request.className());
    }

    @PostMapping("/build")
    public BuildResult build(@RequestBody BuildRequest request) {
        return buildService.build(request.sessionId());
    }

    @PostMapping("/implement")
    public ImplementResult implement(@RequestBody ImplementRequest request) {
        return generationService.implement(request.sessionId(), request.className(), request.mode());
    }

    @PostMapping("/pr")
    public PullRequestResult raisePR(@RequestBody PullRequestRequest request) {
        return gitHubService.raisePR(
                request.sessionId(), request.repoOwner(), request.repoName(), request.branchName());
    }
}
