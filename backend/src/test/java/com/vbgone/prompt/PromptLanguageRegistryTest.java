package com.vbgone.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptLanguageRegistryTest {

    private final PromptLanguageRegistry registry =
            new PromptLanguageRegistry(List.of(new CSharpPrompts(), new JavaPrompts()));

    @Test
    void forLanguage_csharp() {
        assertThat(registry.forLanguage("csharp")).isInstanceOf(CSharpPrompts.class);
    }

    @Test
    void forLanguage_java() {
        assertThat(registry.forLanguage("java")).isInstanceOf(JavaPrompts.class);
    }

    @Test
    void forLanguage_isCaseInsensitive() {
        assertThat(registry.forLanguage("JAVA")).isInstanceOf(JavaPrompts.class);
    }

    @Test
    void forLanguage_nullDefaultsToCsharp() {
        assertThat(registry.forLanguage(null)).isInstanceOf(CSharpPrompts.class);
    }

    @Test
    void forLanguage_blankDefaultsToCsharp() {
        assertThat(registry.forLanguage("  ")).isInstanceOf(CSharpPrompts.class);
    }

    @Test
    void forLanguage_unknownThrows() {
        assertThatThrownBy(() -> registry.forLanguage("rust"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rust");
    }
}
