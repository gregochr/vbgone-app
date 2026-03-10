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
            Windows Forms concerns.

            When generating C# interfaces, use appropriate return types for mathematical operations. \
            Division operations should return double, not int, to preserve decimal precision. \
            Consider the semantics of each method — if the operation can produce a non-integer \
            result, use double or decimal as the return type.

            IMPORTANT: The interface MUST be in the root namespace (no namespace declaration). \
            Do NOT wrap the interface in any namespace block.

            Return only raw C# code. No markdown. No backticks. \
            No explanation. The response will be written directly to a .cs file.""";

    static final String TESTS_SYSTEM_PROMPT = """
            You are a VB.NET to C# migration expert and TDD practitioner. Generate a comprehensive \
            NUnit test suite in C# based on VB.NET source code behaviour. Tests must cover happy \
            path, edge cases, and error conditions including divide by zero, null inputs, and \
            boundary values.

            Generate ONLY the NUnit test class. Do NOT include the interface definition or any \
            implementation class in the test file. The tests should use the interface type for \
            the field declaration and instantiate the real implementation class in the [SetUp] method.

            IMPORTANT: The interface and implementation class are in the ROOT namespace (no namespace). \
            The test file must NOT use any 'using' statement to import them — they are already \
            globally accessible. Do NOT wrap the test class in any namespace block either.

            Return only raw C# code. No markdown. No backticks. No explanation.""";

    static final String STUB_SYSTEM_PROMPT = """
            You are a C# developer. Generate a C# class that implements a given interface. \
            Every method body must be: throw new NotImplementedException(); \
            Do NOT implement any logic — every single method must throw NotImplementedException. \
            IMPORTANT: The class MUST be in the root namespace (no namespace declaration). \
            Do NOT wrap the class in any namespace block. \
            Return only raw C# code. No markdown. No backticks. No explanation.""";

    static final String IMPLEMENT_SYSTEM_PROMPT = """
            You are a VB.NET to C# migration expert. Generate a complete C# implementation of an \
            interface based on VB.NET source behaviour. The class name and interface name are \
            specified in the user message — use EXACTLY those names. Write idiomatic modern C# — \
            use expression-bodied members, pattern matching, and nullable reference types where \
            appropriate.

            CRITICAL: You MUST match the return types EXACTLY as declared in the C# interface. \
            Do NOT change any return types. If the interface declares 'double Divide(int a, int b)' \
            then the implementation MUST be 'public double Divide(int a, int b)'. Changing a return \
            type will cause compilation errors.

            When implementing Divide methods, always check for division by zero and throw \
            DivideByZeroException: if (b == 0) throw new DivideByZeroException("Cannot divide by zero."); \
            Never rely on implicit division by zero behaviour — always throw explicitly.

            IMPORTANT: The class MUST be in the root namespace (no namespace declaration). \
            Do NOT wrap the class in any namespace block.

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
                + " for this VB.NET:\n" + session.getVbContentForClass(className);

        ClaudeClient.ClaudeResponse response = claudeClient.sendWithCachedSystemPrompt(
                INTERFACE_SYSTEM_PROMPT, userMessage, Model.CLAUDE_HAIKU_4_5, 4096L);
        String code = stripNamespaceWrapper(stripCodeFences(response.text()));

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
                + " based on this VB.NET:\n" + session.getVbContentForClass(className)
                + "\n\nThe implementation class name is " + className
                + ", the interface is I" + className + "."
                + "\n\nThe test file should follow this structure exactly:\n"
                + "using NUnit.Framework;\n\n"
                + "[TestFixture]\n"
                + "public class " + className + "Tests\n{\n"
                + "    private I" + className + " _sut;\n\n"
                + "    [SetUp]\n"
                + "    public void SetUp()\n    {\n"
                + "        _sut = new " + className + "();\n"
                + "    }\n\n"
                + "    // test methods here — no namespace block\n"
                + "}";

        ClaudeClient.ClaudeResponse response = claudeClient.sendWithCachedSystemPrompt(
                TESTS_SYSTEM_PROMPT, userMessage, Model.CLAUDE_SONNET_4_6, 16384L);
        String code = repairTruncatedCSharp(stripNamespaceWrapper(stripCodeFences(response.text())));

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

        String userMessage = "Generate a stub class named " + className
                + " that implements " + iface.interfaceName() + ".\n\nInterface:\n" + iface.code();
        ClaudeClient.ClaudeResponse response = claudeClient.sendWithCachedSystemPrompt(
                STUB_SYSTEM_PROMPT, userMessage, Model.CLAUDE_HAIKU_4_5, 4096L);
        String code = stripNamespaceWrapper(stripCodeFences(response.text()));

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
        String userMessage = "Implement a C# class named " + className
                + " that implements " + iface.interfaceName() + ". "
                + "Match every method signature exactly — same return types, same parameter types.\n\n"
                + "Interface:\n" + iface.code()
                + "\n\nOriginal VB.NET behaviour:\n" + session.getVbContentForClass(className);

        ClaudeClient.ClaudeResponse response = claudeClient.sendWithCachedSystemPrompt(
                IMPLEMENT_SYSTEM_PROMPT, userMessage, Model.CLAUDE_SONNET_4_6, 16384L);
        String code = fixClassDeclaration(
                stripNamespaceWrapper(stripCodeFences(response.text())),
                className, iface.interfaceName());

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
        String testSnippets = extractFailingTests(session, failingTests);
        String userMessage = "The following tests are failing for class " + className
                + " (which implements " + iface.interfaceName() + "). "
                + "Fix the implementation to make them pass: "
                + failingList + "\n\nFailing test source:\n" + testSnippets
                + "\n\nCurrent failing implementation:\n" + previous.code()
                + "\n\nInterface:\n" + iface.code()
                + "\n\nOriginal VB.NET behaviour:\n" + session.getVbContentForClass(className);

        ClaudeClient.ClaudeResponse response = claudeClient.sendWithCachedSystemPrompt(
                IMPLEMENT_SYSTEM_PROMPT, userMessage, Model.CLAUDE_SONNET_4_6, 16384L);
        String code = fixClassDeclaration(
                stripNamespaceWrapper(stripCodeFences(response.text())),
                className, iface.interfaceName());

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

    /**
     * Extracts the source code of failing test methods from the full test file.
     * Matches methods by name and captures everything from the [Test] attribute to the closing brace.
     */
    String extractFailingTests(MigrationSession session, java.util.List<String> failingTests) {
        TestsResult tests = session.getTestsResult();
        if (tests == null || failingTests.isEmpty()) return "";

        String testCode = tests.code();
        String[] lines = testCode.split("\n");
        StringBuilder sb = new StringBuilder();

        for (String testName : failingTests) {
            // Find the method — look for a line containing the test name
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains(testName) && (lines[i].contains("void ") || lines[i].contains("int ") || lines[i].contains("double ") || lines[i].contains("string ") || lines[i].contains("bool "))) {
                    // Walk back to find [Test] or [TestCase attribute
                    int start = i;
                    while (start > 0 && !lines[start - 1].trim().startsWith("[Test")) {
                        start--;
                    }
                    if (start > 0) start--; // include the attribute line

                    // Walk forward to find the closing brace (matching depth)
                    int depth = 0;
                    int end = i;
                    for (int j = i; j < lines.length; j++) {
                        for (char c : lines[j].toCharArray()) {
                            if (c == '{') depth++;
                            else if (c == '}') depth--;
                        }
                        if (depth <= 0 && j > i) {
                            end = j;
                            break;
                        }
                    }

                    for (int j = start; j <= end && j < lines.length; j++) {
                        sb.append(lines[j]).append("\n");
                    }
                    sb.append("\n");
                    break;
                }
            }
        }
        return sb.toString().trim();
    }

    /**
     * If Claude generated a class with the wrong name or implementing the wrong interface,
     * fix the class declaration line to use the correct names.
     * e.g. "public class OrderCalculationService : IOrderCalculationService"
     *    → "public class OrderConstants : IOrderConstants"
     */
    String fixClassDeclaration(String code, String expectedClass, String expectedInterface) {
        // Match: public [sealed|partial|abstract|static] class <AnyName> : <AnyInterface>
        String fixed = code.replaceFirst(
                "public\\s+(?:sealed\\s+|partial\\s+|abstract\\s+|static\\s+)*class\\s+\\w+\\s*:\\s*\\w+",
                "public class " + expectedClass + " : " + expectedInterface);
        return fixed;
    }

    /**
     * If Claude's output was truncated mid-token, the C# code will be missing closing braces.
     * This detects the imbalance and appends enough closing braces to make the file compilable.
     * Any incomplete method at the end is removed.
     */
    String repairTruncatedCSharp(String code) {
        int opens = 0;
        for (char c : code.toCharArray()) {
            if (c == '{') opens++;
            else if (c == '}') opens--;
        }
        if (opens <= 0) return code;

        // Truncation happened — remove the last incomplete method/test
        // Find the last complete [Test] or [TestCase block ending with }
        int lastCloseBrace = code.lastIndexOf('}');
        if (lastCloseBrace > 0) {
            code = code.substring(0, lastCloseBrace + 1);
        }

        // Recount and close remaining open braces
        opens = 0;
        for (char c : code.toCharArray()) {
            if (c == '{') opens++;
            else if (c == '}') opens--;
        }
        StringBuilder sb = new StringBuilder(code);
        for (int i = 0; i < opens; i++) {
            sb.append("\n}");
        }
        return sb.toString();
    }

    String stripNamespaceWrapper(String code) {
        // If Claude wraps code in a namespace block despite instructions, unwrap it.
        // Matches: namespace Foo { ... } or namespace Foo\n{ ... }
        String trimmed = code.trim();
        if (trimmed.matches("(?s)^namespace\\s+\\S+\\s*\\{.*\\}\\s*$")) {
            // Remove "namespace X {" and the final "}"
            int openBrace = trimmed.indexOf('{');
            String inner = trimmed.substring(openBrace + 1).trim();
            if (inner.endsWith("}")) {
                inner = inner.substring(0, inner.length() - 1).trim();
            }
            return inner;
        }
        // Also handle file-scoped namespace: namespace Foo;
        if (trimmed.matches("(?s)^namespace\\s+\\S+\\s*;.*")) {
            return trimmed.replaceFirst("^namespace\\s+\\S+\\s*;\\s*", "").trim();
        }
        return trimmed;
    }

    private String stripCodeFences(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("^```\\w*\\s*", "").replaceAll("\\s*```$", "");
        }
        return trimmed;
    }
}
