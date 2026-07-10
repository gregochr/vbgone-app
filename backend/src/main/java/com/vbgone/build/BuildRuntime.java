package com.vbgone.build;

import com.vbgone.common.Keyed;
import com.vbgone.model.BuildResult;
import com.vbgone.model.InterfaceResult;
import com.vbgone.model.MigrationSession;
import com.vbgone.model.TestsResult;

import java.util.List;

/**
 * A target-language build runtime: scaffolds a project for the generated code,
 * compiles and runs the tests, and returns the parsed {@link BuildResult}.
 *
 * <p>Implementations are stateless dispatch targets resolved by
 * {@link BuildRuntimeRegistry} from the session's {@code targetLanguage}. They do
 * NOT mutate the session's green/red build state — that stays in
 * {@code BuildService} so the dispatch layer owns session transitions.
 */
public interface BuildRuntime extends Keyed {

    BuildResult build(MigrationSession session,
                      InterfaceResult iface,
                      TestsResult tests,
                      String implementationCode,
                      List<String> dependencies);
}
