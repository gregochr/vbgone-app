package com.vbgone.session;

import com.vbgone.model.MigrationSession;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class SessionStore {

    private final ConcurrentMap<String, MigrationSession> sessions = new ConcurrentHashMap<>();

    public MigrationSession create() {
        String sessionId = UUID.randomUUID().toString();
        MigrationSession session = new MigrationSession(sessionId);
        sessions.put(sessionId, session);
        return session;
    }

    public Optional<MigrationSession> get(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    /**
     * Returns the session for the given id, or throws {@link IllegalArgumentException}
     * (surfaced as HTTP 400) when no such session exists. Centralises the lookup + error
     * message that every service needs.
     */
    public MigrationSession getOrThrow(String sessionId) {
        return get(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
    }
}
