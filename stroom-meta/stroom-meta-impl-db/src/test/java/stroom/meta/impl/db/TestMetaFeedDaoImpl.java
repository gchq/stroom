/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.meta.impl.db;

import stroom.cache.impl.CacheModule;
import stroom.cluster.lock.mock.MockClusterLockModule;
import stroom.collection.mock.MockCollectionModule;
import stroom.dictionary.mock.MockWordListProviderModule;
import stroom.docrefinfo.mock.MockDocRefInfoModule;
import stroom.security.mock.MockSecurityContextModule;
import stroom.task.mock.MockTaskModule;
import stroom.test.common.MockMetricsModule;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.Guice;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestMetaFeedDaoImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestMetaFeedDaoImpl.class);

    private static final String BANANA = "BANANA";
    private static final String MANGO = "MANGO";
    private static final String CUSTARD_APPLE = "CUSTARD_APPLE";
    private static final String KUMQUAT = "KUMQUAT";

    private Map<String, Integer> testFeedToIdMap = new HashMap<>();

    @Inject
    private Cleanup cleanup;
    @Inject
    private MetaFeedDaoImpl feedDao;

    @BeforeEach
    void setup() {
        LOGGER.logDurationIfDebugEnabled(() -> {
            Guice.createInjector(
                            new MetaTestModule(),
                            new MetaDbModule(),
                            new MetaDaoModule(),
                            new MockClusterLockModule(),
                            new MockSecurityContextModule(),
                            new MockTaskModule(),
                            new MockCollectionModule(),
                            new MockDocRefInfoModule(),
                            new MockWordListProviderModule(),
                            new MockMetricsModule(),
                            new CacheModule(),
                            new DbTestModule())
                    .injectMembers(this);
        }, "createInjector");
        // Delete everything
        LOGGER.logDurationIfDebugEnabled(() -> {
            cleanup.cleanup();
        }, "cleanup");
    }

    @Test
    void test() {
        String feedName = "TEST";
        Integer id1 = feedDao.getOrCreate(feedName);
        Integer id2 = feedDao.getOrCreate(feedName);

        assertThat(id1).isEqualTo(id2);

        feedName = "TEST2";
        id1 = feedDao.getOrCreate(feedName);
        id2 = feedDao.getOrCreate(feedName);

        assertThat(id1).isEqualTo(id2);

        assertThat(feedDao.list().size()).isEqualTo(2);
    }

    @Test
    void testDuplicateCreate() {
        final String feedName = "TEST";
        final Optional<Integer> id1 = feedDao.get(feedName);
        final Optional<Integer> id2 = feedDao.tryCreate(feedName);
        final Optional<Integer> id3 = feedDao.tryCreate(feedName);

        assertThat(id1).isEmpty();
        assertThat(id2).isNotEmpty();
        assertThat(id3).isNotEmpty();

        assertThat(id3).isEqualTo(id2);

        final Optional<Integer> id4 = Optional.ofNullable(feedDao.getOrCreate(feedName));
        final Optional<Integer> id5 = Optional.ofNullable(feedDao.getOrCreate(feedName));
        assertThat(id4).isEqualTo(id3);
        assertThat(id5).isEqualTo(id4);
    }

    @Test
    void testGet() {
        final String feedName = "FOO";
        Optional<Integer> optId = feedDao.get(feedName);

        assertThat(optId)
                .isEmpty();

        final Integer id = feedDao.getOrCreate(feedName);

        assertThat(id)
                .isNotNull();

        optId = feedDao.get(feedName);

        assertThat(optId)
                .hasValue(id);
    }

    @Test
    void testFind_empty() {

        setupFruitFeeds();

        // No conditions so returns everything
        final Map<String, Integer> feedToIdsMap = feedDao.find(Collections.emptyList());

        assertThat(feedToIdsMap)
                .hasSize(0);
    }

    @Test
    void testFind_single() {
        setupFruitFeeds();

        final Map<String, Integer> feedToIdsMap = feedDao.find(List.of(BANANA));
        assertThat(feedToIdsMap)
                .hasSize(1)
                .containsEntry(BANANA, testFeedToIdMap.get(BANANA));
    }

    @Test
    void testFind_single_wildCarded() {
        setupFruitFeeds();

        final Map<String, Integer> feedToIdsMap = feedDao.find(List.of("BANA*"));
        assertThat(feedToIdsMap)
                .hasSize(1)
                .containsEntry(BANANA, testFeedToIdMap.get(BANANA));
    }

    @Test
    void testFind_multiple() {
        setupFruitFeeds();

        final Map<String, Integer> feedToIdsMap = feedDao.find(List.of(
                BANANA,
                MANGO,
                CUSTARD_APPLE));

        assertThat(feedToIdsMap)
                .hasSize(3)
                .containsEntry(BANANA, testFeedToIdMap.get(BANANA))
                .containsEntry(MANGO, testFeedToIdMap.get(MANGO))
                .containsEntry(CUSTARD_APPLE, testFeedToIdMap.get(CUSTARD_APPLE));
    }

    @Test
    void testFind_multiple_wildCarded() {
        setupFruitFeeds();

        final Map<String, Integer> feedToIdsMap = feedDao.find(List.of(
                "BANA*",
                "*GO",
                "*TARD_APP*"));

        assertThat(feedToIdsMap)
                .hasSize(3)
                .containsEntry(BANANA, testFeedToIdMap.get(BANANA))
                .containsEntry(MANGO, testFeedToIdMap.get(MANGO))
                .containsEntry(CUSTARD_APPLE, testFeedToIdMap.get(CUSTARD_APPLE));
    }

    private void setupFruitFeeds() {
        testFeedToIdMap.clear();
        List.of(
                        BANANA,
                        CUSTARD_APPLE,
                        KUMQUAT,
                        MANGO)
                .forEach(feed -> {
                    feedDao.getOrCreate(feed);
                    final Optional<Integer> optId = feedDao.get(feed);
                    assertThat(optId)
                            .isPresent();
                    testFeedToIdMap.put(feed, optId.orElseThrow());
                });
    }
}
