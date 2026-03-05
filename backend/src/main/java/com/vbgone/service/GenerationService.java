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
            boundary values. Return only raw C# code. No markdown. No backticks. No explanation.""";

    static final String STUB_SYSTEM_PROMPT = """
            You are a C# developer. Generate a C# class that implements a given interface with \
            stub methods that throw NotImplementedException. Return only raw C# code. No markdown. \
            No backticks. No explanation.""";

    static final String IMPLEMENT_SYSTEM_PROMPT = """
            You are a VB.NET to C# migration expert. Generate a complete C# implementation of an \
            interface based on VB.NET source behaviour. Write idiomatic modern C# — use \
            expression-bodied members, pattern matching, and nullable reference types where \
            appropriate. Return only raw C# code. No markdown. No backticks. No explanation.""";

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

        String code = claudeClient.sendWithCachedSystemPrompt(
                INTERFACE_SYSTEM_PROMPT, userMessage, Model.CLAUDE_HAIKU_4_5, 4096L);
        code = stripCodeFences(code);

        InterfaceResult result = new InterfaceResult(sessionId, className, "I" + className, code);
        session.setInterfaceResult(result);
        return result;
    }

    public TestsResult generateTests(String sessionId, String className) {
        MigrationSession session = getSession(sessionId);
        String userMessage = "Generate NUnit tests for I" + className
                + " based on this VB.NET:\n" + session.getVbContent();

        String code = claudeClient.sendWithCachedSystemPrompt(
                TESTS_SYSTEM_PROMPT, userMessage, Model.CLAUDE_SONNET_4_6, 8192L);
        code = stripCodeFences(code);

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
        String code = claudeClient.sendWithCachedSystemPrompt(
                STUB_SYSTEM_PROMPT, userMessage, Model.CLAUDE_HAIKU_4_5, 4096L);
        code = stripCodeFences(code);

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

        String code = claudeClient.sendWithCachedSystemPrompt(
                IMPLEMENT_SYSTEM_PROMPT, userMessage, Model.CLAUDE_SONNET_4_6, 8192L);
        code = stripCodeFences(code);

        ImplementResult result = new ImplementResult(sessionId, className, code, mode);
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
