package com.vbgone.build;

import com.vbgone.model.BuildResult;
import com.vbgone.model.InterfaceResult;
import com.vbgone.model.MigrationSession;
import com.vbgone.model.TestsResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuildRuntimeRegistryTest {

    private static BuildRuntime runtime(String id) {
        return new BuildRuntime() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public BuildResult build(MigrationSession session, InterfaceResult iface, TestsResult tests,
                                     String implementationCode, List<String> dependencies) {
                return null;
            }
        };
    }

    private BuildRuntime csharp;
    private BuildRuntime java;
    private BuildRuntimeRegistry registry;

    @BeforeEach
    void setUp() {
        csharp = runtime("csharp");
        java = runtime("java");
        registry = new BuildRuntimeRegistry(List.of(csharp, java));
    }

    @Test
    void forLanguage_resolvesCsharp() {
        assertThat(registry.forLanguage("csharp")).isSameAs(csharp);
    }

    @Test
    void forLanguage_resolvesJava() {
        assertThat(registry.forLanguage("java")).isSameAs(java);
    }

    @Test
    void forLanguage_isCaseInsensitive() {
        assertThat(registry.forLanguage("JAVA")).isSameAs(java);
    }

    @Test
    void forLanguage_defaultsToCsharpForNull() {
        assertThat(registry.forLanguage(null)).isSameAs(csharp);
    }

    @Test
    void forLanguage_defaultsToCsharpForBlank() {
        assertThat(registry.forLanguage("  ")).isSameAs(csharp);
    }

    @Test
    void forLanguage_throwsForUnknown() {
        assertThatThrownBy(() -> registry.forLanguage("rust"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rust");
    }
}
