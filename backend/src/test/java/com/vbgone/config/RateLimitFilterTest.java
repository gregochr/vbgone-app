package com.vbgone.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private RateLimitFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        filterChain = mock(FilterChain.class);
    }

    @Test
    void allowsRequestsUnderLimit() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/migrate/analyse");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void returns429AfterLimitExceeded() throws ServletException, IOException {
        for (int i = 0; i < 100; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/migrate/analyse");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, filterChain);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/migrate/analyse");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("Rate limit exceeded");
        assertThat(response.getContentAsString()).contains("100 migrations per hour per IP address");
    }

    @Test
    void tracksSeparateBucketsPerIP() throws ServletException, IOException {
        // Exhaust limit for IP 1
        for (int i = 0; i < 100; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/migrate/analyse");
            request.setRemoteAddr("192.168.1.1");
            filter.doFilter(request, new MockHttpServletResponse(), filterChain);
        }

        // IP 2 should still be allowed
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/migrate/analyse");
        request.setRemoteAddr("192.168.1.2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doesNotFilterNonMigrateEndpoints() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void rateLimitsAssureEndpoints() throws ServletException, IOException {
        for (int i = 0; i < 100; i++) {
            MockHttpServletResponse ok = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("POST", "/api/assure/baseline-tests"), ok, filterChain);
            assertThat(ok.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/assure/baseline-tests"), blocked, filterChain);

        assertThat(blocked.getStatus()).isEqualTo(429);
    }

    @Test
    void rateLimitsMutationJobStart() throws ServletException, IOException {
        for (int i = 0; i < 100; i++) {
            filter.doFilter(new MockHttpServletRequest("POST", "/api/assure/mutation-test"),
                    new MockHttpServletResponse(), filterChain);
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/assure/mutation-test"), blocked, filterChain);

        assertThat(blocked.getStatus()).isEqualTo(429);
    }

    @Test
    void doesNotRateLimitMutationStatusPolling() throws ServletException, IOException {
        // Exhaust the IP's bucket via another rate-limited endpoint (buckets are per-IP).
        for (int i = 0; i < 100; i++) {
            filter.doFilter(new MockHttpServletRequest("POST", "/api/assure/baseline-tests"),
                    new MockHttpServletResponse(), filterChain);
        }

        // The polled job-status GET must still pass — it is skipped, never consulting the bucket.
        MockHttpServletRequest poll = new MockHttpServletRequest("GET", "/api/assure/mutation-test/job-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(poll, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(poll, response);
    }

    @Test
    void returns429WithJsonContentType() throws ServletException, IOException {
        for (int i = 0; i < 100; i++) {
            filter.doFilter(
                    new MockHttpServletRequest("POST", "/api/migrate/analyse"),
                    new MockHttpServletResponse(), filterChain);
        }

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/migrate/analyse");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, filterChain);

        assertThat(response.getContentType()).isEqualTo("application/json");
    }
}
