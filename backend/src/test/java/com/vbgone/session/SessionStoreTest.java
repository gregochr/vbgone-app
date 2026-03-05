package com.vbgone.session;

import com.vbgone.model.MigrationSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SessionStoreTest {

    private SessionStore store;

    @BeforeEach
    void setUp() {
        store = new SessionStore();
    }

    @Test
    void create_returnsSessionWithValidUUID() {
        MigrationSession session = store.create();

        assertThat(session).isNotNull();
        assertThat(session.getSessionId()).isNotBlank();
        // Should be parseable as a UUID
        UUID.fromString(session.getSessionId());
    }

    @Test
    void create_returnsDifferentIdsEachTime() {
        MigrationSession s1 = store.create();
        MigrationSession s2 = store.create();

        assertThat(s1.getSessionId()).isNotEqualTo(s2.getSessionId());
    }

    @Test
    void get_returnsCorrectSessionById() {
        MigrationSession created = store.create();
        created.setFilename("Test.vb");

        Optional<MigrationSession> retrieved = store.get(created.getSessionId());

        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getSessionId()).isEqualTo(created.getSessionId());
        assertThat(retrieved.get().getFilename()).isEqualTo("Test.vb");
    }

    @Test
    void get_returnsEmptyForUnknownId() {
        Optional<MigrationSession> result = store.get("nonexistent-id");

        assertThat(result).isEmpty();
    }

    @Test
    void multipleSessionsDoNotInterfere() {
        MigrationSession s1 = store.create();
        MigrationSession s2 = store.create();
        s1.setFilename("A.vb");
        s2.setFilename("B.vb");

        assertThat(store.get(s1.getSessionId()).get().getFilename()).isEqualTo("A.vb");
        assertThat(store.get(s2.getSessionId()).get().getFilename()).isEqualTo("B.vb");
    }
}
