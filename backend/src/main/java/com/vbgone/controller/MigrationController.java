package com.vbgone.controller;

import com.vbgone.ai.ProviderUnavailableException;
import com.vbgone.model.*;
import com.vbgone.service.AnalysisService;
import com.vbgone.service.BuildService;
import com.vbgone.service.CostService;
import com.vbgone.service.GenerationService;
import com.vbgone.service.GitHubService;
import com.vbgone.service.ZipExtractorService;
import com.vbgone.session.SessionStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/migrate")
public class MigrationController {

    private final AnalysisService analysisService;
    private final GenerationService generationService;
    private final BuildService buildService;
    private final GitHubService gitHubService;
    private final CostService costService;
    private final ZipExtractorService zipExtractorService;
    private final SessionStore sessionStore;

    public MigrationController(AnalysisService analysisService,
                               GenerationService generationService,
                               BuildService buildService,
                               GitHubService gitHubService,
                               CostService costService,
                               ZipExtractorService zipExtractorService,
                               SessionStore sessionStore) {
        this.analysisService = analysisService;
        this.generationService = generationService;
        this.buildService = buildService;
        this.gitHubService = gitHubService;
        this.costService = costService;
        this.zipExtractorService = zipExtractorService;
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
        rejectJavaTarget(request.targetLanguage());
        return analysisService.analyse(filename, request.content(),
                request.provider(), request.targetLanguage(), request.modelOverrides());
    }

    /**
     * Defensive guard: Java code generation is a frontend-gated preview and is
     * not yet executable on the backend. The frontend never sends "java", so
     * this path only triggers on a misuse of the API.
     */
    private void rejectJavaTarget(String targetLanguage) {
        if (targetLanguage != null && "java".equalsIgnoreCase(targetLanguage)) {
            throw new ProviderUnavailableException(
                    "Java target is in preview and not yet executable on the backend.");
        }
    }

    @PostMapping(value = "/upload-project", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProjectAnalysis uploadProject(@RequestParam("file") MultipartFile file) {
        ZipManifest manifest = zipExtractorService.extract(file);
        return analysisService.analyseProject(manifest);
    }

    @PostMapping("/interface")
    public InterfaceResult generateInterface(@RequestBody ClassRequest request) {
        rejectJavaTarget(request.targetLanguage());
        return generationService.generateInterface(request.sessionId(), request.className(),
                request.provider(), request.targetLanguage(), request.modelOverrides());
    }

    @PostMapping("/tests")
    public TestsResult generateTests(@RequestBody ClassRequest request) {
        rejectJavaTarget(request.targetLanguage());
        return generationService.generateTests(request.sessionId(), request.className(),
                request.provider(), request.targetLanguage(), request.modelOverrides());
    }

    @PostMapping("/stub")
    public StubResult generateStub(@RequestBody ClassRequest request) {
        rejectJavaTarget(request.targetLanguage());
        return generationService.generateStub(request.sessionId(), request.className(),
                request.provider(), request.targetLanguage(), request.modelOverrides());
    }

    @PostMapping("/build")
    public BuildResult build(@RequestBody BuildRequest request) {
        return buildService.build(request.sessionId());
    }

    @PostMapping("/implement")
    public ImplementResult implement(@RequestBody ImplementRequest request) {
        rejectJavaTarget(request.targetLanguage());
        return generationService.implement(request.sessionId(), request.className(), request.mode(),
                request.provider(), request.targetLanguage(), request.modelOverrides());
    }

    @PostMapping("/retry-implement")
    public ImplementResult retryImplement(@RequestBody RetryRequest request) {
        rejectJavaTarget(request.targetLanguage());
        return generationService.retryImplement(
                request.sessionId(), request.className(), request.failingTests(), request.attempt(),
                request.provider(), request.targetLanguage(), request.modelOverrides());
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

    @GetMapping("/dump/{sessionId}")
    public SessionDump dumpSession(@PathVariable String sessionId) {
        MigrationSession session = sessionStore.get(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        return new SessionDump(
                session.getSessionId(),
                session.getVbContent(),
                session.getClassSources(),
                session.getInterfaceResult(),
                session.getTestsResult(),
                session.getStubResult(),
                session.getImplementResult(),
                session.getRedBuild(),
                session.getGreenBuild(),
                session.getTokenUsages());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public java.util.Map<String, String> handleIllegalArgument(IllegalArgumentException ex) {
        return java.util.Map.of("error", ex.getMessage());
    }
}
