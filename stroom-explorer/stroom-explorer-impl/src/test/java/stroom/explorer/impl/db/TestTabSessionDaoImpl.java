/*
 * Copyright 2016-2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.explorer.impl.db;

import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.explorer.shared.TabSession;

import com.google.inject.Guice;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.explorer.impl.db.jooq.tables.TabSession.TAB_SESSION;
import static stroom.explorer.impl.db.jooq.tables.TabSessionDocRef.TAB_SESSION_DOC_REF;

@ExtendWith(MockitoExtension.class)
class TestTabSessionDaoImpl {

    @Inject
    private TabSessionDaoImpl dao;
    @Inject
    ExplorerDbConnProvider explorerDbConnProvider;

    @BeforeEach
    void setup() {
        Guice.createInjector(new TestModule()).injectMembers(this);
        JooqUtil.context(explorerDbConnProvider, context -> {
            context.deleteFrom(TAB_SESSION_DOC_REF).execute();
            context.deleteFrom(TAB_SESSION).execute();
        });
    }

    private int insertTabSession(final String name, final String userUuid) {
        return JooqUtil.contextResult(explorerDbConnProvider, context ->
                context.insertInto(TAB_SESSION, TAB_SESSION.NAME, TAB_SESSION.USER_UUID)
                .values(name, userUuid)
                .returning(TAB_SESSION.ID)
                .fetchOne()
                .get(TAB_SESSION.ID));
    }

    private void insertDocRef(final int sessionId, final int tabIndex, final String type, final String uuid) {
        JooqUtil.context(explorerDbConnProvider, context ->
                context.insertInto(TAB_SESSION_DOC_REF,
                        TAB_SESSION_DOC_REF.TAB_SESSION_ID,
                        TAB_SESSION_DOC_REF.TAB_INDEX,
                        TAB_SESSION_DOC_REF.DOC_REF_TYPE,
                        TAB_SESSION_DOC_REF.DOC_REF_ID)
                .values(sessionId, tabIndex, type, uuid)
                .execute());
    }

    @Test
    void testGetTabSessionForUser_noSessions() {
        final List<TabSession> result = dao.getTabSessionForUser("user-1");
        assertThat(result).isEmpty();
    }

    @Test
    void testGetTabSessionForUser_sessionWithDocRefs() {
        final int sessionId = insertTabSession("My Session", "user-1");
        insertDocRef(sessionId, 0, "Pipeline", "uuid-1");
        insertDocRef(sessionId, 1, "Dictionary", "uuid-2");

        final List<TabSession> result = dao.getTabSessionForUser("user-1");

        assertThat(result).hasSize(1);
        final TabSession session = result.get(0);
        assertThat(session.getUserId()).isEqualTo("user-1");
        assertThat(session.getName()).isEqualTo("My Session");
        assertThat(session.getDocRefs()).hasSize(2);
        assertThat(session.getDocRefs()).extracting(DocRef::getType)
                .containsExactly("Pipeline", "Dictionary");
    }

    @Test
    void testGetTabSessionForUser_sessionWithNoDocRefs() {
        insertTabSession("Empty Session", "user-1");

        final List<TabSession> result = dao.getTabSessionForUser("user-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDocRefs()).isEmpty();
    }

    @Test
    void testGetTabSessionForUser_onlyReturnsSessionsForUser() {
        insertTabSession("Session A", "user-1");
        insertTabSession("Session B", "user-2");

        final List<TabSession> result = dao.getTabSessionForUser("user-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Session A");
    }

    @Test
    void testGetTabSessionForUser_multipleSessionsForUser() {
        insertTabSession("Session A", "user-1");
        insertTabSession("Session B", "user-1");

        final List<TabSession> result = dao.getTabSessionForUser("user-1");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(TabSession::getName)
                .containsExactlyInAnyOrder("Session A", "Session B");
    }

    @Test
    void testCreateTabSession_noDocRefs() {
        final TabSession result = dao.createTabSession("user-1", "My Session", List.of());

        assertThat(result.getUserId()).isEqualTo("user-1");
        assertThat(result.getName()).isEqualTo("My Session");
        assertThat(result.getDocRefs()).isEmpty();

        // Verify it was persisted
        final List<TabSession> sessions = dao.getTabSessionForUser("user-1");
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).getName()).isEqualTo("My Session");
    }

    @Test
    void testCreateTabSession_withDocRefs() {
        final List<DocRef> docRefs = List.of(
                new DocRef("Pipeline", "uuid-1"),
                new DocRef("Dictionary", "uuid-2")
        );

        final TabSession result = dao.createTabSession("user-1", "My Session", docRefs);

        assertThat(result.getUserId()).isEqualTo("user-1");
        assertThat(result.getName()).isEqualTo("My Session");
        assertThat(result.getDocRefs()).hasSize(2);
        assertThat(result.getDocRefs()).extracting(DocRef::getType)
                .containsExactlyInAnyOrder("Pipeline", "Dictionary");

        // Verify it was persisted
        final List<TabSession> sessions = dao.getTabSessionForUser("user-1");
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).getDocRefs()).hasSize(2);
    }

    @Test
    void testCreateTabSession_sessionIdIsReturned() {
        final TabSession result = dao.createTabSession("user-1", "My Session", List.of());
        assertThat(result.getSessionId()).isNotNull();
        assertThat(result.getSessionId()).isNotBlank();
    }

    @Test
    void testCreateTabSession_multipleSessionsForUser() {
        dao.createTabSession("user-1", "Session A", List.of());
        dao.createTabSession("user-1", "Session B", List.of());

        final List<TabSession> sessions = dao.getTabSessionForUser("user-1");
        assertThat(sessions).hasSize(2);
        assertThat(sessions).extracting(TabSession::getName)
                .containsExactlyInAnyOrder("Session A", "Session B");
    }

    @Test
    void testCreateTabSession_uniqueSessionIds() {
        final TabSession session1 = dao.createTabSession("user-1", "Session A", List.of());
        final TabSession session2 = dao.createTabSession("user-1", "Session B", List.of());

        assertThat(session1.getSessionId()).isNotEqualTo(session2.getSessionId());
    }
}
