package com.vbgone.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MigrationSession {
    private final String sessionId;
    private String filename;
    private String vbContent;
    /**
     * The UI-free subset of the source (concatenated net-ready classes) that can compile
     * headless on the Linux CLR. Assure's characterisation run compiles THIS, not the whole
     * estate — otherwise WinForms classes in the same upload break the build.
     */
    private String assurableSource;
    /**
     * Per-class readiness bucket from the last assessment (class name → {@link Bucket}). The routing
     * signal that decides which characterisation runner handles a class: {@link Bucket#NET_READY}
     * runs on the Linux sidecar, {@link Bucket#WINDOWS_GATED} on the Windows runner.
     */
    private final Map<String, Bucket> classBuckets = new HashMap<>();
    private String targetLanguage = "csharp";
    private final Map<String, String> classSources = new HashMap<>();
    private AnalysisResult analysisResult;
    private InterfaceResult interfaceResult;
    private TestsResult testsResult;
    private StubResult stubResult;
    private ImplementResult implementResult;
    private BuildResult redBuild;
    private BuildResult greenBuild;
    private PullRequestResult prResult;
    // Assure-mode artifacts:
    private BaselineResult baselineResult;
    private TestsResult baselineSuite;
    private BuildResult netBuild;
    /**
     * The faithful (green) MSTest baseline suite recorded per assured class, keyed by class name.
     * Unlike {@link #baselineSuite} (a single field overwritten on every run/rerun/repair), this
     * retains every class the user has assured so the whole portfolio's suites can be downloaded
     * as one MSTest project. Insertion-ordered so the bundle is deterministic.
     */
    private final Map<String, TestsResult> baselineSuites = new LinkedHashMap<>();
    private final List<TokenUsage> tokenUsages = new ArrayList<>();
    private final Map<String, String> failureMessages = new HashMap<>();

    public MigrationSession(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionId() { return sessionId; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getVbContent() { return vbContent; }
    public void setVbContent(String vbContent) { this.vbContent = vbContent; }

    public String getAssurableSource() { return assurableSource; }
    public void setAssurableSource(String assurableSource) { this.assurableSource = assurableSource; }

    public void setClassBuckets(Map<String, Bucket> buckets) {
        classBuckets.clear();
        if (buckets != null) classBuckets.putAll(buckets);
    }
    /** The class's readiness bucket, or {@code null} if it wasn't in the last assessment. */
    public Bucket getBucketForClass(String className) { return classBuckets.get(className); }

    public String getTargetLanguage() { return targetLanguage; }
    public void setTargetLanguage(String targetLanguage) { this.targetLanguage = targetLanguage; }

    public AnalysisResult getAnalysisResult() { return analysisResult; }
    public void setAnalysisResult(AnalysisResult analysisResult) { this.analysisResult = analysisResult; }

    public InterfaceResult getInterfaceResult() { return interfaceResult; }
    public void setInterfaceResult(InterfaceResult interfaceResult) { this.interfaceResult = interfaceResult; }

    public TestsResult getTestsResult() { return testsResult; }
    public void setTestsResult(TestsResult testsResult) { this.testsResult = testsResult; }

    public StubResult getStubResult() { return stubResult; }
    public void setStubResult(StubResult stubResult) { this.stubResult = stubResult; }

    public ImplementResult getImplementResult() { return implementResult; }
    public void setImplementResult(ImplementResult implementResult) { this.implementResult = implementResult; }

    public BuildResult getRedBuild() { return redBuild; }
    public void setRedBuild(BuildResult redBuild) { this.redBuild = redBuild; }

    public BuildResult getGreenBuild() { return greenBuild; }
    public void setGreenBuild(BuildResult greenBuild) { this.greenBuild = greenBuild; }

    public PullRequestResult getPrResult() { return prResult; }
    public void setPrResult(PullRequestResult prResult) { this.prResult = prResult; }

    public BaselineResult getBaselineResult() { return baselineResult; }
    public void setBaselineResult(BaselineResult baselineResult) { this.baselineResult = baselineResult; }

    public TestsResult getBaselineSuite() { return baselineSuite; }
    public void setBaselineSuite(TestsResult baselineSuite) { this.baselineSuite = baselineSuite; }

    public BuildResult getNetBuild() { return netBuild; }
    public void setNetBuild(BuildResult netBuild) { this.netBuild = netBuild; }

    /** Faithful baseline suites recorded per assured class (insertion-ordered), for download. */
    public Map<String, TestsResult> getBaselineSuites() { return baselineSuites; }
    public void putBaselineSuite(String className, TestsResult suite) {
        baselineSuites.put(className, suite);
    }

    public List<TokenUsage> getTokenUsages() { return tokenUsages; }
    public void addTokenUsage(TokenUsage usage) { tokenUsages.add(usage); }

    public Map<String, String> getFailureMessages() { return failureMessages; }
    public void setFailureMessages(Map<String, String> messages) {
        failureMessages.clear();
        failureMessages.putAll(messages);
    }

    /** Clears per-class artifacts when starting a new class in a multi-class session. */
    public void clearClassArtifacts() {
        this.interfaceResult = null;
        this.testsResult = null;
        this.stubResult = null;
        this.implementResult = null;
        this.redBuild = null;
        this.greenBuild = null;
    }

    public Map<String, String> getClassSources() { return classSources; }
    public void putClassSource(String className, String source) { classSources.put(className, source); }

    /**
     * Returns VB.NET source for a specific class if available (project mode),
     * otherwise extracts the class section from the full vbContent.
     * Falls back to full vbContent if extraction fails.
     */
    public String getVbContentForClass(String className) {
        String classSource = classSources.get(className);
        if (classSource != null) return classSource;

        // Try to extract just this class from the full VB content
        if (vbContent != null) {
            String extracted = extractVbClass(vbContent, className);
            if (extracted != null) return extracted;
        }
        return vbContent;
    }

    /**
     * Extracts a single VB.NET class from source containing multiple classes.
     * Matches: [Public|Friend|Private] Class ClassName ... End Class
     */
    public static String extractVbClass(String source, String className) {
        // Match class declaration with optional access modifier
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?m)^\\s*(?:Public|Friend|Private)?\\s*Class\\s+" +
                java.util.regex.Pattern.quote(className) + "\\b.*?^\\s*End\\s+Class",
                java.util.regex.Pattern.DOTALL | java.util.regex.Pattern.MULTILINE);
        java.util.regex.Matcher matcher = pattern.matcher(source);
        if (matcher.find()) {
            return matcher.group().trim();
        }
        return null;
    }
}
