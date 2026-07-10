package com.vbgone.lang;

import com.vbgone.common.AbstractLanguageRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

/** Resolves {@link LanguageConventions} by target-language id. Spring injects all implementations. */
@Component
public class LanguageConventionsRegistry extends AbstractLanguageRegistry<LanguageConventions> {

    public LanguageConventionsRegistry(List<LanguageConventions> conventions) {
        super(conventions, "language conventions");
    }
}
