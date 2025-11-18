/*
 * Copyright 2017 Crown Copyright
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

package stroom.statistics.impl.sql;


import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.statistics.impl.sql.entity.StatisticStoreCache;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.concurrent.AtomicSequence;
import stroom.util.concurrent.SimpleExecutor;
import stroom.util.time.StroomDuration;

import jakarta.inject.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestSQLStatisticEventStore extends StroomUnitTest {

    private final AtomicLong createCount = new AtomicLong();
    private final AtomicLong destroyCount = new AtomicLong();
    private final AtomicLong eventCount = new AtomicLong();
    private final AtomicSequence atomicSequence = new AtomicSequence(10);
    private SQLStatisticsConfig sqlStatisticsConfig = new SQLStatisticsConfig();
    private final SecurityContext securityContext = new MockSecurityContext();

    private final StatisticStoreCache mockStatisticsDataSourceCache = new StatisticStoreCache() {
        @Override
        public StatisticStoreDoc getStatisticsDataSource(final String statisticName) {
            return StatisticStoreDoc.builder()
                    .uuid(UUID.randomUUID().toString())
                    .name(statisticName)
                    .precision(1_000L)
                    .build();
        }

        @Override
        public StatisticStoreDoc getStatisticsDataSource(final DocRef docRef) {
            return null;
        }
    };

    @Mock
    private SQLStatisticCache mockSqlStatisticCache;
    @Captor
    private ArgumentCaptor<SQLStatisticAggregateMap> aggregateMapCaptor;

    @BeforeEach
    void beforeTest() {
        createCount.set(0);
        destroyCount.set(0);
        eventCount.set(0);
    }

    private StatisticEvent createEvent() {
        return createEvent(System.currentTimeMillis());

    }

    private StatisticEvent createEvent(final long timeMs) {
        eventCount.incrementAndGet();
        return StatisticEvent.createCount(timeMs, "NAME" + atomicSequence.next(), null, 1L);
    }

    @Test
    void test() throws InterruptedException {
        // Max Pool size of 5 with 10 items in the pool Add 1000 and we should
        // expect APROX the below
        final Provider<SQLStatisticsConfig> configProvider = () -> getSqlStatisticsConfig()
                .withInMemAggregatorPoolSize(5)
                .withInMemPooledAggregatorSizeThreshold(10)
                .withInMemPooledAggregatorAgeThreshold(StroomDuration.ofSeconds(10));

        final SQLStatisticEventStore store = new SQLStatisticEventStore(
                null,
                mockStatisticsDataSourceCache,
                null,
                configProvider,
                securityContext,
                new SimpleTaskContextFactory()) {
            @Override
            public SQLStatisticAggregateMap createAggregateMap() {
                createCount.incrementAndGet();
                return super.createAggregateMap();
            }

            @Override
            public void destroyAggregateMap(final SQLStatisticAggregateMap map) {
                destroyCount.incrementAndGet();
            }
        };

        final SimpleExecutor simpleExecutor = new SimpleExecutor(10);

        for (int i = 0; i < 1000; i++) {
            simpleExecutor.execute(() -> store.putEvent(createEvent()));
            store.putEvent(createEvent());
        }

        simpleExecutor.stop(false);

        assertThat(createCount.get() > 0).isTrue();
        assertThat(destroyCount.get() > 0).isTrue();

    }

    @Test
    void testIdle() throws InterruptedException {
        // Max Pool size of 5 with 10 items in the pool Add 1000 and we should
        // expect APROX the below
        final Provider<SQLStatisticsConfig> configProvider = () -> getSqlStatisticsConfig()
                .withInMemAggregatorPoolSize(10)
                .withInMemPooledAggregatorSizeThreshold(10)
                .withInMemPooledAggregatorAgeThreshold(StroomDuration.ofMillis(100));
        final SQLStatisticEventStore store = new SQLStatisticEventStore(
                null,
                mockStatisticsDataSourceCache,
                null,
                configProvider,
                securityContext,
                new SimpleTaskContextFactory()) {
            @Override
            public SQLStatisticAggregateMap createAggregateMap() {
                createCount.incrementAndGet();
                return super.createAggregateMap();
            }

            @Override
            public void destroyAggregateMap(final SQLStatisticAggregateMap map) {
                destroyCount.incrementAndGet();
            }
        };

        assertThat(store.getNumIdle()).isEqualTo(0);
        assertThat(store.getNumActive()).isEqualTo(0);

        store.putEvent(createEvent());

        assertThat(store.getNumIdle()).isEqualTo(1);
        assertThat(store.getNumActive()).isEqualTo(0);

        Thread.sleep(200);

        // store required manually evicting
        store.evict();

        assertThat(store.getNumIdle()).isEqualTo(0);
        assertThat(store.getNumActive()).isEqualTo(0);

    }

    public SQLStatisticsConfig getSqlStatisticsConfig() {
        return sqlStatisticsConfig;
    }

    @Test
    void testEmptyPropString() {
        final int eventCount = 100;
        final long firstEventTimeMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);

        sqlStatisticsConfig = sqlStatisticsConfig.withMaxProcessingAge(null);

        processEvents(eventCount, eventCount, firstEventTimeMs, TimeUnit.MINUTES.toMillis(1));

    }

    @Test
    void testAllEventsTooOld() {
        final int eventCount = 100;
        // ten day old events
        final long firstEventTimeMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10);

        // only process stuff younger than a day
        sqlStatisticsConfig = sqlStatisticsConfig.withMaxProcessingAge(StroomDuration.ofDays(1));

        // no events should be processed
        processEvents(eventCount, 0, firstEventTimeMs, TimeUnit.MINUTES.toMillis(1));

    }

    @Test
    void testSomeEventsTooOld() {
        final int eventCount = 100;
        // events starting at 100 days old with each a day newer
        // add 10secs to ensure give the test time to run, otherwise it only
        // does 74
        final long firstEventTimeMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(100)
                                      + TimeUnit.SECONDS.toMillis(10);

        // only process stuff younger than 25days
        sqlStatisticsConfig = sqlStatisticsConfig.withMaxProcessingAge(StroomDuration.ofDays(25));

        // no events should be processed
        processEvents(eventCount, 25, firstEventTimeMs, TimeUnit.DAYS.toMillis(1));

    }

    private void processEvents(final int eventCount,
                               final int expectedProcessedCount,
                               final long firstEventTimeMs,
                               final long eventTimeDeltaMs) {
        final Provider<SQLStatisticsConfig> configProvider = () -> getSqlStatisticsConfig()
                .withInMemAggregatorPoolSize(1)
                .withInMemPooledAggregatorSizeThreshold(1)
                .withInMemPooledAggregatorAgeThreshold(StroomDuration.ofMinutes(10));
        final SQLStatisticEventStore store = new SQLStatisticEventStore(
                null,
                mockStatisticsDataSourceCache,
                mockSqlStatisticCache,
                configProvider,
                securityContext,
                new SimpleTaskContextFactory());

        for (int i = 0; i < eventCount; i++) {
            store.putEvent(createEvent(firstEventTimeMs + (i * eventTimeDeltaMs)));
        }

        Mockito.verify(mockSqlStatisticCache, Mockito.times(expectedProcessedCount)).add(aggregateMapCaptor.capture());

        final List<SQLStatisticAggregateMap> aggregateMaps = aggregateMapCaptor.getAllValues();

        int count = 0;
        for (final SQLStatisticAggregateMap aggregateMap : aggregateMaps) {
            count += aggregateMap.size();
        }

        assertThat(aggregateMaps).isNotNull();

        assertThat(count).isEqualTo(expectedProcessedCount);
    }
}
