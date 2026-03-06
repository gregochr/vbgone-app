package com.vbgone.service;

import com.anthropic.models.messages.Model;
import com.vbgone.model.*;
import com.vbgone.session.SessionStore;
import org.springframework.stereotype.Service;

@Service
public class GenerationService {

    static final String INTERFACE_SYSTEM_PROMPT = """
            You are a VB.NET to C# migration expert. Generate a C# interface from VB.NET source \
            code. Extract only public business logic methods — ignore all UI, event handlers, and \
            Windows Forms concerns. Return only raw C# code. No markdown. No backticks. \
            No explanation. The response will be written directly to a .cs file.""";

    static final String TESTS_SYSTEM_PROMPT = """
            You are a VB.NET to C# migration expert and TDD practitioner. Generate a comprehensive \
            NUnit test suite in C# based on VB.NET source code behaviour. Tests must cover happy \
            path, edge cases, and error conditions including divide by zero, null inputs, and \
            boundary values.

            Generate ONLY the NUnit test class. Do NOT include the interface definition or any \
            implementation class in the test file. The tests should use the interface type for \
            the field declaration and instantiate the real implementation class in the [SetUp] method.

            Return only raw C# code. No markdown. No backticks. No explanation.""";

    static final String STUB_SYSTEM_PROMPT = """
            You are a C# developer. Generate a C# class that implements a given interface. \
            Every method body must be: throw new NotImplementedException(); \
            Do NOT implement any logic — every single method must throw NotImplementedException. \
            Return only raw C# code. No markdown. No backticks. No explanation.""";

    static final String IMPLEMENT_SYSTEM_PROMPT = """
            You are a VB.NET to C# migration expert. Generate a complete C# implementation of an \
            interface based on VB.NET source behaviour. Write idiomatic modern C# — use \
            expression-bodied members, pattern matching, and nullable reference types where \
            appropriate. Match return types EXACTLY as declared in the interface. Do not change \
            return types — if the interface declares double, return double. \
            Return only raw C# code. No markdown. No backticks. No explanation.""";

    private final ClaudeClient claudeClient;
    private final SessionStore sessionStore;

    public GenerationService(ClaudeClient claudeClient, SessionStore sessionStore) {
        this.claudeClient = claudeClient;
        this.sessionStore = sessionStore;
    }

    public InterfaceResult generateInterface(String sessionId, String className) {
        MigrationSession session = getSession(sessionId);
        String userMessage = "Generate a C# interface named I" + className
                + " for this VB.NET:\n" + session.getVbContent();

        ClaudeClient.ClaudeResponse response = claudeClient.sendWithCachedSystemPrompt(
                INTERFACE_SYSTEM_PROMPT, userMessage, Model.CLAUDE_HAIKU_4_5, 4096L);
        String code = stripCodeFences(response.text());

        String modelId = Model.CLAUDE_HAIKU_4_5.asString();
        double cost = CostService.calculateCost(modelId, response.inputTokens(), response.outputTokens());
        session.addTokenUsage(new TokenUsage("interface", modelId, response.inputTokens(), response.outputTokens(), cost));

        InterfaceResult result = new InterfaceResult(sessionId, className, "I" + className, code);
        session.setInterfaceResult(result);
        return result;
    }

    public TestsResult generateTests(String sessionId, String className) {
        MigrationSession session = getSession(sessionId);
        String userMessage = "Generate NUnit tests for I" + className
                + " based on this VB.NET:\n" + session.getVbContent()
                + "\n\nThe implementation class name is " + className
                + ", the interface is I" + className + "."
                + "\n\nThe test file should follow this structure exactly:\n"
                + "using NUnit.Framework;\n"
                + "// other using statements\n\n"
                + "namespace " + className + "Tests\n{\n"
                + "    [TestFixture]\n"
                + "    public class " + className + "Tests\n    {\n"
                + "        private I" + className + " _sut;\n\n"
                + "        [SetUp]\n"
                + "        public void SetUp()\n        {\n"
                + "            _sut = new " + className + "();\n"
                + "        }\n\n"
                + "        // test methods only\n"
                + "    }\n}";

        ClaudeClient.ClaudeResponse response = claudeClient.sendWithCachedSystemPrompt(
                TESTS_SYSTEM_PROMPT, userMessage, Model.CLAUDE_SONNET_4_6, 8192L);
        String code = stripCodeFences(response.text());

        String modelId = Model.CLAUDE_SONNET_4_6.asString();
        double cost = CostService.calculateCost(modelId, response.inputTokens(), response.outputTokens());
        session.addTokenUsage(new TokenUsage("tests", modelId, response.inputTokens(), response.outputTokens(), cost));

        int testCount = countTests(code);
        TestsResult result = new TestsResult(sessionId, className, className + "Tests", code, testCount);
        session.setTestsResult(result);
        return result;
    }

    public StubResult generateStub(String sessionId, String className) {
        MigrationSession session = getSession(sessionId);
        InterfaceResult iface = session.getInterfaceResult();
        if (iface == null) {
            throw new IllegalStateException("Interface must be generated before stub");
        }

        String userMessage = "Generate a stub implementation of " + iface.code();
        ClaudeClient.ClaudeResponse response = claudeClient.sendWithCachedSystemPrompt(
                STUB_SYSTEM_PROMPT, userMessage, Model.CLAUDE_HAIKU_4_5, 4096L);
        String code = stripCodeFences(response.text());

        String modelId = Model.CLAUDE_HAIKU_4_5.asString();
        double cost = CostService.calculateCost(modelId, response.inputTokens(), response.outputTokens());
        session.addTokenUsage(new TokenUsage("stub", modelId, response.inputTokens(), response.outputTokens(), cost));

        StubResult result = new StubResult(sessionId, className, code);
        session.setStubResult(result);
        return result;
    }

    public ImplementResult implement(String sessionId, String className, ImplementMode mode) {
        MigrationSession session = getSession(sessionId);

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
        String userMessage = "Implement " + iface.code()
                + " based on this VB.NET behaviour:\n" + session.getVbContent();

        ClaudeClient.ClaudeResponse response = claudeClient.sendWithCachedSystemPrompt(
                IMPLEMENT_SYSTEM_PROMPT, userMessage, Model.CLAUDE_SONNET_4_6, 8192L);
        String code = stripCodeFences(response.text());

        String modelId = Model.CLAUDE_SONNET_4_6.asString();
        double cost = CostService.calculateCost(modelId, response.inputTokens(), response.outputTokens());
        session.addTokenUsage(new TokenUsage("implement", modelId, response.inputTokens(), response.outputTokens(), cost));

        ImplementResult result = new ImplementResult(sessionId, className, code, mode);
        session.setImplementResult(result);
        return result;
    }

    public ImplementResult retryImplement(String sessionId, String className, java.util.List<String> failingTests) {
        MigrationSession session = getSession(sessionId);

        InterfaceResult iface = session.getInterfaceResult();
        if (iface == null) {
            throw new IllegalStateException("Interface must be generated before retry");
        }
        ImplementResult previous = session.getImplementResult();
        if (previous == null) {
            throw new IllegalStateException("Previous implementation must exist before retry");
        }

        String failingList = String.join(", ", failingTests);
        String userMessage = "The following tests are failing. Fix the implementation to make them pass: "
                + failingList + "\n\nCurrent failing implementation:\n" + previous.code()
                + "\n\nInterface:\n" + iface.code()
                + "\n\nOriginal VB.NET behaviour:\n" + session.getVbContent();

        ClaudeClient.ClaudeResponse response = claudeClient.sendWithCachedSystemPrompt(
                IMPLEMENT_SYSTEM_PROMPT, userMessage, Model.CLAUDE_SONNET_4_6, 8192L);
        String code = stripCodeFences(response.text());

        String modelId = Model.CLAUDE_SONNET_4_6.asString();
        double cost = CostService.calculateCost(modelId, response.inputTokens(), response.outputTokens());
        session.addTokenUsage(new TokenUsage("retry-implement", modelId, response.inputTokens(), response.outputTokens(), cost));

        ImplementResult result = new ImplementResult(sessionId, className, code, ImplementMode.CLAUDE);
        session.setImplementResult(result);
        return result;
    }

    private MigrationSession getSession(String sessionId) {
        return sessionStore.get(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
    }

    int countTests(String code) {
        return (int) code.lines()
                .filter(line -> line.trim().startsWith("[Test]") || line.trim().startsWith("[TestCase"))
                .count();
    }

    private String stripCodeFences(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("^```\\w*\\s*", "").replaceAll("\\s*```$", "");
        }
        return trimmed;
    }
}
