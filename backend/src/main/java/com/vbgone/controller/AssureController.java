package com.vbgone.controller;

import com.vbgone.model.AssessRequest;
import com.vbgone.model.BaselineRerunRequest;
import com.vbgone.model.BaselineResult;
import com.vbgone.model.BaselineTestsResult;
import com.vbgone.model.ClassRequest;
import com.vbgone.model.ReadinessReport;
import com.vbgone.model.RepairAttempt;
import com.vbgone.model.RepairRequest;
import com.vbgone.model.ZipManifest;
import com.vbgone.service.AssureAssessmentService;
import com.vbgone.service.AssureService;
import com.vbgone.service.ZipExtractorService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Assure-mode endpoints (hybrid API). Analysis is shared with Migrate via
 * {@code /api/migrate/analyse} + a {@code mode} param; the genuinely-different steps —
 * the readiness assessment, pinning the baseline surface, and running the characterisation
 * suite against the original VB.NET — live here.
 */
@RestController
@RequestMapping("/api/assure")
public class AssureController {

    private final AssureService assureService;
    private final AssureAssessmentService assessmentService;
    private final ZipExtractorService zipExtractorService;

    public AssureController(AssureService assureService,
                             AssureAssessmentService assessmentService,
                             ZipExtractorService zipExtractorService) {
        this.assureService = assureService;
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
        return assureService.generateBaseline(request.sessionId(), request.className(),
                request.provider(), request.targetLanguage(), request.modelOverrides());
    }

    @PostMapping("/baseline-tests")
    public BaselineTestsResult baselineTests(@RequestBody ClassRequest request) {
        return assureService.runBaselineTests(request.sessionId(), request.className(),
                request.provider(), request.targetLanguage(), request.modelOverrides());
    }

    /** Re-run a corrected net (edited assertions) against the original VB — no AI call. */
    @PostMapping("/rerun-baseline-tests")
    public BaselineTestsResult rerunBaselineTests(@RequestBody BaselineRerunRequest request) {
        return assureService.rerunBaselineTests(
                request.sessionId(), request.className(), request.code());
    }

    /**
     * Auto-repair one failing baseline test at the given tier (1 → mechanical, 2 → reasoning,
     * 3 → escalation). Rewrites just that test to match the real observed behaviour, gates the
     * rewrite, and re-runs it against the original VB. The frontend calls this once per tier,
     * feeding the previous attempt's code forward, until it goes green or quarantines.
     */
    @PostMapping("/repair")
    public RepairAttempt repair(@RequestBody RepairRequest request) {
        return assureService.repairAttempt(request);
    }
}
