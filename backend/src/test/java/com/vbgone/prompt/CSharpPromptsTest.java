package com.vbgone.prompt;

import com.vbgone.model.InterfaceResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct coverage for the C# prompt strategy's post-processors. These assertions were
 * previously reachable only through back-compat delegators on {@code GenerationService};
 * they now exercise {@link CSharpPrompts} directly.
 */
class CSharpPromptsTest {

    private final CSharpPrompts prompts = new CSharpPrompts();

    private InterfaceResult iface(String className, String interfaceName) {
        return new InterfaceResult("", className, interfaceName, "");
    }

    // ── stripCodeFences ──

    @Test
    void stripCodeFences_stripsBacktickFences() {
        String fenced = "```csharp\npublic class Foo : IFoo\n{\n}\n```";
        String result = prompts.stripCodeFences(fenced);
        assertThat(result).doesNotContain("```");
        assertThat(result).startsWith("public class Foo");
    }

    @Test
    void stripCodeFences_extractsEmbeddedCodeBlock() {
        String response = """
                Looking at the failing tests, the issue is with shipping thresholds.

                ```csharp
                public class OrderValidator : IOrderValidator
                {
                    public string ValidateOrder(string n, string a, string q) => "OK";
                }
                ```

                This should fix the shipping tests.""";
        String result = prompts.stripCodeFences(response);
        assertThat(result).startsWith("public class OrderValidator");
        assertThat(result).doesNotContain("```");
        assertThat(result).doesNotContain("Looking at");
    }

    @Test
    void stripCodeFences_extractsCodeFromNaturalLanguagePreamble() {
        String response = "Here is the fixed implementation:\n\npublic class Foo : IFoo\n{\n    public int Bar() => 42;\n}";
        String result = prompts.stripCodeFences(response);
        assertThat(result).startsWith("public class Foo");
        assertThat(result).doesNotContain("Here is");
    }

    @Test
    void stripCodeFences_anchorsOnUsingWhenBeforePublicClass() {
        String response = "Sure:\n\nusing NUnit.Framework;\n\npublic class FooTests\n{\n}";
        String result = prompts.stripCodeFences(response);
        assertThat(result).startsWith("using NUnit.Framework;");
        assertThat(result).doesNotContain("Sure:");
    }

    // ── countTests ──

    @Test
    void countTests_countsTestAndTestCaseAttributes() {
        String code = """
                [Test]
                public void A() { }
                [TestCase(1)]
                [TestCase(2)]
                public int B(int x) { return x; }
                [Test]
                public void C() { }
                """;
        assertThat(prompts.countTests(code)).isEqualTo(4);
    }

    // ── countMsTests (Assure) ──

    @Test
    void countMsTests_countsTestMethodAndDataTestMethod() {
        String code = """
                [TestMethod]
                public void A() { }
                [DataTestMethod]
                public void B() { }
                [TestMethod]
                public void C() { }
                """;
        assertThat(prompts.countMsTests(code)).isEqualTo(3);
    }

    // ── extractFailingTests ──

    @Test
    void extractFailingTests_extractsMatchingTestMethod() {
        String testCode = """
                [TestFixture]
                public class Form1Tests
                {
                    [Test]
                    public void Add_ReturnsSum()
                    {
                        Assert.That(_sut.Add(2, 3), Is.EqualTo(5));
                    }

                    [Test]
                    public void Subtract_ReturnsDiff()
                    {
                        Assert.That(_sut.Subtract(5, 3), Is.EqualTo(2));
                    }
                }""";

        String result = prompts.extractFailingTests(testCode, List.of("Subtract_ReturnsDiff"));

        assertThat(result).contains("Subtract_ReturnsDiff");
        assertThat(result).contains("Is.EqualTo(2)");
        assertThat(result).contains("[Test]");
        assertThat(result).doesNotContain("Add_ReturnsSum");
    }

    @Test
    void extractFailingTests_returnsEmptyWhenNoTestCodeOrNames() {
        assertThat(prompts.extractFailingTests("", List.of("Foo"))).isEmpty();
        assertThat(prompts.extractFailingTests("[Test]\npublic void A() { }", List.of())).isEmpty();
    }

    // ── repairTruncated ──

    @Test
    void repairTruncated_closesUnclosedBracesAndRemovesIncompleteMethod() {
        String truncated = """
                [TestFixture]
                public class FooTests
                {
                    [Test]
                    public void A() { Assert.Pass(); }

                    [Test]
                    public void B()
                    {
                        Assert.That(_s""";

        String repaired = prompts.repairTruncated(truncated);

        assertThat(repaired).doesNotContain("Assert.That(_s");
        assertThat(repaired).endsWith("}");
        long opens = repaired.chars().filter(c -> c == '{').count();
        long closes = repaired.chars().filter(c -> c == '}').count();
        assertThat(opens).isEqualTo(closes);
    }

    @Test
    void repairTruncated_leavesBalancedCodeUntouched() {
        String balanced = "[TestFixture]\npublic class FooTests\n{\n    [Test]\n    public void A() { Assert.Pass(); }\n}";
        assertThat(prompts.repairTruncated(balanced)).isEqualTo(balanced);
    }

    // ── stripWrappers ──

    @Test
    void stripWrappers_removesBlockNamespace() {
        String code = "namespace Foo\n{\n    public interface IFoo { }\n}";
        assertThat(prompts.stripWrappers(code)).isEqualTo("public interface IFoo { }");
    }

    @Test
    void stripWrappers_removesFileScopedNamespace() {
        String code = "namespace Foo;\n\npublic interface IFoo { }";
        assertThat(prompts.stripWrappers(code)).isEqualTo("public interface IFoo { }");
    }

    @Test
    void stripWrappers_leavesCodeWithoutNamespaceUntouched() {
        String code = "public interface IFoo { }";
        assertThat(prompts.stripWrappers(code)).isEqualTo("public interface IFoo { }");
    }

    // ── fixDeclaration ──

    @Test
    void fixDeclaration_correctsWrongClassAndInterface() {
        String code = "public class OrderCalculationService : IOrderCalculationService\n{\n    // body\n}";
        String fixed = prompts.fixDeclaration(code, "OrderConstants", iface("OrderConstants", "IOrderConstants"));
        assertThat(fixed).startsWith("public class OrderConstants : IOrderConstants");
        assertThat(fixed).contains("// body");
    }

    @Test
    void fixDeclaration_leavesCorrectCodeUntouched() {
        String code = "public class OrderConstants : IOrderConstants\n{\n    // body\n}";
        String fixed = prompts.fixDeclaration(code, "OrderConstants", iface("OrderConstants", "IOrderConstants"));
        assertThat(fixed).isEqualTo(code);
    }

    @Test
    void fixDeclaration_handlesExtraWhitespace() {
        String code = "public  class  Foo  :  IBar\n{\n}";
        String fixed = prompts.fixDeclaration(code, "MyClass", iface("MyClass", "IMyClass"));
        assertThat(fixed).contains("public class MyClass : IMyClass");
    }

    @Test
    void fixDeclaration_handlesSealedClass() {
        String code = "public sealed class WrongName : IWrong\n{\n}";
        String fixed = prompts.fixDeclaration(code, "OrderValidator", iface("OrderValidator", "IOrderValidator"));
        assertThat(fixed).contains("public class OrderValidator : IOrderValidator");
    }

    @Test
    void fixDeclaration_handlesPartialClass() {
        String code = "public partial class WrongName : IWrong\n{\n}";
        String fixed = prompts.fixDeclaration(code, "OrderValidator", iface("OrderValidator", "IOrderValidator"));
        assertThat(fixed).contains("public class OrderValidator : IOrderValidator");
    }

    // ── looksLikeCode ──

    @Test
    void looksLikeCode_trueForPublicClass_falseForProse() {
        assertThat(prompts.looksLikeCode("public class Foo { }")).isTrue();
        assertThat(prompts.looksLikeCode("I cannot generate this implementation.")).isFalse();
    }
}
