package com.vbgone.mutation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VbMutatorTest {

    private final VbMutator mutator = new VbMutator();

    private List<Mutant> boundaryRelational(List<Mutant> ms) {
        return ms.stream().filter(m -> m.operator().equals("relational") && m.before().equals("<=")).toList();
    }

    @Test
    void emptyForNullOrBlank() {
        assertThat(mutator.generate(null)).isEmpty();
        assertThat(mutator.generate("   \n  ")).isEmpty();
    }

    @Test
    void flipsRelationalBoundary() {
        List<Mutant> ms = mutator.generate("If x <= 100 Then Return 1\n");

        Mutant boundary = boundaryRelational(ms).get(0);
        assertThat(boundary.before()).isEqualTo("<=");
        assertThat(boundary.after()).isEqualTo("<");
        assertThat(boundary.line()).isEqualTo(1);
        assertThat(boundary.mutatedSource()).isEqualTo("If x < 100 Then Return 1\n");
    }

    @Test
    void distinguishes100From1000_theSpikeBug() {
        // A substring/sed mutator would hit "<= 1000" when targeting "<= 100". Token positions don't.
        String src = "If a <= 100 Then\nIf b <= 1000 Then\n";
        List<Mutant> boundary = boundaryRelational(mutator.generate(src));

        assertThat(boundary).hasSize(2);
        Mutant onLine1 = boundary.stream().filter(m -> m.line() == 1).findFirst().orElseThrow();
        Mutant onLine2 = boundary.stream().filter(m -> m.line() == 2).findFirst().orElseThrow();

        // Mutating the 100 line leaves the 1000 line untouched, and vice-versa.
        assertThat(onLine1.mutatedSource()).contains("If a < 100 Then").contains("If b <= 1000 Then");
        assertThat(onLine2.mutatedSource()).contains("If a <= 100 Then").contains("If b < 1000 Then");
    }

    @Test
    void mutatesBooleanConnectivesAsWholeWords() {
        List<Mutant> ms = mutator.generate("If a AndAlso b Then\n");

        assertThat(ms).anyMatch(m -> m.operator().equals("boolean-connective")
                && m.before().equals("AndAlso") && m.after().equals("OrElse"));
    }

    @Test
    void doesNotMatchBooleanOpInsideAnIdentifier() {
        // "Order" contains "Or"; "AndAlso" contains "And" — neither should be mutated as a connective.
        assertThat(mutator.generate("Dim Order As Integer\n"))
                .noneMatch(m -> m.operator().equals("boolean-connective"));
        assertThat(mutator.generate("If a AndAlso b Then\n"))
                .noneMatch(m -> m.before().equalsIgnoreCase("and"));
    }

    @Test
    void mutatesArithmetic() {
        assertThat(mutator.generate("Return a + b\n"))
                .anyMatch(m -> m.operator().equals("arithmetic") && m.before().equals("+") && m.after().equals("-"));
    }

    @Test
    void mutatesIntegerLiteralsOffByOne() {
        assertThat(mutator.generate("Dim n = 100\n"))
                .anyMatch(m -> m.operator().equals("boundary-literal")
                        && m.before().equals("100") && m.after().equals("101"));
    }

    @Test
    void ignoresContentInsideStringLiterals() {
        // The <=, And and 100 all live inside the string — none should be mutated.
        List<Mutant> ms = mutator.generate("Dim s As String = \"score <= 100 And done\"\n");
        assertThat(ms).isEmpty();
    }

    @Test
    void handlesEscapedQuotesInStrings() {
        // The "" is an escaped quote; the string runs to the final quote, so <= stays masked.
        List<Mutant> ms = mutator.generate("Dim s = \"a \"\"quote\"\" and x <= 5\"\n");
        assertThat(ms).isEmpty();
    }

    @Test
    void ignoresContentInComments() {
        // Before the comment: only the literal 1 is mutable. Everything after ' is masked.
        List<Mutant> ms = mutator.generate("Dim x = 1 ' if x <= 5 AndAlso y then\n");

        assertThat(ms).allMatch(m -> m.line() == 1);
        assertThat(ms).noneMatch(m -> m.before().equals("<=") || m.before().equalsIgnoreCase("andalso"));
        assertThat(ms).anyMatch(m -> m.operator().equals("boundary-literal") && m.before().equals("1"));
    }

    @Test
    void everyMutantChangesExactlyOneTokenAndIsOtherwiseIdentical() {
        String src = "If x <= 100 Then Return 1\n";
        for (Mutant m : mutator.generate(src)) {
            // Same length delta as the token swap; identical outside the single replaced span.
            assertThat(m.mutatedSource()).isNotEqualTo(src);
            int expectedLen = src.length() - m.before().length() + m.after().length();
            assertThat(m.mutatedSource()).hasSize(expectedLen);
        }
    }
}
