package com.vbgone.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    /**
     * Endpoint families that trigger expensive work (Claude calls, {@code dotnet test},
     * mutation runs) and so are rate limited. Assure/mutation cost as much as migrate and
     * were previously left unprotected.
     */
    private static final List<String> RATE_LIMITED_PREFIXES = List.of("/api/migrate/", "/api/assure/");

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        boolean rateLimited = RATE_LIMITED_PREFIXES.stream().anyMatch(uri::startsWith);
        // The mutation job-status endpoint (GET /api/assure/mutation-test/{jobId}) is polled
        // repeatedly by the UI while a job runs, so it must stay unlimited even though it lives
        // under /api/assure/. The POST that starts the job is still rate limited.
        boolean mutationStatusPoll = "GET".equalsIgnoreCase(request.getMethod())
                && uri.startsWith("/api/assure/mutation-test/");
        return !rateLimited || mutationStatusPoll;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        Bucket bucket = buckets.computeIfAbsent(ip, k -> createBucket());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Rate limit exceeded. VBGone allows 100 migrations per hour per IP address.\"}");
        }
    }

    private Bucket createBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.simple(100, Duration.ofHours(1)))
                .build();
    }
}
