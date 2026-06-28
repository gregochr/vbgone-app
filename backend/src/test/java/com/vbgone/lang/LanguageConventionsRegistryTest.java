package com.vbgone.lang;

import com.vbgone.model.InterfaceResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LanguageConventionsRegistryTest {

    private final LanguageConventionsRegistry registry =
            new LanguageConventionsRegistry(List.of(new CSharpConventions(), new JavaConventions()));

    @Test
    void resolvesCsharp() {
        LanguageConventions c = registry.forLanguage("csharp");
        assertEquals("csharp", c.id());
        assertEquals("IOrderProcessor", c.interfaceName("OrderProcessor"));
        assertEquals("OrderProcessor", c.implName("OrderProcessor"));
        assertEquals("OrderProcessorTests", c.testClassName("OrderProcessor"));
        assertEquals(".cs", c.sourceExtension());
        assertEquals("NotImplementedException", c.notImplExceptionType());
    }

    @Test
    void resolvesJava() {
        LanguageConventions j = registry.forLanguage("java");
        assertEquals("java", j.id());
        assertEquals("OrderProcessor", j.interfaceName("OrderProcessor"));
        assertEquals("OrderProcessorImpl", j.implName("OrderProcessor"));
        assertEquals("OrderProcessorTest", j.testClassName("OrderProcessor"));
        assertEquals(".java", j.sourceExtension());
        assertEquals("UnsupportedOperationException", j.notImplExceptionType());
    }

    @Test
    void nullOrBlankDefaultsToCsharp() {
        assertEquals("csharp", registry.forLanguage(null).id());
        assertEquals("csharp", registry.forLanguage("").id());
        assertEquals("csharp", registry.forLanguage("  ").id());
    }

    @Test
    void caseInsensitive() {
        assertEquals("java", registry.forLanguage("JAVA").id());
    }

    @Test
    void unknownLanguageThrows() {
        assertThrows(IllegalArgumentException.class, () -> registry.forLanguage("rust"));
    }

    @Test
    void interfaceResultBackCompatConstructorDefaultsImplNameToClassName() {
        InterfaceResult r = new InterfaceResult("s1", "OrderProcessor", "IOrderProcessor", "code");
        assertEquals("OrderProcessor", r.implName());
    }
}
