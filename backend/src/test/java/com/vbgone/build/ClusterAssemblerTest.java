package com.vbgone.build;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterAssemblerTest {

    private static String cls(String name, String body) {
        return "Public Class " + name + "\n" + body + "\nEnd Class";
    }

    @Test
    void selfContainedClass_returnsTargetOnly() {
        Map<String, String> candidates = Map.of(
                "Ledger", cls("Ledger", "  Public Function Net() As Decimal\n    Return 0D\n  End Function"),
                "Unrelated", cls("Unrelated", ""));

        String result = ClusterAssembler.assemble("Ledger", candidates.get("Ledger"), candidates);

        assertThat(result).contains("Public Class Ledger").doesNotContain("Unrelated");
    }

    @Test
    void directSiblingReference_isPulledIn() {
        // A LINQ-to-SQL entity with an EntitySet(Of OrderLine) association.
        Map<String, String> candidates = Map.of(
                "Order", cls("Order", "  Public Property Lines As EntitySet(Of OrderLine)"),
                "OrderLine", cls("OrderLine", "  Public Property Sku As String"),
                "Customer", cls("Customer", ""));

        String result = ClusterAssembler.assemble("Order", candidates.get("Order"), candidates);

        assertThat(result).contains("Public Class Order").contains("Public Class OrderLine");
        // Customer isn't referenced, so it stays out.
        assertThat(result).doesNotContain("Public Class Customer");
    }

    @Test
    void transitiveReferences_areFollowed() {
        Map<String, String> candidates = Map.of(
                "A", cls("A", "  Dim b As B"),
                "B", cls("B", "  Dim c As C"),
                "C", cls("C", "  Public X As Integer"),
                "D", cls("D", ""));

        String result = ClusterAssembler.assemble("A", candidates.get("A"), candidates);

        assertThat(result).contains("Public Class A").contains("Public Class B").contains("Public Class C");
        assertThat(result).doesNotContain("Public Class D");
    }

    @Test
    void targetLeadsTheFile() {
        Map<String, String> candidates = new LinkedHashMap<>();
        candidates.put("Sibling", cls("Sibling", ""));
        candidates.put("Target", cls("Target", "  Dim s As Sibling"));

        String result = ClusterAssembler.assemble("Target", candidates.get("Target"), candidates);

        assertThat(result.indexOf("Public Class Target")).isLessThan(result.indexOf("Public Class Sibling"));
    }

    @Test
    void matchesWholeWordsOnly_notSubstrings() {
        Map<String, String> candidates = Map.of(
                "Ledger", cls("Ledger", "  Dim a As Account"),
                "Account", cls("Account", "  Public Balance As Decimal"),
                "AccountingPeriod", cls("AccountingPeriod", "")); // substring of nothing in Ledger

        String result = ClusterAssembler.assemble("Ledger", candidates.get("Ledger"), candidates);

        assertThat(result).contains("Public Class Account\n");
        assertThat(result).doesNotContain("AccountingPeriod");
    }

    @Test
    void blankCandidateSources_areNotPulled() {
        Map<String, String> candidates = new HashMap<>();
        candidates.put("Target", cls("Target", "  Dim g As Ghost"));
        candidates.put("Ghost", "   "); // referenced but has no usable source

        String result = ClusterAssembler.assemble("Target", candidates.get("Target"), candidates);

        assertThat(result).contains("Public Class Target");
        assertThat(result.trim()).isEqualTo(cls("Target", "  Dim g As Ghost"));
    }

    @Test
    void clusterSizeIsCapped() {
        // A 30-long reference chain C0 -> C1 -> ... -> C29; the cap must stop it at MAX_CLUSTER.
        Map<String, String> candidates = new HashMap<>();
        for (int i = 0; i < 30; i++) {
            candidates.put("C" + i, cls("C" + i, "  Dim x As C" + (i + 1)));
        }

        String result = ClusterAssembler.assemble("C0", candidates.get("C0"), candidates);

        long classes = result.split("Public Class ", -1).length - 1;
        assertThat(classes).isEqualTo(ClusterAssembler.MAX_CLUSTER);
    }
}
