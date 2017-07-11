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

package stroom.statistics.server.sql;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import stroom.node.server.MockStroomPropertyService;
import stroom.query.api.v1.DocRef;
import stroom.statistics.server.sql.datasource.StatisticStoreCache;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.util.concurrent.AtomicSequence;
import stroom.util.concurrent.SimpleExecutor;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;
import stroom.util.thread.ThreadUtil;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestSQLStatisticEventStore extends StroomUnitTest {
    private final AtomicLong createCount = new AtomicLong();
    private final AtomicLong destroyCount = new AtomicLong();
    private final AtomicLong eventCount = new AtomicLong();
    private final AtomicSequence atomicSequence = new AtomicSequence(10);
    private final MockStroomPropertyService propertyService = new MockStroomPropertyService();
    private final StatisticStoreCache mockStatisticsDataSourceCache = new StatisticStoreCache() {
        @Override
        public StatisticStoreEntity getStatisticsDataSource(final String statisticName) {
            final StatisticStoreEntity statisticsDataSource = new StatisticStoreEntity();
            statisticsDataSource.setName(statisticName);
            statisticsDataSource.setPrecision(1000L);

            return statisticsDataSource;
        }

        @Override
        public StatisticStoreEntity getStatisticsDataSource(final DocRef docRef) {
            return null;
        }
    };
    @Mock
    private SQLStatisticCache mockSqlStatisticCache;
    @Captor
    private ArgumentCaptor<SQLStatisticAggregateMap> aggregateMapCaptor;

    @Before
    public void beforeTest() {
        MockitoAnnotations.initMocks(this);
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
    public void test() {
        // Max Pool size of 5 with 10 items in the pool Add 1000 and we should
        // expect APROX the below
        final SQLStatisticEventStore store = new SQLStatisticEventStore(5, 10, 10000, null,
                mockStatisticsDataSourceCache, null, null, propertyService) {
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
            simpleExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    store.putEvent(createEvent());
                }
            });
            store.putEvent(createEvent());
        }

        simpleExecutor.stop(false);

        Assert.assertTrue(createCount.get() > 0);
        Assert.assertTrue(destroyCount.get() > 0);

    }

    @Test
    public void testIdle() {
        // Max Pool size of 5 with 10 items in the pool Add 1000 and we should
        // expect APROX the below
        final SQLStatisticEventStore store = new SQLStatisticEventStore(10, 10, 100, null,
                mockStatisticsDataSourceCache, null, null, propertyService) {
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

        Assert.assertEquals(0, store.getNumIdle());
        Assert.assertEquals(0, store.getNumActive());

        store.putEvent(createEvent());

        Assert.assertEquals(1, store.getNumIdle());
        Assert.assertEquals(0, store.getNumActive());

        ThreadUtil.sleep(200);

        // store required manually evicting
        store.evict();

        Assert.assertEquals(0, store.getNumIdle());
        Assert.assertEquals(0, store.getNumActive());

    }

    @Test
    public void testEmptyPropString() {
        final int eventCount = 100;
        final long firstEventTimeMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);

        propertyService.setProperty(SQLStatisticConstants.PROP_KEY_STATS_MAX_PROCESSING_AGE, "");

        processEvents(eventCount, eventCount, firstEventTimeMs, TimeUnit.MINUTES.toMillis(1));

    }

    @Test
    public void testAllEventsTooOld() {
        final int eventCount = 100;
        // ten day old events
        final long firstEventTimeMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10);

        // only process stuff younger than a day
        propertyService.setProperty(SQLStatisticConstants.PROP_KEY_STATS_MAX_PROCESSING_AGE, "1d");

        // no events should be processed
        processEvents(eventCount, 0, firstEventTimeMs, TimeUnit.MINUTES.toMillis(1));

    }

    @Test
    public void testSomeEventsTooOld() {
        final int eventCount = 100;
        // events starting at 100 days old with each a day newer
        // add 10secs to ensure give the test time to run, otherwise it only
        // does 74
        final long firstEventTimeMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(100)
                + TimeUnit.SECONDS.toMillis(10);

        // only process stuff younger than 25days
        propertyService.setProperty(SQLStatisticConstants.PROP_KEY_STATS_MAX_PROCESSING_AGE, "25d");

        // no events should be processed
        processEvents(eventCount, 25, firstEventTimeMs, TimeUnit.DAYS.toMillis(1));

    }

    private void processEvents(final int eventCount, final int expectedProcessedCount, final long firstEventTimeMs,
                               final long eventTimeDeltaMs) {
        final SQLStatisticEventStore store = new SQLStatisticEventStore(1, 1, 10000, null,
                mockStatisticsDataSourceCache, mockSqlStatisticCache, null, propertyService);

        for (int i = 0; i < eventCount; i++) {
            store.putEvent(createEvent(firstEventTimeMs + (i * eventTimeDeltaMs)));
        }

        Mockito.verify(mockSqlStatisticCache, Mockito.times(expectedProcessedCount)).add(aggregateMapCaptor.capture());

        final List<SQLStatisticAggregateMap> aggregateMaps = aggregateMapCaptor.getAllValues();

        int count = 0;
        for (final SQLStatisticAggregateMap aggregateMap : aggregateMaps) {
            count += aggregateMap.size();
        }

        Assert.assertNotNull(aggregateMaps);

        Assert.assertEquals(expectedProcessedCount, count);
    }
}
