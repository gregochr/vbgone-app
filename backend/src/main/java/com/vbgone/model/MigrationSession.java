package com.vbgone.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MigrationSession {
    private final String sessionId;
    private String filename;
    private String vbContent;
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
