package com.vbgone.service;

import com.vbgone.model.CostResult;
import com.vbgone.model.MigrationSession;
import com.vbgone.model.TokenUsage;
import com.vbgone.session.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CostServiceTest {

    @Mock
    private SessionStore sessionStore;

    private CostService costService;

    @BeforeEach
    void setUp() {
        costService = new CostService(sessionStore);
    }

    // ── calculateCost — Sonnet pricing ──

    @Test
    void calculateCost_sonnetInputTokens() {
        // 1M input tokens at $3/M = $3.00
        double cost = CostService.calculateCost("claude-sonnet-4-6", 1_000_000, 0);
        assertThat(cost).isEqualTo(3.0);
    }

    @Test
    void calculateCost_sonnetOutputTokens() {
        // 1M output tokens at $15/M = $15.00
        double cost = CostService.calculateCost("claude-sonnet-4-6", 0, 1_000_000);
        assertThat(cost).isEqualTo(15.0);
    }

    @Test
    void calculateCost_sonnetMixed() {
        // 200 input at $3/M + 100 output at $15/M
        double cost = CostService.calculateCost("claude-sonnet-4-6", 200, 100);
        double expected = (200 * 3.0 + 100 * 15.0) / 1_000_000.0;
        assertThat(cost).isCloseTo(expected, within(1e-10));
    }

    // ── calculateCost — Haiku pricing ──

    @Test
    void calculateCost_haikuInputTokens() {
        // 1M input tokens at $0.80/M = $0.80
        double cost = CostService.calculateCost("claude-haiku-4-5-20251001", 1_000_000, 0);
        assertThat(cost).isEqualTo(0.80);
    }

    @Test
    void calculateCost_haikuOutputTokens() {
        // 1M output tokens at $4/M = $4.00
        double cost = CostService.calculateCost("claude-haiku-4-5-20251001", 0, 1_000_000);
        assertThat(cost).isEqualTo(4.0);
    }

    @Test
    void calculateCost_haikuMixed() {
        // 500 input at $0.80/M + 250 output at $4/M
        double cost = CostService.calculateCost("claude-haiku-4-5-20251001", 500, 250);
        double expected = (500 * 0.80 + 250 * 4.0) / 1_000_000.0;
        assertThat(cost).isCloseTo(expected, within(1e-10));
    }

    // ── calculateCost — edge cases ──

    @Test
    void calculateCost_zeroTokensReturnsZero() {
        assertThat(CostService.calculateCost("claude-sonnet-4-6", 0, 0)).isEqualTo(0.0);
        assertThat(CostService.calculateCost("claude-haiku-4-5-20251001", 0, 0)).isEqualTo(0.0);
    }

    @Test
    void calculateCost_unknownModelFallsBackToSonnetPricing() {
        double cost = CostService.calculateCost("claude-opus-4-6", 1_000_000, 0);
        assertThat(cost).isEqualTo(3.0);
    }

    // ── getCost ──

    @Test
    void getCost_returnsEmptyStepsForNewSession() {
        MigrationSession session = new MigrationSession("s1");
        when(sessionStore.get("s1")).thenReturn(Optional.of(session));

        CostResult result = costService.getCost("s1");

        assertThat(result.sessionId()).isEqualTo("s1");
        assertThat(result.steps()).isEmpty();
        assertThat(result.totalCost()).isEqualTo(0.0);
    }

    @Test
    void getCost_sumsCostAcrossMultipleSteps() {
        MigrationSession session = new MigrationSession("s1");
        session.addTokenUsage(new TokenUsage("analyse", "claude-sonnet-4-6", 200, 100, 0.0021));
        session.addTokenUsage(new TokenUsage("interface", "claude-haiku-4-5-20251001", 150, 75, 0.00042));

        when(sessionStore.get("s1")).thenReturn(Optional.of(session));

        CostResult result = costService.getCost("s1");

        assertThat(result.sessionId()).isEqualTo("s1");
        assertThat(result.steps()).hasSize(2);
        assertThat(result.totalCost()).isCloseTo(0.00252, within(1e-10));
    }

    @Test
    void getCost_throwsWhenSessionNotFound() {
        when(sessionStore.get("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> costService.getCost("bad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session not found: bad");
    }
}
