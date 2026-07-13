package com.vbgone.build;

import com.vbgone.model.BuildResult;
import com.vbgone.model.MigrationSession;
import com.vbgone.model.TestsResult;

/**
 * Runs a C# MSTest characterisation {@code suite} against the ORIGINAL VB.NET for {@code className}
 * and reports GREEN / RED / ERROR. Every implementation produces the outcome from an MSTest
 * {@code .trx} via {@link TrxParser}, so the whole downstream (parsing, coverage, readiness, UI) is
 * shared regardless of where the compile-and-run happened.
 *
 * <p>Two implementations, selected per class by its {@link com.vbgone.model.Bucket}:
 * <ul>
 *   <li>{@link VbCharacterisationRunner} — the Linux/net8.0 .NET SDK sidecar. Fast and local, but can
 *       only build the headless, cross-platform subset ({@link com.vbgone.model.Bucket#NET_READY}).</li>
 *   <li>{@code WindowsCharacterisationRunner} — dispatches to a GitHub-hosted {@code windows-latest}
 *       runner that builds on net48, for the {@link com.vbgone.model.Bucket#WINDOWS_GATED} classes that
 *       reference .NET Framework-only APIs and therefore cannot compile on the Linux sidecar.</li>
 * </ul>
 */
public interface CharacterisationRunner {

    /**
     * Compiles the original VB for {@code className} and runs the MSTest characterisation {@code suite}
     * against it. GREEN when every assertion holds, RED when assertions fail, ERROR when the VB or the
     * suite fails to compile.
     */
    BuildResult run(MigrationSession session, String className, TestsResult suite);
}
