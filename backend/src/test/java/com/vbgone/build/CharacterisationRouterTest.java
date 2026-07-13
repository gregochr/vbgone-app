package com.vbgone.build;

import com.vbgone.model.Bucket;
import com.vbgone.model.BuildResult;
import com.vbgone.model.BuildStatus;
import com.vbgone.model.MigrationSession;
import com.vbgone.model.TestsResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CharacterisationRouterTest {

    @Mock
    private VbCharacterisationRunner linux;
    @Mock
    private WindowsCharacterisationRunner windows;

    private final TestsResult suite =
            new TestsResult("s", "Foo", "FooBaselineTests", "code", 1);
    private final BuildResult sentinel =
            new BuildResult("s", BuildStatus.GREEN, 1, 1, 0, List.of(), List.of());

    private MigrationSession sessionWith(Bucket bucket, String runnerMode) {
        MigrationSession s = new MigrationSession("s");
        s.setClassBuckets(Map.of("Foo", bucket));
        s.setRunnerMode(runnerMode);
        return s;
    }

    @Test
    void windowsGatedRoutesToWindows_whenWindowsChosenAndEnabled() {
        CharacterisationRouter router = new CharacterisationRouter(linux, windows, true);
        MigrationSession session = sessionWith(Bucket.WINDOWS_GATED, "windows");
        when(windows.run(session, "Foo", suite)).thenReturn(sentinel);

        assertThat(router.run(session, "Foo", suite)).isSameAs(sentinel);
        verify(windows).run(session, "Foo", suite);
        verifyNoInteractions(linux);
    }

    @Test
    void netReadyStaysOnLinux_evenInWindowsMode() {
        CharacterisationRouter router = new CharacterisationRouter(linux, windows, true);
        MigrationSession session = sessionWith(Bucket.NET_READY, "windows");
        when(linux.run(session, "Foo", suite)).thenReturn(sentinel);

        assertThat(router.run(session, "Foo", suite)).isSameAs(sentinel);
        verify(linux).run(session, "Foo", suite);
        verifyNoInteractions(windows);
    }

    @Test
    void windowsGatedStaysOnLinux_whenLinuxChosen() {
        CharacterisationRouter router = new CharacterisationRouter(linux, windows, true);
        MigrationSession session = sessionWith(Bucket.WINDOWS_GATED, "linux");
        when(linux.run(session, "Foo", suite)).thenReturn(sentinel);

        assertThat(router.run(session, "Foo", suite)).isSameAs(sentinel);
        verify(linux).run(session, "Foo", suite);
        verifyNoInteractions(windows);
    }

    @Test
    void killSwitchForcesLinux_evenWhenWindowsChosen() {
        CharacterisationRouter router = new CharacterisationRouter(linux, windows, false);
        MigrationSession session = sessionWith(Bucket.WINDOWS_GATED, "windows");
        when(linux.run(session, "Foo", suite)).thenReturn(sentinel);

        assertThat(router.run(session, "Foo", suite)).isSameAs(sentinel);
        verify(linux).run(session, "Foo", suite);
        verifyNoInteractions(windows);
    }
}
