package com.vbgone.service;

import com.vbgone.ai.AiRequestOptions;
import com.vbgone.ai.AiResponse;
import com.vbgone.ai.ModelRole;
import com.vbgone.lang.LanguageConventions;
import com.vbgone.lang.LanguageConventionsRegistry;
import com.vbgone.model.*;
import com.vbgone.prompt.CSharpPrompts;
import com.vbgone.prompt.PromptLanguage;
import com.vbgone.prompt.PromptLanguageRegistry;
import com.vbgone.session.SessionStore;
import org.springframework.stereotype.Service;

@Service
public class GenerationService {

    // ── Back-compat references for tests that pin the C# system prompts. ──
    // The prompt text now lives on the per-language strategy ({@link CSharpPrompts}).
    static final String INTERFACE_SYSTEM_PROMPT = CSharpPrompts.INTERFACE_SYSTEM_PROMPT;
    static final String TESTS_SYSTEM_PROMPT = CSharpPrompts.TESTS_SYSTEM_PROMPT;
    static final String STUB_SYSTEM_PROMPT = CSharpPrompts.STUB_SYSTEM_PROMPT;
    static final String IMPLEMENT_SYSTEM_PROMPT = CSharpPrompts.IMPLEMENT_SYSTEM_PROMPT;

    private final AiCallSupport aiCallSupport;
    private final SessionStore sessionStore;
    private final PromptLanguageRegistry promptRegistry;
    private final LanguageConventionsRegistry conventionsRegistry;

    public GenerationService(AiCallSupport aiCallSupport,
                             SessionStore sessionStore,
                             PromptLanguageRegistry promptRegistry,
                             LanguageConventionsRegistry conventionsRegistry) {
        this.aiCallSupport = aiCallSupport;
        this.sessionStore = sessionStore;
        this.promptRegistry = promptRegistry;
        this.conventionsRegistry = conventionsRegistry;
    }

    public InterfaceResult generateInterface(String sessionId, String className) {
        return generateInterface(sessionId, className, AiRequestOptions.defaults());
    }

    public InterfaceResult generateInterface(String sessionId, String className, AiRequestOptions options) {
        PromptLanguage prompts = promptRegistry.forLanguage(options.targetLanguage());
        LanguageConventions conv = conventionsRegistry.forLanguage(options.targetLanguage());
        MigrationSession session = sessionStore.getOrThrow(sessionId);
        session.setTargetLanguage(options.targetLanguage());
        session.clearClassArtifacts();

        String userMessage = prompts.interfaceUserMessage(className, session.getVbContentForClass(className));

        AiResponse response = aiCallSupport.call(options, ModelRole.MECHANICAL, prompts.interfaceSystemPrompt(),
                userMessage, 4096L, "interface", session);
        String code = prompts.stripWrappers(prompts.stripCodeFences(response.text()));

        InterfaceResult result = new InterfaceResult(sessionId, className,
                conv.interfaceName(className), code, conv.implName(className));
        session.setInterfaceResult(result);
        return result;
    }

    public TestsResult generateTests(String sessionId, String className) {
        return generateTests(sessionId, className, AiRequestOptions.defaults());
    }

    public TestsResult generateTests(String sessionId, String className, AiRequestOptions options) {
        PromptLanguage prompts = promptRegistry.forLanguage(options.targetLanguage());
        LanguageConventions conv = conventionsRegistry.forLanguage(options.targetLanguage());
        MigrationSession session = sessionStore.getOrThrow(sessionId);
        session.setTargetLanguage(options.targetLanguage());
        InterfaceResult iface = session.getInterfaceResult();
        String ifaceCode = iface != null ? iface.code() : "";

        String userMessage = prompts.testsUserMessage(className, ifaceCode,
                session.getVbContentForClass(className));

        AiResponse response = aiCallSupport.call(options, ModelRole.REASONING, prompts.testsSystemPrompt(),
                userMessage, 16384L, "tests", session);
        String code = prompts.repairTruncated(prompts.stripWrappers(prompts.stripCodeFences(response.text())));

        int testCount = prompts.countTests(code);
        TestsResult result = new TestsResult(sessionId, className,
                conv.testClassName(className), code, testCount);
        session.setTestsResult(result);
        return result;
    }

    public StubResult generateStub(String sessionId, String className) {
        return generateStub(sessionId, className, AiRequestOptions.defaults());
    }

    public StubResult generateStub(String sessionId, String className, AiRequestOptions options) {
        PromptLanguage prompts = promptRegistry.forLanguage(options.targetLanguage());
        MigrationSession session = sessionStore.getOrThrow(sessionId);
        session.setTargetLanguage(options.targetLanguage());
        InterfaceResult iface = session.getInterfaceResult();
        if (iface == null) {
            throw new IllegalStateException("Interface must be generated before stub");
        }

        String userMessage = prompts.stubUserMessage(className, iface);
        AiResponse response = aiCallSupport.call(options, ModelRole.MECHANICAL, prompts.stubSystemPrompt(),
                userMessage, 4096L, "stub", session);
        String code = prompts.stripWrappers(prompts.stripCodeFences(response.text()));

        StubResult result = new StubResult(sessionId, className, code);
        session.setStubResult(result);
        return result;
    }

    public ImplementResult implement(String sessionId, String className, ImplementMode mode) {
        return implement(sessionId, className, mode, AiRequestOptions.defaults());
    }

    public ImplementResult implement(String sessionId, String className, ImplementMode mode,
                                     AiRequestOptions options) {
        PromptLanguage prompts = promptRegistry.forLanguage(options.targetLanguage());
        MigrationSession session = sessionStore.getOrThrow(sessionId);
        session.setTargetLanguage(options.targetLanguage());

        if (mode == ImplementMode.STUB) {
            StubResult stub = session.getStubResult();
            if (stub == null) {
                throw new IllegalStateException("Stub must be generated before implement in STUB mode");
            }
            ImplementResult result = new ImplementResult(sessionId, className, stub.code(), mode);
            session.setImplementResult(result);
            return result;
        }

        InterfaceResult iface = session.getInterfaceResult();
        if (iface == null) {
            throw new IllegalStateException("Interface must be generated before implement");
        }
        String userMessage = prompts.implementUserMessage(className, iface,
                session.getVbContentForClass(className));

        AiResponse response = aiCallSupport.call(options, ModelRole.IMPLEMENTATION, prompts.implementSystemPrompt(),
                userMessage, 16384L, "implement", session);
        String code = prompts.fixDeclaration(
                prompts.stripWrappers(prompts.stripCodeFences(response.text())),
                className, iface);

        // If the model returned analysis instead of code, throw rather than write garbage
        if (!prompts.looksLikeCode(code)) {
            throw new RuntimeException("Model did not return valid " + prompts.id() + " code — try again");
        }

        ImplementResult result = new ImplementResult(sessionId, className, code, mode);
        session.setImplementResult(result);
        return result;
    }

    public ImplementResult retryImplement(String sessionId, String className,
                                           java.util.List<String> failingTests, int attempt) {
        return retryImplement(sessionId, className, failingTests, attempt, AiRequestOptions.defaults());
    }

    public ImplementResult retryImplement(String sessionId, String className,
                                          java.util.List<String> failingTests, int attempt,
                                          AiRequestOptions options) {
        PromptLanguage prompts = promptRegistry.forLanguage(options.targetLanguage());
        MigrationSession session = sessionStore.getOrThrow(sessionId);
        session.setTargetLanguage(options.targetLanguage());

        InterfaceResult iface = session.getInterfaceResult();
        if (iface == null) {
            throw new IllegalStateException("Interface must be generated before retry");
        }
        ImplementResult previous = session.getImplementResult();
        if (previous == null) {
            throw new IllegalStateException("Previous implementation must exist before retry");
        }

        String testSnippets = extractFailingTests(prompts, session, failingTests);

        // Build failure message section from actual test output (expected vs actual)
        StringBuilder failureDetails = new StringBuilder();
        var failureMessages = session.getFailureMessages();
        for (String testName : failingTests) {
            String msg = failureMessages.get(testName);
            if (msg != null) {
                failureDetails.append(testName).append(": ").append(msg).append("\n");
            }
        }

        TestsResult tests = session.getTestsResult();
        String fullTestFile = tests != null ? tests.code() : "";

        String userMessage = prompts.retryUserMessage(className, iface, previous.code(),
                fullTestFile, failingTests, failureDetails.toString(), testSnippets);

        // Escalate on the final attempt (attempt >= 3) — ESCALATION role, else IMPLEMENTATION.
        ModelRole role = attempt >= 3 ? ModelRole.ESCALATION : ModelRole.IMPLEMENTATION;

        AiResponse response = aiCallSupport.call(options, role, prompts.implementSystemPrompt(), userMessage, 16384L,
                "retry-implement", session);
        String code = prompts.fixDeclaration(
                prompts.stripWrappers(prompts.stripCodeFences(response.text())),
                className, iface);

        // If the model returned analysis instead of code, fall back to the previous implementation
        if (!prompts.looksLikeCode(code)) {
            code = previous.code();
        }

        ImplementResult result = new ImplementResult(sessionId, className, code, ImplementMode.CLAUDE);
        session.setImplementResult(result);
        return result;
    }

    /** Pulls the test code off the session and delegates extraction to the language strategy. */
    private String extractFailingTests(PromptLanguage prompts, MigrationSession session,
                                       java.util.List<String> failingTests) {
        TestsResult tests = session.getTestsResult();
        String testCode = tests != null ? tests.code() : "";
        return prompts.extractFailingTests(testCode, failingTests);
    }

}
