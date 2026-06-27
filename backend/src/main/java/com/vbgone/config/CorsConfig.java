package com.vbgone.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    // Origin *patterns* (allowedOriginPatterns supports `:*` wildcards even with
    // allowCredentials=true). Override at deploy time with CORS_ALLOWED_ORIGINS
    // (comma-separated); blank/unset falls back to these defaults.
    private static final List<String> DEFAULT_ORIGIN_PATTERNS = List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "http://100.76.73.16:*",  // dockermacmini — Tailscale
            "http://192.168.0.102:*", // dockermacmini — LAN
            "https://vbgone.online"
    );

    @Value("${CORS_ALLOWED_ORIGINS:}")
    private String allowedOriginsCsv;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(resolveOriginPatterns());
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }

    private List<String> resolveOriginPatterns() {
        if (allowedOriginsCsv == null || allowedOriginsCsv.isBlank()) {
            return DEFAULT_ORIGIN_PATTERNS;
        }
        return Arrays.stream(allowedOriginsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
