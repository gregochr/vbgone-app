package com.vbgone.build;

import com.vbgone.common.AbstractLanguageRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

/** Resolves a {@link BuildRuntime} by target-language id. Spring injects all implementations. */
@Component
public class BuildRuntimeRegistry extends AbstractLanguageRegistry<BuildRuntime> {

    public BuildRuntimeRegistry(List<BuildRuntime> runtimes) {
        super(runtimes, "build runtime");
    }
}
