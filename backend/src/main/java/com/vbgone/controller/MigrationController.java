package com.vbgone.controller;

import com.vbgone.model.*;
import com.vbgone.service.AnalysisService;
import com.vbgone.service.BuildService;
import com.vbgone.service.CostService;
import com.vbgone.service.GenerationService;
import com.vbgone.service.GitHubService;
import com.vbgone.session.SessionStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/migrate")
public class MigrationController {

    private final AnalysisService analysisService;
    private final GenerationService generationService;
    private final BuildService buildService;
    private final GitHubService gitHubService;
    private final CostService costService;
    private final SessionStore sessionStore;

    public MigrationController(AnalysisService analysisService,
                               GenerationService generationService,
                               BuildService buildService,
                               GitHubService gitHubService,
                               CostService costService,
                               SessionStore sessionStore) {
        this.analysisService = analysisService;
        this.generationService = generationService;
        this.buildService = buildService;
        this.gitHubService = gitHubService;
        this.costService = costService;
        this.sessionStore = sessionStore;
    }

    @PostMapping("/analyse")
    public AnalysisResult analyse(@RequestBody AnalyseRequest request) {
        String filename = request.filename();
        if (filename == null || filename.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filename must not be empty.");
        }
        String lower = filename.toLowerCase();
        if (!lower.endsWith(".vb") && !lower.endsWith(".zip")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only .vb and .zip files are supported. Received: " + filename);
        }
        return analysisService.analyse(filename, request.content());
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

    @GetMapping("/cost/{sessionId}")
    public CostResult getCost(@PathVariable String sessionId) {
        return costService.getCost(sessionId);
    }
}
