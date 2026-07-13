package com.vbgone.build;

import com.vbgone.model.Bucket;
import com.vbgone.model.BuildResult;
import com.vbgone.model.MigrationSession;
import com.vbgone.model.TestsResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The {@link CharacterisationRunner} that {@code AssureService} depends on. Routes each class to the
 * runner its readiness {@link Bucket} implies:
 * <ul>
 *   <li>{@link Bucket#WINDOWS_GATED} → the {@link WindowsCharacterisationRunner} (when enabled), which
 *       builds on net48 on a GitHub-hosted Windows runner;</li>
 *   <li>everything else → the {@link VbCharacterisationRunner} Linux sidecar.</li>
 * </ul>
 *
 * <p>Gated by {@code vbgone.assure.windows-runner.enabled} (default {@code false}). With it off,
 * every class routes to Linux — so a WINDOWS_GATED class surfaces as ERROR on compile, exactly as it
 * does today. This is what makes the whole feature safe to merge before the runner is provisioned.
 *
 * <p><b>Note:</b> a Windows run takes minutes; before enabling in production the Assure baseline-tests
 * call must be made asynchronous (the Phase 3 job wrapper, modelled on the mutation-testing job) so
 * the HTTP request doesn't block.
 */
@Component
@Primary
public class CharacterisationRouter implements CharacterisationRunner {

    private final VbCharacterisationRunner linux;
    private final WindowsCharacterisationRunner windows;
    private final boolean windowsEnabled;

    public CharacterisationRouter(
            VbCharacterisationRunner linux,
            WindowsCharacterisationRunner windows,
            @Value("${vbgone.assure.windows-runner.enabled:false}") boolean windowsEnabled) {
        this.linux = linux;
        this.windows = windows;
        this.windowsEnabled = windowsEnabled;
    }

    @Override
    public BuildResult run(MigrationSession session, String className, TestsResult suite) {
        if (windowsEnabled && session.getBucketForClass(className) == Bucket.WINDOWS_GATED) {
            return windows.run(session, className, suite);
        }
        return linux.run(session, className, suite);
    }
}
