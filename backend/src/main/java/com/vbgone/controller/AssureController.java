package com.vbgone.controller;

import com.vbgone.model.AssessRequest;
import com.vbgone.model.AugmentBaselineRequest;
import com.vbgone.model.BaselineRerunRequest;
import com.vbgone.model.BaselineResult;
import com.vbgone.model.BaselineTestsResult;
import com.vbgone.model.ClassRequest;
import com.vbgone.model.IngestRepoRequest;
import com.vbgone.model.QuarantineRequest;
import com.vbgone.model.ReadinessReport;
import com.vbgone.model.RepairAttempt;
import com.vbgone.model.RepairRequest;
import com.vbgone.model.ZipManifest;
import com.vbgone.service.AssureArtifactService;
import com.vbgone.service.AssureAssessmentService;
import com.vbgone.service.AssureService;
import com.vbgone.service.RepoIngestService;
import com.vbgone.service.ZipExtractorService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

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
    private final RepoIngestService repoIngestService;
    private final AssureArtifactService artifactService;

    public AssureController(AssureService assureService,
                             AssureAssessmentService assessmentService,
                             ZipExtractorService zipExtractorService,
                             RepoIngestService repoIngestService,
                             AssureArtifactService artifactService) {
        this.assureService = assureService;
        this.assessmentService = assessmentService;
        this.zipExtractorService = zipExtractorService;
        this.repoIngestService = repoIngestService;
        this.artifactService = artifactService;
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

    /**
     * Readiness assessment for a <strong>public</strong> GitHub repo URL — an alternative to the
     * {@code .zip} upload. Clones server-side, keeps only {@code .vb} sources (build output and
     * non-source files are skipped), and classifies across them. Public repos only; no auth.
     */
    @PostMapping("/ingest-repo")
    public ReadinessReport ingestRepo(@RequestBody IngestRepoRequest request) {
        ZipManifest manifest = repoIngestService.ingest(request);
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

    /** "Add more tests" — extend a thin green net; the model adds tests for the untested code, then re-run. */
    @PostMapping("/augment-baseline-tests")
    public BaselineTestsResult augmentBaselineTests(@RequestBody AugmentBaselineRequest request) {
        return assureService.augmentBaselineTests(
                request.sessionId(), request.className(), request.code(), request.coveragePercent(),
                request.provider(), request.targetLanguage(), request.modelOverrides());
    }

    /**
     * Accept a red baseline by setting the unrepairable test(s) aside: mark them {@code [Ignore]}
     * (kept but skipped) and re-run the rest against the original VB. A green remainder is recorded
     * as the class's downloadable baseline suite. No AI call.
     */
    @PostMapping("/quarantine-baseline")
    public BaselineTestsResult quarantineBaseline(@RequestBody QuarantineRequest request) {
        return assureService.quarantineBaseline(
                request.sessionId(), request.className(), request.code(), request.tests());
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

    /**
     * Download one assured class's baseline test suite as a {@code .cs} file. The suite is a real
     * artifact of the baseline-tests step (it ran green against the original VB.NET) — streamed
     * as-is, not regenerated. 404 if the class hasn't been assured in this session.
     */
    @GetMapping("/{sessionId}/tests/{className}")
    public ResponseEntity<byte[]> classTests(@PathVariable String sessionId,
                                             @PathVariable String className) {
        AssureArtifactService.TestFileArtifact artifact =
                artifactService.classTestFile(sessionId, className);
        return download(artifact.content().getBytes(StandardCharsets.UTF_8),
                new MediaType("text", "plain", StandardCharsets.UTF_8), artifact.filename());
    }

    /**
     * Download every assured class's baseline suite as one runnable MSTest project
     * ({@code tests/*.cs} + {@code .csproj} + {@code README.md}), assembled server-side.
     * 404 if nothing has been assured yet.
     */
    @GetMapping("/{sessionId}/tests.zip")
    public ResponseEntity<byte[]> testsBundle(@PathVariable String sessionId) {
        return download(artifactService.bundleZip(sessionId),
                MediaType.parseMediaType("application/zip"), artifactService.bundleFileName());
    }

    private static ResponseEntity<byte[]> download(byte[] body, MediaType type, String filename) {
        ContentDisposition disposition = ContentDisposition.attachment().filename(filename).build();
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(body);
    }
}
