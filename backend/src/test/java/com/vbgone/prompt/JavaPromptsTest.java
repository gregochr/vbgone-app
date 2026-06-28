package com.vbgone.prompt;

import com.vbgone.model.InterfaceResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JavaPromptsTest {

    private final JavaPrompts prompts = new JavaPrompts();

    private InterfaceResult iface() {
        return new InterfaceResult("s1", "Calculator", "Calculator",
                "package com.vbgone.generated;\n\npublic interface Calculator { }", "CalculatorImpl");
    }

    // ── stripCodeFences ──

    @Test
    void stripCodeFences_stripsJavaFences() {
        String fenced = "```java\npackage com.vbgone.generated;\n\npublic class Foo { }\n```";
        String result = prompts.stripCodeFences(fenced);
        assertThat(result).doesNotContain("```");
        assertThat(result).startsWith("package com.vbgone.generated;");
        assertThat(result).contains("public class Foo");
    }

    @Test
    void stripCodeFences_extractsEmbeddedJavaBlock() {
        String response = """
                Here is the implementation:

                ```java
                package com.vbgone.generated;

                public class FooImpl implements Foo {
                }
                ```

                Done.""";
        String result = prompts.stripCodeFences(response);
        assertThat(result).startsWith("package com.vbgone.generated;");
        assertThat(result).doesNotContain("```");
        assertThat(result).doesNotContain("Here is");
    }

    @Test
    void stripCodeFences_anchorsOnPackageLine() {
        String response = "Sure! Here you go:\n\npackage com.vbgone.generated;\n\npublic class FooImpl implements Foo { }";
        String result = prompts.stripCodeFences(response);
        assertThat(result).startsWith("package com.vbgone.generated;");
        assertThat(result).doesNotContain("Sure!");
    }

    @Test
    void stripCodeFences_anchorsOnPublicClassWhenNoPackage() {
        String response = "Here:\n\npublic class FooImpl implements Foo { }";
        String result = prompts.stripCodeFences(response);
        assertThat(result).startsWith("public class FooImpl");
    }

    // ── stripWrappers ──

    @Test
    void stripWrappers_keepsPackageLine() {
        String code = "package com.vbgone.generated;\n\npublic interface Foo { }";
        assertThat(prompts.stripWrappers(code)).isEqualTo(code.trim());
        assertThat(prompts.stripWrappers(code)).contains("package com.vbgone.generated;");
    }

    // ── fixDeclaration ──

    @Test
    void fixDeclaration_rewritesWrongNamesToImplImplementsInterface() {
        String code = "public class WrongName implements IWrong\n{\n    // body\n}";
        String fixed = prompts.fixDeclaration(code, "Calculator", iface());
        assertThat(fixed).startsWith("public class CalculatorImpl implements Calculator");
        assertThat(fixed).contains("// body");
        assertThat(fixed).doesNotContain("WrongName");
        assertThat(fixed).doesNotContain("IWrong");
    }

    @Test
    void fixDeclaration_leavesCorrectDeclarationUntouched() {
        String code = "public class CalculatorImpl implements Calculator\n{\n}";
        assertThat(prompts.fixDeclaration(code, "Calculator", iface())).isEqualTo(code);
    }

    @Test
    void fixDeclaration_handlesFinalClass() {
        String code = "public final class WrongName implements Wrong { }";
        String fixed = prompts.fixDeclaration(code, "Calculator", iface());
        assertThat(fixed).contains("public class CalculatorImpl implements Calculator");
    }

    // ── countTests ──

    @Test
    void countTests_countsAtTestAnnotations() {
        String code = """
                @Test
                void a() { }

                @Test
                void b() { }

                @BeforeEach
                void setUp() { }

                @Test
                void c() { }
                """;
        assertThat(prompts.countTests(code)).isEqualTo(3);
    }

    // ── looksLikeCode ──

    @Test
    void looksLikeCode_trueForClassOrInterface() {
        assertThat(prompts.looksLikeCode("package x;\npublic class Foo { }")).isTrue();
        assertThat(prompts.looksLikeCode("public interface Foo { }")).isTrue();
    }

    @Test
    void looksLikeCode_falseForProse() {
        assertThat(prompts.looksLikeCode("I cannot generate this implementation.")).isFalse();
    }

    // ── extractFailingTests ──

    @Test
    void extractFailingTests_capturesJavaMethodBody() {
        String testCode = """
                package com.vbgone.generated;

                class CalculatorTest {
                    @Test
                    void add_returnsSum() {
                        assertEquals(5, sut.add(2, 3));
                    }

                    @Test
                    void subtract_returnsDifference() {
                        assertEquals(2, sut.subtract(5, 3));
                    }
                }""";

        String result = prompts.extractFailingTests(testCode, List.of("subtract_returnsDifference"));

        assertThat(result).contains("subtract_returnsDifference");
        assertThat(result).contains("sut.subtract(5, 3)");
        assertThat(result).contains("@Test");
        assertThat(result).doesNotContain("add_returnsSum");
    }

    @Test
    void extractFailingTests_returnsEmptyWhenNoNames() {
        String testCode = "class FooTest { @Test void a() { } }";
        assertThat(prompts.extractFailingTests(testCode, List.of())).isEmpty();
    }

    @Test
    void countTests_zeroForCSharpStyle() {
        // C# [Test] attributes must NOT be counted by the Java strategy.
        String code = "[Test]\npublic void A() { }";
        assertThat(prompts.countTests(code)).isZero();
    }
}
