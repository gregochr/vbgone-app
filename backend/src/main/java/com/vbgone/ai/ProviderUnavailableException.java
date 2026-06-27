package com.vbgone.ai;

/**
 * Thrown when an AI provider cannot service a request — e.g. it is not
 * configured (missing token), an unknown provider was requested, or the
 * upstream call failed. Surfaced to the client as HTTP 422 with a clear,
 * non-fatal message; never crashes app startup.
 */
public class ProviderUnavailableException extends RuntimeException {

    public ProviderUnavailableException(String message) {
        super(message);
    }

    public ProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
