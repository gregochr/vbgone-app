package com.vbgone.prompt;

import com.vbgone.common.AbstractLanguageRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

/** Resolves a {@link PromptLanguage} by target-language id. Spring injects all implementations. */
@Component
public class PromptLanguageRegistry extends AbstractLanguageRegistry<PromptLanguage> {

    public PromptLanguageRegistry(List<PromptLanguage> prompts) {
        super(prompts, "prompt language");
    }
}
