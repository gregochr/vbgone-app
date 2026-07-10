package com.vbgone.service;

import com.vbgone.build.BuildRuntime;
import com.vbgone.build.BuildRuntimeRegistry;
import com.vbgone.model.*;
import com.vbgone.session.SessionStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Dispatches a build to the {@link BuildRuntime} for the session's target
 * language. Owns the language-agnostic preconditions (interface/tests present,
 * implementation code, dependency resolution) and the session green/red
 * transition; the per-language scaffold-compile-parse logic lives in the runtime.
 */
@Service
public class BuildService {

    private final SessionStore sessionStore;
    private final BuildRuntimeRegistry runtimeRegistry;

    public BuildService(SessionStore sessionStore, BuildRuntimeRegistry runtimeRegistry) {
        this.sessionStore = sessionStore;
        this.runtimeRegistry = runtimeRegistry;
    }

    public BuildResult build(String sessionId) {
        MigrationSession session = sessionStore.getOrThrow(sessionId);

        InterfaceResult iface = session.getInterfaceResult();
        if (iface == null) {
            throw new IllegalStateException("Interface must be generated before building");
        }
        TestsResult tests = session.getTestsResult();
        if (tests == null) {
            throw new IllegalStateException("Tests must be generated before building");
        }

        String className = iface.className();
        String implementationCode = getImplementationCode(session);
        List<String> dependencies = getDependencies(session, className);

        BuildRuntime runtime = runtimeRegistry.forLanguage(session.getTargetLanguage());
        BuildResult result = runtime.build(session, iface, tests, implementationCode, dependencies);

        if (result.buildStatus() == BuildStatus.GREEN) {
            session.setGreenBuild(result);
        } else {
            session.setRedBuild(result);
        }

        return result;
    }

    private String getImplementationCode(MigrationSession session) {
        if (session.getImplementResult() != null) {
            return session.getImplementResult().code();
        }
        if (session.getStubResult() != null) {
            return session.getStubResult().code();
        }
        throw new IllegalStateException("Stub or implementation must exist before building");
    }

    List<String> getDependencies(MigrationSession session, String className) {
        AnalysisResult analysis = session.getAnalysisResult();
        if (analysis == null) return List.of();
        return analysis.classes().stream()
                .filter(c -> c.name().equals(className))
                .findFirst()
                .map(ClassInfo::dependencies)
                .orElse(List.of())
                .stream()
                .filter(dep -> !dep.contains("."))
                .toList();
    }
}
