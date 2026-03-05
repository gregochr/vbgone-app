package com.vbgone.model;

import java.util.ArrayList;
import java.util.List;

public class MigrationSession {
    private final String sessionId;
    private String filename;
    private String vbContent;
    private AnalysisResult analysisResult;
    private InterfaceResult interfaceResult;
    private TestsResult testsResult;
    private StubResult stubResult;
    private ImplementResult implementResult;
    private BuildResult redBuild;
    private BuildResult greenBuild;
    private PullRequestResult prResult;
    private final List<TokenUsage> tokenUsages = new ArrayList<>();

    public MigrationSession(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionId() { return sessionId; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getVbContent() { return vbContent; }
    public void setVbContent(String vbContent) { this.vbContent = vbContent; }

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
}
