package com.vbgone.controller;

import com.vbgone.model.AssessRequest;
import com.vbgone.model.BaselineRerunRequest;
import com.vbgone.model.BaselineResult;
import com.vbgone.model.BaselineTestsResult;
import com.vbgone.model.ClassRequest;
import com.vbgone.model.ReadinessReport;
import com.vbgone.model.ZipManifest;
import com.vbgone.service.ProtectAssessmentService;
import com.vbgone.service.ProtectService;
import com.vbgone.service.ZipExtractorService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Protect-mode endpoints (hybrid API). Analysis is shared with Migrate via
 * {@code /api/migrate/analyse} + a {@code mode} param; the genuinely-different steps —
 * the readiness assessment, pinning the baseline surface, and running the characterisation
 * suite against the original VB.NET — live here.
 */
@RestController
@RequestMapping("/api/protect")
public class ProtectController {

    private final ProtectService protectService;
    private final ProtectAssessmentService assessmentService;
    private final ZipExtractorService zipExtractorService;

    public ProtectController(ProtectService protectService,
                             ProtectAssessmentService assessmentService,
                             ZipExtractorService zipExtractorService) {
        this.protectService = protectService;
        this.assessmentService = assessmentService;
        this.zipExtractorService = zipExtractorService;
    }

    /** Front-gate readiness assessment for a single source — static, no AI, nothing leaves the tenant. */
    @PostMapping("/assess")
    public ReadinessReport assess(@RequestBody AssessRequest request) {
        return assessmentService.assess(request.filename(), request.content());
    }

    /** Readiness assessment for an uploaded estate (.zip) — extract .vb files, classify across them. */
    @PostMapping(value = "/assess-project", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReadinessReport assessProject(@RequestParam("file") MultipartFile file) {
        ZipManifest manifest = zipExtractorService.extract(file);
        return assessmentService.assessProject(manifest);
    }

    @PostMapping("/baseline")
    public BaselineResult baseline(@RequestBody ClassRequest request) {
        return protectService.generateBaseline(request.sessionId(), request.className(),
                request.provider(), request.targetLanguage(), request.modelOverrides());
    }

    @PostMapping("/baseline-tests")
    public BaselineTestsResult baselineTests(@RequestBody ClassRequest request) {
        return protectService.runBaselineTests(request.sessionId(), request.className(),
                request.provider(), request.targetLanguage(), request.modelOverrides());
    }

    /** Re-run a corrected net (edited assertions) against the original VB — no AI call. */
    @PostMapping("/rerun-baseline-tests")
    public BaselineTestsResult rerunBaselineTests(@RequestBody BaselineRerunRequest request) {
        return protectService.rerunBaselineTests(
                request.sessionId(), request.className(), request.code());
    }
}
