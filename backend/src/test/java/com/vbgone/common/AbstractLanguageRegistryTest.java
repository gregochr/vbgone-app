package com.vbgone.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Direct coverage of the shared resolution behaviour the per-domain registries inherit. */
class AbstractLanguageRegistryTest {

    private record Widget(String id) implements Keyed {}

    /** A concrete registry over a fake Keyed type, exercising the base directly. */
    private static final class WidgetRegistry extends AbstractLanguageRegistry<Widget> {
        WidgetRegistry(List<Widget> items) {
            super(items, "widget");
        }
    }

    private final Widget csharp = new Widget("csharp");
    private final Widget java = new Widget("java");
    private final WidgetRegistry registry = new WidgetRegistry(List.of(csharp, java));

    @Test
    void resolvesById() {
        assertThat(registry.forLanguage("csharp")).isSameAs(csharp);
        assertThat(registry.forLanguage("java")).isSameAs(java);
    }

    @Test
    void isCaseInsensitive() {
        assertThat(registry.forLanguage("JAVA")).isSameAs(java);
    }

    @Test
    void nullOrBlankFallsBackToCsharp() {
        assertThat(registry.forLanguage(null)).isSameAs(csharp);
        assertThat(registry.forLanguage("   ")).isSameAs(csharp);
    }

    @Test
    void unknownThrowsWithTheOffendingInputAndTheElementNoun() {
        assertThatThrownBy(() -> registry.forLanguage("rust"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rust")     // the offending input
                .hasMessageContaining("widget");  // the constructor-supplied noun (previously untested)
    }
}
