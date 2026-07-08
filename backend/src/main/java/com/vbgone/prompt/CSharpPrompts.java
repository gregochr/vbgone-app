package com.vbgone.prompt;

import com.vbgone.model.InterfaceResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * C# / NUnit prompt strategy. The system prompts, user-message construction, and
 * post-processors are moved verbatim from the original {@code GenerationService}
 * so C# behaviour stays byte-identical.
 */
@Component
public class CSharpPrompts implements PromptLanguage {

    public static final String INTERFACE_SYSTEM_PROMPT = """
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

    public static final String TESTS_SYSTEM_PROMPT = """
            You are a VB.NET to C# migration expert and TDD practitioner. Generate a comprehensive \
            NUnit test suite in C# based on VB.NET source code behaviour. Tests must cover happy \
            path, edge cases, and error conditions including divide by zero, null inputs, and \
            boundary values.

            CRITICAL — BRANCH COVERAGE: Before writing tests, enumerate every decision point in \
            the VB.NET source — each If/ElseIf/Else, Select Case arm, loop body vs skipped loop, \
            guard clause, early return, and short-circuited Boolean (AndAlso/OrElse). Write at \
            least one asserting test that exercises EACH side of EVERY branch, so that every \
            reachable path through each method is covered. For a condition like \
            'If x > 10 Then ... Else ...', include a test where x > 10, one where x < 10, and one \
            at the boundary x = 10. Target full BRANCH coverage, not merely line coverage — a \
            method is not adequately tested until every branch outcome has its own asserting test.

            CRITICAL — TEST CONSISTENCY: When a method has multiple interacting rules \
            (e.g. tiered pricing, conditional fees, cascading thresholds), every test MUST \
            account for ALL rules that apply to its specific inputs. Before writing any \
            assertion, trace through the FULL VB.NET logic for the test's exact inputs and \
            include every effect that triggers. Do NOT test one rule in isolation if the \
            inputs also trigger other rules — the expected value must reflect the complete \
            calculation path.

            Generate ONLY the NUnit test class. Do NOT include the interface definition or any \
            implementation class in the test file. The tests should use the interface type for \
            the field declaration and instantiate the real implementation class in the [SetUp] method.

            IMPORTANT: The interface and implementation class are in the ROOT namespace (no namespace). \
            The test file must NOT use any 'using' statement to import them — they are already \
            globally accessible. Do NOT wrap the test class in any namespace block either.

            Return only raw C# code. No markdown. No backticks. No explanation.""";

    public static final String STUB_SYSTEM_PROMPT = """
            You are a C# developer. Generate a C# class that implements a given interface. \
            Every method body must be: throw new NotImplementedException(); \
            Do NOT implement any logic — every single method must throw NotImplementedException. \
            IMPORTANT: The class MUST be in the root namespace (no namespace declaration). \
            Do NOT wrap the class in any namespace block. \
            Return only raw C# code. No markdown. No backticks. No explanation.""";

    public static final String IMPLEMENT_SYSTEM_PROMPT = """
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

            Return ONLY the complete C# class. No markdown. No backticks. No explanation. \
            No analysis. No discussion. No test code. Just the class starting with 'public class'.""";

    // ── Assure-mode prompts (C#-only; Assure locks TARGET to C#) ──

    public static final String BASELINE_SURFACE_SYSTEM_PROMPT = """
            You are a VB.NET behaviour archaeologist. Given a VB.NET class, list its ACTUAL public \
            surface — every public method as a C#-style signature — exactly as it exists today. You \
            do NOT synthesise a clean interface and you do NOT change anything. For any member whose \
            behaviour is a known defect (silent wrong result, unhandled exception on edge inputs), \
            add a short defect note describing what it does today. Return JSON only, no preamble, no \
            markdown, matching this exact structure:
            {
              "members": [
                { "signature": "decimal CalculateTotal(IReadOnlyList<LineItem> items)", "defect": null },
                { "signature": "decimal ApplyDiscount(decimal subtotal, string code)", "defect": "returns subtotal unchanged on unknown code" }
              ]
            }
            Use "defect": null for members with no flagged defect. Signatures use C# type names.""";

    public static final String BASELINE_TESTS_SYSTEM_PROMPT = """
            You are a VB.NET behaviour archaeologist writing a CHARACTERISATION suite. Generate an \
            MSTest class in C# that pins the CURRENT behaviour of the legacy class exactly as it runs \
            today — defects included. Assert the REAL outcomes: the actual exception types it throws \
            on edge inputs (e.g. DivideByZeroException, InvalidCastException, NullReferenceException) \
            and the real coerced/stringified results — NOT idealised or corrected behaviour. A test \
            that documents a silent fault (e.g. an unknown discount code returning the subtotal \
            unchanged) must assert that fault as-is.

            GREEN means behaviour is unchanged; it does NOT mean correct. Use [TestClass] on the \
            class and [TestMethod] on each test. Instantiate the real class directly. The class and \
            its types are in the root namespace — do NOT add a namespace block and do NOT add 'using' \
            statements for the class under test.

            Return only raw C# code. No markdown. No backticks. No explanation.""";

    public static final String AUGMENT_BASELINE_TESTS_SYSTEM_PROMPT = """
            You are a VB.NET behaviour archaeologist EXTENDING an existing MSTest characterisation \
            suite to pin more of a legacy class. You are given the current suite and the original \
            VB.NET. Keep EVERY existing [TestMethod] exactly as it is, and ADD new [TestMethod]s that \
            characterise behaviour the current suite does not yet exercise: untested public methods, \
            untaken branches (each side of an If / Select Case / loop), and edge inputs — null, zero, \
            empty, negatives, and boundary values.

            Assert the REAL current behaviour, defects included — the actual exception types thrown \
            (e.g. DivideByZeroException, InvalidCastException, NullReferenceException) and the real \
            coerced/stringified results, NOT idealised or corrected behaviour. GREEN means behaviour \
            is unchanged; it does NOT mean correct. Do NOT weaken or delete any existing assertion, \
            and never add a meaningless always-pass test.

            Return the FULL [TestClass] — every existing test plus the new ones — with the SAME class \
            name. Instantiate the real class directly. The class and its types are in the root \
            namespace: do NOT add a namespace block and do NOT add 'using' statements for the class \
            under test. Return only raw C# code. No markdown. No backticks. No explanation.""";

    /** Assure step 3 — asks the model for the concrete public surface as JSON. */
    public String baselineSurfaceUserMessage(String className, String vbSource) {
        return "List the actual public surface of the VB.NET class " + className
                + " as C# signatures, flagging known defects. VB.NET source:\n" + vbSource;
    }

    /** Assure step 4 — asks for an MSTest characterisation suite over the original behaviour. */
    public String baselineTestsUserMessage(String className, String vbSource) {
        return "Generate an MSTest characterisation suite named " + className + "BaselineTests that pins "
                + "the CURRENT behaviour of " + className + " — assert the real exception types and "
                + "coerced results, defects included. Instantiate new " + className + "().\n\n"
                + "The test class MUST follow this structure exactly:\n"
                + "[TestClass]\npublic class " + className + "BaselineTests\n{\n"
                + "    // [TestMethod] characterisation tests here — no namespace block\n}\n\n"
                + "Original VB.NET behaviour:\n" + vbSource;
    }

    /**
     * Assure "add more tests" — extend an existing green suite to pin more of the class. Carries the
     * current suite, the source, and (when known) the current line coverage so the model knows how
     * thin the net is and how much room there is to grow.
     */
    public String augmentBaselineTestsUserMessage(String className, String vbSource,
                                                  String currentSuite, Double coveragePercent) {
        String thinness = coveragePercent == null
                ? "The current characterisation suite for " + className + " leaves parts of the class unpinned — extend it."
                : "The current characterisation suite for " + className + " covers only "
                        + Math.round(coveragePercent) + "% of the class — extend it to pin more.";
        return thinness + " Keep EVERY existing [TestMethod]; ADD new ones that exercise the methods, "
                + "branches and edge inputs not yet tested. Instantiate new " + className + "().\n\n"
                + "Current suite (keep all of these tests, add more):\n" + currentSuite + "\n\n"
                + "Original VB.NET behaviour:\n" + vbSource;
    }

    /**
     * Assure auto-repair — rewrites ONE failing characterisation test to match the real observed
     * behaviour of the untouched original. The model is grounded on the actual runner output
     * (Expected/Actual or the thrown type) and must not guess the "right" answer or touch the code
     * under test. It returns strict JSON so the service can gate, splice and re-run the result.
     */
    public static final String REPAIR_SYSTEM_PROMPT = """
            You repair ONE failing MSTest characterisation test. The class under test is the \
            user's ORIGINAL, UNMODIFIED VB.NET — so a failing test can only mean the TEST is wrong, \
            never the code. Rewrite the test to match the REAL observed behaviour shown in the \
            runner output (the actual returned value, or the exact exception type thrown). Do NOT \
            change the code under test. Do NOT guess an idealised answer — match what actually \
            happened.

            Hard rules:
            - Keep the SAME method under test and the SAME inputs. Only the assertion (and, if it \
            becomes misleading, the test name/comment) may change.
            - Keep a REAL assertion on the return value or thrown exception. Never write a \
            meaningless always-pass test (Assert.IsTrue(true), a deleted assert, a swallowing \
            try/catch).
            - If a value was expected but the code THREW, switch to Assert.ThrowsException<T> with \
            the EXACT exception type from the output. If the code returned a different value, swap \
            the expected literal to the observed one.
            - If there is no valid single-test edit that matches the observed behaviour (e.g. the \
            value is different on every run), set "noEdit": true and explain why — do NOT fake a pass.

            Return STRICT JSON only, no markdown:
            {"rationale": "one or two plain sentences", "newTest": "the full rewritten [TestMethod] \
            method, or empty string if noEdit", "noEdit": false}""";

    /**
     * Assure auto-repair user message for a single tier. Carries the failing test as it stands,
     * the method under test's VB source, and the exact runner output to ground the rewrite.
     */
    public String repairUserMessage(String failingTest, String currentTest, String vbSource,
                                    String observed, String tierGuidance) {
        return "Repair the failing test `" + failingTest + "`.\n\n"
                + "Runner output (the REAL observed behaviour to match):\n"
                + (observed == null || observed.isBlank() ? "(no message captured)" : observed) + "\n\n"
                + "The failing test as it stands now:\n" + currentTest + "\n\n"
                + "The original VB.NET under test (do NOT change it):\n" + vbSource + "\n\n"
                + tierGuidance;
    }

    /** Counts MSTest test methods ([TestMethod] / [DataTestMethod]). */
    public int countMsTests(String code) {
        if (code == null) return 0;
        return (int) code.lines()
                .filter(line -> {
                    String t = line.trim();
                    return t.startsWith("[TestMethod]") || t.startsWith("[DataTestMethod]");
                })
                .count();
    }

    @Override
    public String id() {
        return "csharp";
    }

    @Override
    public String interfaceSystemPrompt() {
        return INTERFACE_SYSTEM_PROMPT;
    }

    @Override
    public String testsSystemPrompt() {
        return TESTS_SYSTEM_PROMPT;
    }

    @Override
    public String stubSystemPrompt() {
        return STUB_SYSTEM_PROMPT;
    }

    @Override
    public String implementSystemPrompt() {
        return IMPLEMENT_SYSTEM_PROMPT;
    }

    @Override
    public String interfaceUserMessage(String className, String vbSource) {
        return "Generate a C# interface named I" + className
                + " for this VB.NET:\n" + vbSource;
    }

    @Override
    public String testsUserMessage(String className, String interfaceCode, String vbSource) {
        return "Generate NUnit tests for I" + className
                + " based on this VB.NET:\n" + vbSource
                + "\n\nC# interface (ONLY test methods defined here — do NOT invent extra methods):\n"
                + interfaceCode
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
    }

    @Override
    public String stubUserMessage(String className, InterfaceResult iface) {
        return "Generate a stub class named " + className
                + " that implements " + iface.interfaceName() + ".\n\nInterface:\n" + iface.code();
    }

    @Override
    public String implementUserMessage(String className, InterfaceResult iface, String vbSource) {
        return "Implement a C# class named EXACTLY " + className
                + " that implements EXACTLY " + iface.interfaceName() + ". "
                + "The class declaration MUST be: public class " + className + " : " + iface.interfaceName() + "\n"
                + "Do NOT use any other class name or interface name.\n"
                + "Match every method signature exactly — same return types, same parameter types.\n"
                + "Only implement the methods declared in the interface below — ignore any other methods in the VB source.\n\n"
                + "Interface:\n" + iface.code()
                + "\n\nOriginal VB.NET behaviour:\n" + vbSource;
    }

    @Override
    public String retryUserMessage(String className, InterfaceResult iface,
                                   String previousCode, String fullTestFile,
                                   List<String> failingTests, String failureDetails,
                                   String failingTestSnippets) {
        String failingList = String.join(", ", failingTests);
        return "Fix this C# class to make ALL tests pass. Do NOT break any currently passing tests.\n"
                + "Class declaration MUST be: public class " + className + " : " + iface.interfaceName() + "\n\n"
                + "FAILING TESTS: " + failingList + "\n\n"
                + "FAILING TESTS (expected vs actual from test runner):\n"
                + failureDetails + "\n"
                + "Failing test source:\n" + failingTestSnippets + "\n\n"
                + "FULL TEST FILE (you must pass ALL of these, not just the failing ones):\n"
                + fullTestFile + "\n"
                + "CURRENT IMPLEMENTATION (fix this):\n" + previousCode + "\n"
                + "INTERFACE:\n" + iface.code();
    }

    // ── Post-processing ──

    @Override
    public String stripWrappers(String code) {
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

    @Override
    public String stripCodeFences(String text) {
        String trimmed = text.trim();
        // If the response starts with a code fence, strip it
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("^```\\w*\\s*", "").replaceAll("\\s*```$", "");
            return trimmed;
        }
        // If Claude returned natural language with an embedded code block, extract it
        Matcher m = Pattern.compile(
                "```(?:csharp|cs)?\\s*\\n(.*?)\\n\\s*```",
                Pattern.DOTALL).matcher(trimmed);
        if (m.find()) {
            return m.group(1).trim();
        }
        // If the response contains "public class" but doesn't start with it,
        // extract from the first "public class" or "using " onwards
        int classIdx = trimmed.indexOf("public class ");
        int usingIdx = trimmed.indexOf("using ");
        int startIdx = -1;
        if (classIdx >= 0 && usingIdx >= 0) startIdx = Math.min(classIdx, usingIdx);
        else if (classIdx >= 0) startIdx = classIdx;
        else if (usingIdx >= 0) startIdx = usingIdx;
        if (startIdx > 0) {
            return trimmed.substring(startIdx).trim();
        }
        return trimmed;
    }

    @Override
    public String fixDeclaration(String code, String className, InterfaceResult iface) {
        // Match: public [sealed|partial|abstract|static] class <AnyName> : <AnyInterface>
        return code.replaceFirst(
                "public\\s+(?:sealed\\s+|partial\\s+|abstract\\s+|static\\s+)*class\\s+\\w+\\s*:\\s*\\w+",
                "public class " + className + " : " + iface.interfaceName());
    }

    @Override
    public String repairTruncated(String code) {
        return PromptPostProcessing.repairTruncatedByBraces(code);
    }

    @Override
    public boolean looksLikeCode(String code) {
        return code.contains("public class ");
    }

    @Override
    public int countTests(String code) {
        return (int) code.lines()
                .filter(line -> line.trim().startsWith("[Test]") || line.trim().startsWith("[TestCase"))
                .count();
    }

    @Override
    public String extractFailingTests(String testCode, List<String> failingTestNames) {
        if (testCode == null || testCode.isBlank() || failingTestNames.isEmpty()) return "";

        String[] lines = testCode.split("\n");
        StringBuilder sb = new StringBuilder();

        for (String testName : failingTestNames) {
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
}
