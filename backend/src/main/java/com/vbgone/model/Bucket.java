package com.vbgone.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Protect readiness bucket for a method (or a class, rolled up worst-case).
 *
 * <p>The JSON/wire values are the stable identifiers ({@code net-ready} etc.); the
 * user-facing labels ("Ready to protect", "Needs Windows runner", "Tangled in the UI")
 * live in the frontend display layer only.
 */
public enum Bucket {
    /** Public, UI-free — compiles & runs headless on the CLR today. */
    NET_READY("net-ready"),
    /** Pure logic trapped in a WinForms-referencing class — needs a Windows runner. */
    WINDOWS_GATED("windows-gated"),
    /** Reads/writes controls or pops dialogs — entangled with the UI, must be separated first. */
    REFACTOR_FIRST("refactor-first");

    private final String wire;

    Bucket(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    /** Severity for class rollup: the worst (most-blocked) method bucket wins. */
    public int severity() {
        return switch (this) {
            case NET_READY -> 0;
            case WINDOWS_GATED -> 1;
            case REFACTOR_FIRST -> 2;
        };
    }
}
