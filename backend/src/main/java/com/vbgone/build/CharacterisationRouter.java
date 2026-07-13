package com.vbgone.build;

import com.vbgone.model.Bucket;
import com.vbgone.model.BuildResult;
import com.vbgone.model.MigrationSession;
import com.vbgone.model.TestsResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The {@link CharacterisationRunner} that {@code AssureService} depends on. A class routes to the
 * Windows runner only when all three hold:
 * <ul>
 *   <li>the user picked <b>Windows</b> in the header RUNNER toggle ({@code session.getRunnerMode()});</li>
 *   <li>the class is {@link Bucket#WINDOWS_GATED} (net-ready classes always run on the faster Linux
 *       sidecar, even in Windows mode);</li>
 *   <li>the global kill-switch {@code vbgone.assure.windows-runner.enabled} is on (default true).</li>
 * </ul>
 * Otherwise it routes to the {@link VbCharacterisationRunner} Linux sidecar. Since the runner mode
 * defaults to Linux, default behaviour is unchanged — only an explicit Windows choice dispatches.
 *
 * <p>The kill-switch lets an operator disable the Windows path estate-wide (e.g. a deployment with no
 * Windows runner provisioned) without touching the UI.
 *
 * <p><b>Note:</b> a Windows run takes minutes; before relying on it in production the Assure
 * baseline-tests call must be made asynchronous (the Phase 3 job wrapper, modelled on the
 * mutation-testing job) so the HTTP request doesn't block.
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
            @Value("${vbgone.assure.windows-runner.enabled:true}") boolean windowsEnabled) {
        this.linux = linux;
        this.windows = windows;
        this.windowsEnabled = windowsEnabled;
    }

    @Override
    public BuildResult run(MigrationSession session, String className, TestsResult suite) {
        boolean useWindows = windowsEnabled
                && "windows".equalsIgnoreCase(session.getRunnerMode())
                && session.getBucketForClass(className) == Bucket.WINDOWS_GATED;
        return useWindows
                ? windows.run(session, className, suite)
                : linux.run(session, className, suite);
    }
}
