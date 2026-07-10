package com.vbgone.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    // Mirrors CorsConfig.DEFAULT_ORIGIN_PATTERNS (in declared order).
    private static final List<String> DEFAULT_ORIGIN_PATTERNS = List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "http://100.76.73.16:*",
            "http://192.168.0.102:*",
            "https://vbgone.online"
    );

    // --- Origin pattern parsing / fallback ----------------------------------

    @Test
    void fallsBackToDefaultsWhenUnset() {
        // Field never injected -> allowedOriginsCsv == null branch.
        assertThat(apiCorsConfig(new CorsConfig()).getAllowedOriginPatterns())
                .containsExactlyElementsOf(DEFAULT_ORIGIN_PATTERNS);
    }

    @Test
    void fallsBackToDefaultsWhenBlank() {
        assertThat(apiCorsConfig(configWith("")).getAllowedOriginPatterns())
                .containsExactlyElementsOf(DEFAULT_ORIGIN_PATTERNS);
        assertThat(apiCorsConfig(configWith("   ")).getAllowedOriginPatterns())
                .containsExactlyElementsOf(DEFAULT_ORIGIN_PATTERNS);
    }

    @Test
    void parsesCsvTrimmingAndDroppingEmptyEntries() {
        // Leading/trailing spaces, a consecutive-comma empty token, and a
        // whitespace-only token must all be trimmed away, order preserved.
        CorsConfig config = configWith(" https://a.example , https://b.example ,, ,  https://c.example ");
        assertThat(apiCorsConfig(config).getAllowedOriginPatterns())
                .containsExactly("https://a.example", "https://b.example", "https://c.example");
    }

    @Test
    void overrideReplacesDefaultsRatherThanAppending() {
        CorsConfiguration cors = apiCorsConfig(configWith("https://only.example"));
        assertThat(cors.getAllowedOriginPatterns())
                .containsExactly("https://only.example")
                .doesNotContain("https://vbgone.online");
    }

    // --- corsFilter() wiring -------------------------------------------------

    @Test
    void wiresAllowedMethods() {
        assertThat(apiCorsConfig(new CorsConfig()).getAllowedMethods())
                .containsExactly("GET", "POST", "OPTIONS");
    }

    @Test
    void allowsCredentials() {
        assertThat(apiCorsConfig(new CorsConfig()).getAllowCredentials()).isTrue();
    }

    @Test
    void allowsAllHeaders() {
        assertThat(apiCorsConfig(new CorsConfig()).getAllowedHeaders()).containsExactly("*");
    }

    @Test
    void registersConfigurationForApiPathOnly() {
        CorsFilter filter = new CorsConfig().corsFilter();
        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) ReflectionTestUtils.getField(filter, "configSource");
        assertThat(source.getCorsConfigurations()).containsOnlyKeys("/api/**");
    }

    // --- helpers -------------------------------------------------------------

    private static CorsConfig configWith(String csv) {
        CorsConfig config = new CorsConfig();
        ReflectionTestUtils.setField(config, "allowedOriginsCsv", csv);
        return config;
    }

    private static CorsConfiguration apiCorsConfig(CorsConfig config) {
        CorsFilter filter = config.corsFilter();
        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) ReflectionTestUtils.getField(filter, "configSource");
        return source.getCorsConfigurations().get("/api/**");
    }
}
