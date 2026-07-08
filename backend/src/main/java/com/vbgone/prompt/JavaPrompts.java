package com.vbgone.prompt;

import com.vbgone.model.InterfaceResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java / JUnit 5 prompt strategy. Mirrors {@link CSharpPrompts} but targets the Java
 * idioms the {@code JavaRuntime} scaffolds: {@code package com.vbgone.generated;},
 * plain (non-{@code I}-prefixed) interface, a separate {@code <Class>Impl} class,
 * JUnit 5 assertions, and {@code ArithmeticException} for integer divide-by-zero.
 */
@Component
public class JavaPrompts implements PromptLanguage {

    static final String PACKAGE = "com.vbgone.generated";
    private static final String PACKAGE_LINE = "package " + PACKAGE + ";";

    static final String INTERFACE_SYSTEM_PROMPT = """
            You are a VB.NET to Java migration expert. Generate a Java interface from VB.NET source \
            code. Extract only public business logic methods — ignore all UI, event handlers, and \
            Windows Forms concerns.

            The interface MUST NOT use an 'I' prefix — the type name is exactly the class name from \
            the user message. The interface declares public method signatures only: no bodies, no \
            'default' methods, no fields.

            Use appropriate return types for mathematical operations. Integer division that can \
            produce a non-integer result must return double, not int, to preserve decimal precision. \
            Consider the semantics of each method — if the operation can produce a fractional result, \
            use double.

            IMPORTANT: The FIRST line of the file MUST be exactly:
            package com.vbgone.generated;

            Return only raw Java code. No markdown. No backticks. No explanation. \
            The response will be written directly to a .java file.""";

    static final String TESTS_SYSTEM_PROMPT = """
            You are a VB.NET to Java migration expert and TDD practitioner. Generate a comprehensive \
            JUnit 5 test suite in Java based on VB.NET source code behaviour. Tests must cover happy \
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
            calculation path. Every test MUST be satisfiable by a correct implementation of \
            the interface — never assert behaviour the interface cannot express.

            CRITICAL JUnit 5 IDIOMS (these differ from NUnit — get them exactly right):
            - assertEquals(expected, actual) — the EXPECTED value comes FIRST, the actual SECOND \
            (this is the OPPOSITE order from NUnit's Assert.That).
            - For double/floating-point results, ALWAYS use a delta: \
            assertEquals(expected, actual, 0.0001).
            - Integer divide-by-zero in Java throws java.lang.ArithmeticException (NOT C#'s \
            DivideByZeroException). Assert it with: \
            assertThrows(ArithmeticException.class, () -> sut.divide(1, 0)).

            Generate ONLY the JUnit 5 test class. Do NOT include the interface definition or the \
            implementation class in the test file. Use the interface type for the field and \
            instantiate the implementation class (<className>Impl) in the @BeforeEach method.

            The FIRST line of the file MUST be exactly:
            package com.vbgone.generated;

            Then the imports:
            import org.junit.jupiter.api.*;
            import static org.junit.jupiter.api.Assertions.*;

            The test class MUST follow this structure exactly:
            class <className>Test {
                private <className> sut;

                @BeforeEach
                void setUp() {
                    sut = new <className>Impl();
                }

                @Test
                void someBehaviour_returnsExpected() {
                    // arrange / act / assert
                }
            }

            Return only raw Java code. No markdown. No backticks. No explanation.""";

    static final String STUB_SYSTEM_PROMPT = """
            You are a Java developer. Generate a Java class that implements a given interface. \
            The class name is <className>Impl and it implements the interface named in the user \
            message. Every method body must be exactly: throw new UnsupportedOperationException(); \
            Do NOT implement any logic — every single method must throw UnsupportedOperationException. \
            The FIRST line of the file MUST be exactly:
            package com.vbgone.generated;

            Return only raw Java code. No markdown. No backticks. No explanation.""";

    static final String IMPLEMENT_SYSTEM_PROMPT = """
            You are a VB.NET to Java migration expert. Generate a complete Java implementation of an \
            interface based on VB.NET source behaviour. The implementation class name and interface \
            name are specified in the user message — use EXACTLY those names. Write idiomatic, modern \
            Java 21.

            The class declaration MUST be exactly:
            public class <className>Impl implements <className>
            (the interface has NO 'I' prefix; the implementation class ends with 'Impl').

            CRITICAL: You MUST match every method signature EXACTLY as declared in the Java interface \
            — same return types, same parameter types. If the interface declares \
            'double divide(int a, int b)' then the implementation MUST be \
            'public double divide(int a, int b)'. Changing a return type will cause compilation errors.

            When implementing divide methods, check for division by zero and throw \
            ArithmeticException: if (b == 0) throw new ArithmeticException("Cannot divide by zero."); \
            Java already throws ArithmeticException on integer divide-by-zero, so an explicit throw \
            keeps the message clear and consistent.

            The FIRST line of the file MUST be exactly:
            package com.vbgone.generated;

            Return ONLY the complete Java class. No markdown. No backticks. No explanation. \
            No analysis. No discussion. No test code. Just the package line followed by the class \
            starting with 'public class'.""";

    @Override
    public String id() {
        return "java";
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
        return "Generate a Java interface named EXACTLY " + className
                + " (no 'I' prefix) in package " + PACKAGE + " for this VB.NET:\n" + vbSource;
    }

    @Override
    public String testsUserMessage(String className, String interfaceCode, String vbSource) {
        return "Generate JUnit 5 tests for the interface " + className
                + " based on this VB.NET:\n" + vbSource
                + "\n\nJava interface (ONLY test methods defined here — do NOT invent extra methods):\n"
                + interfaceCode
                + "\n\nThe implementation class name is " + className + "Impl"
                + ", the interface is " + className + " (no 'I' prefix)."
                + "\n\nThe test file should follow this structure exactly:\n"
                + PACKAGE_LINE + "\n\n"
                + "import org.junit.jupiter.api.*;\n"
                + "import static org.junit.jupiter.api.Assertions.*;\n\n"
                + "class " + className + "Test {\n"
                + "    private " + className + " sut;\n\n"
                + "    @BeforeEach\n"
                + "    void setUp() {\n"
                + "        sut = new " + className + "Impl();\n"
                + "    }\n\n"
                + "    // @Test methods here\n"
                + "}";
    }

    @Override
    public String stubUserMessage(String className, InterfaceResult iface) {
        return "Generate a stub class named " + iface.implName()
                + " that implements " + iface.interfaceName()
                + " in package " + PACKAGE + ".\n\nInterface:\n" + iface.code();
    }

    @Override
    public String implementUserMessage(String className, InterfaceResult iface, String vbSource) {
        return "Implement a Java class named EXACTLY " + iface.implName()
                + " that implements EXACTLY " + iface.interfaceName() + ". "
                + "The class declaration MUST be: public class " + iface.implName()
                + " implements " + iface.interfaceName() + "\n"
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
        return "Fix this Java class to make ALL tests pass. Do NOT break any currently passing tests.\n"
                + "Class declaration MUST be: public class " + iface.implName()
                + " implements " + iface.interfaceName() + "\n\n"
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
        // Java keeps the package line — there is nothing to unwrap. Just normalise.
        return code.trim();
    }

    @Override
    public String stripCodeFences(String text) {
        String trimmed = text.trim();
        // If the response starts with a code fence, strip it
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("^```\\w*\\s*", "").replaceAll("\\s*```$", "");
            return trimmed;
        }
        // If the model returned natural language with an embedded java code block, extract it
        Matcher m = Pattern.compile(
                "```(?:java)?\\s*\\n(.*?)\\n\\s*```",
                Pattern.DOTALL).matcher(trimmed);
        if (m.find()) {
            return m.group(1).trim();
        }
        // Otherwise anchor extraction on the first real code marker, preferring the
        // package line so the whole compilation unit is preserved.
        int startIdx = firstIndexOf(trimmed, "package ", "public class ", "public interface ", "class ", "interface ");
        if (startIdx > 0) {
            return trimmed.substring(startIdx).trim();
        }
        return trimmed;
    }

    private static int firstIndexOf(String text, String... markers) {
        int best = -1;
        for (String marker : markers) {
            int idx = text.indexOf(marker);
            if (idx >= 0 && (best < 0 || idx < best)) {
                best = idx;
            }
        }
        return best;
    }

    @Override
    public String fixDeclaration(String code, String className, InterfaceResult iface) {
        // Match: public [sealed|final|abstract]* class <AnyName> implements <AnyInterface>
        return code.replaceFirst(
                "public\\s+(?:sealed\\s+|final\\s+|abstract\\s+)*class\\s+\\w+\\s+implements\\s+\\w+",
                "public class " + iface.implName() + " implements " + iface.interfaceName());
    }

    @Override
    public String repairTruncated(String code) {
        return PromptPostProcessing.repairTruncatedByBraces(code);
    }

    @Override
    public boolean looksLikeCode(String code) {
        return code.contains("class ") || code.contains("interface ");
    }

    @Override
    public int countTests(String code) {
        return (int) code.lines()
                .filter(line -> line.trim().startsWith("@Test"))
                .count();
    }

    @Override
    public String extractFailingTests(String testCode, List<String> failingTestNames) {
        if (testCode == null || testCode.isBlank() || failingTestNames.isEmpty()) return "";

        String[] lines = testCode.split("\n");
        StringBuilder sb = new StringBuilder();

        for (String testName : failingTestNames) {
            for (int i = 0; i < lines.length; i++) {
                // JUnit 5 test methods: `void <name>(` (possibly with modifiers in front).
                if (lines[i].contains("void " + testName + "(")
                        || lines[i].contains(testName + "(") && lines[i].contains("void ")) {
                    // Walk back to include a preceding @Test (and any annotations) line.
                    int start = i;
                    while (start > 0 && lines[start - 1].trim().startsWith("@")) {
                        start--;
                    }

                    // Walk forward to the matching closing brace.
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
