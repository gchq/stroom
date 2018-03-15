/*
 * Copyright 2016 Crown Copyright
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

package stroom.statistics.sql;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import stroom.node.server.StroomPropertyService;
import stroom.statistics.common.RolledUpStatisticEvent;
import stroom.statistics.common.StatisticEvent;
import stroom.statistics.common.StatisticStoreCache;
import stroom.statistics.common.StatisticStoreValidator;
import stroom.statistics.common.exception.StatisticsEventValidationException;
import stroom.statistics.server.common.AbstractStatistics;
import stroom.statistics.shared.StatisticStore;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.ModelStringUtil;
import stroom.util.spring.StroomFrequencySchedule;

import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class SQLStatisticEventStore extends AbstractStatistics {
    public static final StroomLogger LOGGER = StroomLogger.getLogger(SQLStatisticEventStore.class);

    public static final String ENGINE_NAME = "sql";

    private final SQLStatisticCache statisticCache;
    private final StroomPropertyService propertyService;

    private static final int DEFAULT_POOL_SIZE = 10;
    private static final long DEFAULT_SIZE_THRESHOLD = 1000000L;

    private static final Set<String> BLACK_LISTED_INDEX_FIELDS = new HashSet<>(
            Arrays.asList(StatisticStoreEntity.FIELD_NAME_MIN_VALUE, StatisticStoreEntity.FIELD_NAME_MAX_VALUE));

    /**
     * Keep half the time out our SQL insert threshold
     */
    private static final long DEFAULT_AGE_MS_THRESHOLD = TimeUnit.MINUTES.toMillis(5);

    // @formatter:off
    private static final String STAT_QUERY_SKELETON = "" + "select " + "K." + SQLStatisticNames.NAME + ", " + "V."
            + SQLStatisticNames.PRECISION + ", " + "V." + SQLStatisticNames.TIME_MS + ", " + "V."
            + SQLStatisticNames.VALUE_TYPE + ", " + "V." + SQLStatisticNames.VALUE + ", " + "V."
            + SQLStatisticNames.COUNT + " " + "FROM " + SQLStatisticNames.SQL_STATISTIC_KEY_TABLE_NAME + " K " + "JOIN "
            + SQLStatisticNames.SQL_STATISTIC_VALUE_TABLE_NAME + " V ON (K." + SQLStatisticNames.ID + " = V."
            + SQLStatisticNames.SQL_STATISTIC_KEY_FOREIGN_KEY + ") " + "WHERE K." + SQLStatisticNames.NAME + " LIKE ? "
            + "AND K." + SQLStatisticNames.NAME + " REGEXP ? " + "AND V." + SQLStatisticNames.TIME_MS + " >= ? "
            + "AND V." + SQLStatisticNames.TIME_MS + " < ?";

    // @formatter:on

    private long poolAgeMsThreshold = DEFAULT_AGE_MS_THRESHOLD;
    private long aggregatorSizeThreshold = DEFAULT_SIZE_THRESHOLD;
    private int poolSize = DEFAULT_POOL_SIZE;

    private GenericObjectPool<SQLStatisticAggregateMap> objectPool;

    private class ObjectFactory extends BasePooledObjectFactory<SQLStatisticAggregateMap> {
        @Override
        public SQLStatisticAggregateMap create() throws Exception {
            return createAggregateMap();
        }

        @Override
        public PooledObject<SQLStatisticAggregateMap> wrap(final SQLStatisticAggregateMap obj) {
            return new DefaultPooledObject<>(obj);
        }

        @Override
        public void destroyObject(final PooledObject<SQLStatisticAggregateMap> p) throws Exception {
            super.destroyObject(p);
            destroyAggregateMap(p.getObject());
        }

        /**
         * Should we give this item back to the pool
         */
        @Override
        public boolean validateObject(final PooledObject<SQLStatisticAggregateMap> p) {
            if (p.getObject().size() >= aggregatorSizeThreshold) {
                return false;
            }
            final long age = System.currentTimeMillis() - p.getCreateTime();
            if (age > poolAgeMsThreshold) {
                return false;
            }

            return super.validateObject(p);
        }
    }

    @Override
    protected Set<String> getIndexFieldBlackList() {
        return BLACK_LISTED_INDEX_FIELDS;
    }

    private GenericObjectPoolConfig getObjectPoolConfig() {
        final GenericObjectPoolConfig config = new GenericObjectPoolConfig();
        // Max number of idle items .... same as our pool size
        config.setMaxIdle(poolSize);
        // Pool size
        config.setMaxTotal(poolSize);
        // Returns the minimum amount of time an object may sit idle in the pool
        // before it is eligible for eviction by the idle object evictor
        // Here if it is idle for 10 min's it will simply return It will also
        // return by validateObject if it is simple more than 10min old
        config.setMinEvictableIdleTimeMillis(poolAgeMsThreshold);
        // Check for idle objects never .... we will do this with task sytstem
        config.setTimeBetweenEvictionRunsMillis(0);
        // Must cause other threads to block to wait for a object
        config.setBlockWhenExhausted(true);
        config.setJmxEnabled(false);
        // Check item on just before returning to pool
        config.setTestOnReturn(true);

        return config;
    }

    @Inject
    SQLStatisticEventStore(final StatisticStoreValidator statisticsDataSourceValidator,
                           final StatisticStoreCache statisticsDataSourceCache, final SQLStatisticCache statisticCache,
                           final StroomPropertyService propertyService) {
        super(statisticsDataSourceValidator, statisticsDataSourceCache, propertyService);
        this.statisticCache = statisticCache;
        this.propertyService = propertyService;

        initPool(getObjectPoolConfig());
    }

    public SQLStatisticEventStore(final int poolSize, final long aggregatorSizeThreshold, final long poolAgeMsThreshold,
                                  final StatisticStoreValidator statisticsDataSourceValidator,
                                  final StatisticStoreCache statisticsDataSourceCache, final SQLStatisticCache statisticCache,
                                  final StroomPropertyService propertyService) {
        super(statisticsDataSourceValidator, statisticsDataSourceCache, propertyService);
        this.statisticCache = statisticCache;

        this.aggregatorSizeThreshold = aggregatorSizeThreshold;
        this.poolAgeMsThreshold = poolAgeMsThreshold;
        this.poolSize = poolSize;
        this.propertyService = propertyService;

        initPool(getObjectPoolConfig());
    }

    @Override
    public String getEngineName() {
        return ENGINE_NAME;
    }

    @StroomFrequencySchedule("1m")
    public void evict() {
        LOGGER.debug("evict");
        try {
            objectPool.evict();
        } catch (final Exception ex) {
            LOGGER.error("evict", ex);
        }
    }

    private void initPool(final GenericObjectPoolConfig config) {
        objectPool = new GenericObjectPool<>(new ObjectFactory(), config);

    }

    /**
     * Get the threshold datetime (ms) for processing a statistic
     *
     * @return The threshold in ms since unix epoch
     */
    private Long getEventProcessingThresholdMs() {
        final String eventProcessingThresholdStr = propertyService
                .getProperty(SQLStatisticConstants.PROP_KEY_STATS_MAX_PROCESSING_AGE);

        if (StringUtils.hasText(eventProcessingThresholdStr)) {
            final long duration = ModelStringUtil.parseDurationString(eventProcessingThresholdStr);
            return Long.valueOf(System.currentTimeMillis() - duration);
        } else {
            return null;
        }
    }

    private boolean isStatisticEventInsideProcessingThreshold(final StatisticEvent statisticEvent,
                                                              final Long optionalEventProcessingThresholdMs) {
        return statisticEvent
                .getTimeMs() > (optionalEventProcessingThresholdMs != null ? optionalEventProcessingThresholdMs : 0);
    }

    public SQLStatisticAggregateMap createAggregateMap() {
        return new SQLStatisticAggregateMap();
    }

    public void destroyAggregateMap(final SQLStatisticAggregateMap map) {
        LOGGER.debug("destroyAggregateMap - Flushing map size=%s", map.size());
        statisticCache.add(map);
    }

    @Override
    public boolean putEvents(final List<StatisticEvent> statisticEvents, final StatisticStore statisticStore) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("putEvents - count=%s", statisticEvents.size());
        }

        final Long optionalEventProcessingThresholdMs = getEventProcessingThresholdMs();

        final StatisticStoreEntity entity = (StatisticStoreEntity) statisticStore;

        // validate the first stat in the batch to check we have a statistic
        // data source for it.
        if (validateStatisticDataSource(statisticEvents.iterator().next(), entity) == false) {
            // no StatisticsDataSource entity so don't record the stat as we
            // will have no way of querying the stat
            return false;
        }

        try {
            final SQLStatisticAggregateMap statisticAggregateMap = objectPool.borrowObject();
            try {
                for (final StatisticEvent statisticEvent : statisticEvents) {
                    // Only process a stat if it is inside the processing
                    // threshold
                    if (isStatisticEventInsideProcessingThreshold(statisticEvent, optionalEventProcessingThresholdMs)) {
                        final RolledUpStatisticEvent rolledUpStatisticEvent = generateTagRollUps(statisticEvent,
                                entity);
                        statisticAggregateMap.addRolledUpEvent(rolledUpStatisticEvent, entity.getPrecision());
                    }
                }
            } finally {
                objectPool.returnObject(statisticAggregateMap);
            }
        } catch (final StatisticsEventValidationException seve) {
            throw new RuntimeException(seve.getMessage(), seve);
        } catch (final Exception ex) {
            LOGGER.error("putEvent()", ex);
            return false;
        }
        return true;
    }

    @Override
    public boolean putEvent(final StatisticEvent statisticEvent, final StatisticStore statisticStore) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("putEvent - count=1");
        }

        final StatisticStoreEntity entity = (StatisticStoreEntity) statisticStore;

        // validate the first stat in the batch to check we have a statistic
        // data source for it.
        if (validateStatisticDataSource(statisticEvent, entity) == false) {
            // no StatisticsDataSource entity so don't record the stat as we
            // will have no way of querying the stat
            return false;
        }

        // Only process a stat if it is inside the processing threshold
        if (isStatisticEventInsideProcessingThreshold(statisticEvent, getEventProcessingThresholdMs())) {
            final RolledUpStatisticEvent rolledUpStatisticEvent = generateTagRollUps(statisticEvent, entity);
            try {
                final SQLStatisticAggregateMap statisticAggregateMap = objectPool.borrowObject();
                try {
                    statisticAggregateMap.addRolledUpEvent(rolledUpStatisticEvent, entity.getPrecision());
                } finally {
                    objectPool.returnObject(statisticAggregateMap);
                }
            } catch (final StatisticsEventValidationException seve) {
                throw new RuntimeException(seve.getMessage(), seve);
            } catch (final Exception ex) {
                LOGGER.error("putEvent()", ex);
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> getValuesByTag(final String tagName) {
        throw new UnsupportedOperationException("Code waiting to be written");
    }

    @Override
    public List<String> getValuesByTagAndPartialValue(final String tagName, final String partialValue) {
        throw new UnsupportedOperationException("Code waiting to be written");
    }

    @Override
    public void flushAllEvents() {
        throw new UnsupportedOperationException("Code waiting to be written");
    }

    @Override
    public String toString() {
        return "numActive=" + objectPool.getNumActive() + ", numIdle=" + objectPool.getNumIdle();
    }

    public int getNumActive() {
        return objectPool.getNumActive();
    }

    public int getNumIdle() {
        return objectPool.getNumIdle();
    }
}
