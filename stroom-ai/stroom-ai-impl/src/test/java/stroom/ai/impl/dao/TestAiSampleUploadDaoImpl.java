/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl.dao;

import stroom.ai.impl.AiSampleUploadDao;
import stroom.ai.impl.AiSampleUploadDao.UploadSet;
import stroom.ai.impl.db.AiDbConnProvider;
import stroom.ai.impl.db.AiDbModule;
import stroom.collection.api.CollectionService;
import stroom.db.util.JooqUtil;
import stroom.dictionary.api.WordListProvider;
import stroom.docstore.api.DocFinder;
import stroom.test.common.util.db.DbTestUtil;
import stroom.test.common.util.db.UniqueDbTestModule;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.ai.impl.db.jooq.tables.AiSampleUploadFile.AI_SAMPLE_UPLOAD_FILE;

/// Runs against a database of its own, with every migration run, as an upgrade would.
class TestAiSampleUploadDaoImpl {

    private static final long NOW = 1_000_000L;
    private static final long LATER = NOW + 15 * 60_000L;
    // Claims made before this are stale.
    private static final long STALE_BEFORE = NOW - 10 * 60_000L;

    private AiSampleUploadDao dao;
    private AiDbConnProvider aiDbConnProvider;

    @BeforeEach
    void setUp() {
        final Injector injector = Guice.createInjector(
                new UniqueDbTestModule(),
                new AiDbModule(),
                new AiDaoModule(),
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(WordListProvider.class).toInstance(Mockito.mock(WordListProvider.class));
                        bind(CollectionService.class).toInstance(Mockito.mock(CollectionService.class));
                        bind(DocFinder.class).toInstance(Mockito.mock(DocFinder.class));
                    }
                });
        dao = injector.getInstance(AiSampleUploadDao.class);
        aiDbConnProvider = injector.getInstance(AiDbConnProvider.class);
    }

    @AfterEach
    void tearDown() {
        DbTestUtil.dropThreadTestDatabase(false);
    }

    @Test
    void testASetIsFoundByItsTokenUntilItRunsOut() {
        final Map<String, String> headers = new LinkedHashMap<>();
        headers.put("MyHost", "ws01");
        headers.put("Environment", "Test");
        dao.create(set("token-1", "alice", headers, "fortios", LATER), List.of("a.json"), NOW);

        final UploadSet found = dao.find("token-1", NOW).orElseThrow();

        assertThat(found.id()).isPositive();
        assertThat(found.token()).isEqualTo("token-1");
        assertThat(found.userUuid()).isEqualTo("alice");
        assertThat(found.feedName()).isEqualTo("FEED");
        assertThat(found.feedUuid()).isEqualTo("f1");
        assertThat(found.streamType()).isEqualTo("Raw Events");
        assertThat(found.build()).isEqualTo("fortios");
        assertThat(found.expiryTimeMs()).isEqualTo(LATER);
        // The receipt headers, in the order they were given.
        assertThat(found.headers()).containsExactly(Map.entry("MyHost", "ws01"), Map.entry("Environment", "Test"));
        assertThat(dao.find("token-2", NOW)).isEmpty();
        assertThat(dao.find("token-1", LATER)).isEmpty();
    }

    @Test
    void testASetWithNoBuildOrHeaders() {
        dao.create(set("token-1", "alice", Map.of(), null, LATER), List.of("a.json"), NOW);

        final UploadSet found = dao.find("token-1", NOW).orElseThrow();

        assertThat(found.build()).isNull();
        assertThat(found.headers()).isEmpty();
    }

    @Test
    void testAFileIsClaimedByOnePostAtATime() {
        final int setId = create("token-1", "alice", LATER, "a.json");

        assertThat(dao.claim(setId, "a.json", "node1", NOW, STALE_BEFORE)).isTrue();
        // Another post, on this node or another, while the first may still be storing it.
        assertThat(dao.claim(setId, "a.json", "node2", NOW + 1, STALE_BEFORE + 1)).isFalse();
        assertThat(dao.claim(setId, "a.json", "node1", NOW + 2, STALE_BEFORE + 2)).isFalse();
        // A file the set doesn't have.
        assertThat(dao.claim(setId, "b.json", "node1", NOW, STALE_BEFORE)).isFalse();
        assertThat(dao.claim(setId + 1, "a.json", "node1", NOW, STALE_BEFORE)).isFalse();
    }

    @Test
    void testAStoredFileIsNeverClaimedAgain() {
        final int setId = create("token-1", "alice", LATER, "a.json");
        dao.claim(setId, "a.json", "node1", NOW, STALE_BEFORE);

        assertThat(dao.markStored(setId, "a.json", "node1", NOW, 77L, NOW + 5)).isTrue();

        assertThat(dao.claim(setId, "a.json", "node2", NOW + 6, STALE_BEFORE + 6)).isFalse();
        // Not even once the claim that stored it is long past.
        assertThat(dao.claim(setId, "a.json", "node2", LATER, LATER)).isFalse();
        assertThat(dao.release(setId, "a.json", "node1", NOW)).isFalse();
    }

    @Test
    void testAReleasedFileCanBeClaimedAgain() {
        final int setId = create("token-1", "alice", LATER, "a.json");
        dao.claim(setId, "a.json", "node1", NOW, STALE_BEFORE);

        // Only by the claim that holds it.
        assertThat(dao.release(setId, "a.json", "node2", NOW)).isFalse();
        assertThat(dao.release(setId, "a.json", "node1", NOW + 1)).isFalse();
        assertThat(dao.release(setId, "a.json", "node1", NOW)).isTrue();

        assertThat(dao.claim(setId, "a.json", "node2", NOW + 2, STALE_BEFORE + 2)).isTrue();
    }

    @Test
    void testAStaleClaimIsTakenOver() {
        // The node that claimed it stopped part way through the post.
        final int setId = create("token-1", "alice", LATER, "a.json");
        dao.claim(setId, "a.json", "node1", NOW, STALE_BEFORE);

        assertThat(dao.claim(setId, "a.json", "node2", NOW + 600_000L, NOW)).isFalse();
        assertThat(dao.claim(setId, "a.json", "node2", NOW + 600_001L, NOW + 1)).isTrue();

        // The first claim no longer holds the file: it can neither store nor release it.
        assertThat(dao.markStored(setId, "a.json", "node1", NOW, 1L, NOW + 600_002L)).isFalse();
        assertThat(dao.release(setId, "a.json", "node1", NOW)).isFalse();
        assertThat(dao.markStored(setId, "a.json", "node2", NOW + 600_001L, 2L, NOW + 600_002L)).isTrue();
    }

    @Test
    void testOnlySetsWithFilesStillToPostAreCountedAsWaiting() {
        final int posted = create("posted", "alice", LATER, "a.json");
        final int sending = create("sending", "alice", LATER, "a.json");
        create("waiting", "alice", LATER, "a.json", "b.json");
        create("expired", "alice", NOW, "a.json");
        create("bobs", "bob", LATER, "a.json");
        dao.claim(posted, "a.json", "node1", NOW, STALE_BEFORE);
        dao.markStored(posted, "a.json", "node1", NOW, 1L, NOW);
        dao.claim(sending, "a.json", "node1", NOW, STALE_BEFORE);

        assertThat(dao.countWaiting("alice", NOW, STALE_BEFORE)).isEqualTo(1);
        // Once the claim of the file being sent is stale, it may be sent again, so its set waits again.
        assertThat(dao.countWaiting("alice", NOW + 1, NOW + 1)).isEqualTo(2);
        assertThat(dao.countWaiting("bob", NOW, STALE_BEFORE)).isEqualTo(1);
        assertThat(dao.countWaiting("carol", NOW, STALE_BEFORE)).isZero();
    }

    @Test
    void testSetsThatHaveRunOutAreDeletedWithTheirFiles() {
        create("expired", "alice", NOW, "a.json", "b.json");
        create("current", "alice", LATER, "c.json");

        assertThat(dao.deleteExpired(NOW)).isEqualTo(1);

        assertThat(dao.find("expired", 0L)).isEmpty();
        assertThat(dao.find("current", NOW)).isPresent();
        assertThat(JooqUtil.getTableCount(aiDbConnProvider, AI_SAMPLE_UPLOAD_FILE)).isEqualTo(1);
        assertThat(dao.deleteExpired(NOW)).isZero();
    }

    @Test
    void testAUsersSetsAreDeleted() {
        create("alice-1", "alice", LATER, "a.json");
        create("alice-2", "alice", LATER, "b.json");
        create("bob-1", "bob", LATER, "a.json");
        assertThat(dao.getUserUuids()).containsExactlyInAnyOrder("alice", "bob");

        assertThat(dao.deleteAllForUser("alice")).isEqualTo(2);

        assertThat(dao.getUserUuids()).containsExactly("bob");
        assertThat(JooqUtil.getTableCount(aiDbConnProvider, AI_SAMPLE_UPLOAD_FILE)).isEqualTo(1);
        assertThat(dao.deleteAllForUser("carol")).isZero();
    }

    private int create(final String token, final String user, final long expiryTimeMs, final String... files) {
        dao.create(set(token, user, Map.of(), null, expiryTimeMs), List.of(files), NOW);
        return dao.find(token, 0L).orElseThrow().id();
    }

    private static UploadSet set(final String token,
                                 final String user,
                                 final Map<String, String> headers,
                                 final String build,
                                 final long expiryTimeMs) {
        return new UploadSet(0, token, user, "FEED", "f1", "Raw Events", headers, build, expiryTimeMs);
    }
}
