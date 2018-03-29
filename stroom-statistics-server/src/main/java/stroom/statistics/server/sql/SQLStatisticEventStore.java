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

import com.google.common.base.Preconditions;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import stroom.datasource.api.v2.DataSourceField;
import stroom.node.server.StroomPropertyService;
import stroom.statistics.server.sql.datasource.StatisticStoreCache;
import stroom.statistics.server.sql.datasource.StatisticStoreValidator;
import stroom.statistics.server.sql.exception.StatisticsEventValidationException;
import stroom.statistics.server.sql.rollup.RollUpBitMask;
import stroom.statistics.server.sql.rollup.RolledUpStatisticEvent;
import stroom.statistics.shared.StatisticStore;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.statistics.shared.common.CustomRollUpMask;
import stroom.statistics.shared.common.StatisticRollUpType;
import stroom.util.shared.ModelStringUtil;
import stroom.util.spring.StroomFrequencySchedule;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class SQLStatisticEventStore implements Statistics {
    public static final Logger LOGGER = LoggerFactory.getLogger(SQLStatisticEventStore.class);

    private static final int DEFAULT_POOL_SIZE = 10;
    private static final long DEFAULT_SIZE_THRESHOLD = 1000000L;
    private static final Set<String> BLACK_LISTED_INDEX_FIELDS = Collections.emptySet();
    /**
     * Keep half the time out our SQL insert threshold
     */
    private static final long DEFAULT_AGE_MS_THRESHOLD = TimeUnit.MINUTES.toMillis(5);
    private final StatisticStoreValidator statisticsDataSourceValidator;
    private final StatisticStoreCache statisticsDataSourceCache;
    private final SQLStatisticCache statisticCache;
    private final StroomPropertyService propertyService;
    /**
     * SQL for testing querying the stat/tag names
     * <p>
     * create table test (name varchar(255)) ENGINE=InnoDB DEFAULT
     * CHARSET=latin1;
     * <p>
     * insert into test values ('StatName1');
     * <p>
     * insert into test values ('StatName2¬Tag1¬Val1¬Tag2¬Val2');
     * <p>
     * insert into test values ('StatName2¬Tag2¬Val2¬Tag1¬Val1');
     * <p>
     * select * from test where name REGEXP '^StatName1(¬|$)';
     * <p>
     * select * from test where name REGEXP '¬Tag1¬Val1(¬|$)';
     */

    private long poolAgeMsThreshold = DEFAULT_AGE_MS_THRESHOLD;
    private long aggregatorSizeThreshold = DEFAULT_SIZE_THRESHOLD;
    private int poolSize = DEFAULT_POOL_SIZE;
    private GenericObjectPool<SQLStatisticAggregateMap> objectPool;

    @Inject
    SQLStatisticEventStore(final StatisticStoreValidator statisticsDataSourceValidator,
                           final StatisticStoreCache statisticsDataSourceCache,
                           final SQLStatisticCache statisticCache,
                           final StroomPropertyService propertyService) {

        this.statisticsDataSourceValidator = statisticsDataSourceValidator;
        this.propertyService = propertyService;
        this.statisticsDataSourceCache = statisticsDataSourceCache;
        this.statisticCache = statisticCache;

        initPool(getObjectPoolConfig());
    }

    public SQLStatisticEventStore(final int poolSize,
                                  final long aggregatorSizeThreshold,
                                  final long poolAgeMsThreshold,
                                  final StatisticStoreValidator statisticsDataSourceValidator,
                                  final StatisticStoreCache statisticsDataSourceCache,
                                  final SQLStatisticCache statisticCache,
                                  final StroomPropertyService propertyService) {
        this.statisticsDataSourceValidator = statisticsDataSourceValidator;
        this.statisticsDataSourceCache = statisticsDataSourceCache;
        this.statisticCache = statisticCache;
        this.aggregatorSizeThreshold = aggregatorSizeThreshold;
        this.poolAgeMsThreshold = poolAgeMsThreshold;
        this.poolSize = poolSize;
        this.propertyService = propertyService;

        initPool(getObjectPoolConfig());
    }


    // TODO could go futher up the chain so is store agnostic
    public static RolledUpStatisticEvent generateTagRollUps(final StatisticEvent event,
                                                            final StatisticStoreEntity statisticsDataSource) {
        RolledUpStatisticEvent rolledUpStatisticEvent = null;

        final int eventTagListSize = event.getTagList().size();

        final StatisticRollUpType rollUpType = statisticsDataSource.getRollUpType();

        if (eventTagListSize == 0 || StatisticRollUpType.NONE.equals(rollUpType)) {
            rolledUpStatisticEvent = new RolledUpStatisticEvent(event);
        } else if (StatisticRollUpType.ALL.equals(rollUpType)) {
            final List<List<StatisticTag>> tagListPerms = generateStatisticTagPerms(event.getTagList(),
                    RollUpBitMask.getRollUpPermutationsAsBooleans(eventTagListSize));

            // wrap the original event along with the perms list
            rolledUpStatisticEvent = new RolledUpStatisticEvent(event, tagListPerms);

        } else if (StatisticRollUpType.CUSTOM.equals(rollUpType)) {
            final Set<List<Boolean>> perms = new HashSet<>();
            for (final CustomRollUpMask mask : statisticsDataSource.getStatisticDataSourceDataObject()
                    .getCustomRollUpMasks()) {
                final RollUpBitMask rollUpBitMask = RollUpBitMask.fromTagPositions(mask.getRolledUpTagPositions());

                perms.add(rollUpBitMask.getBooleanMask(eventTagListSize));
            }
            final List<List<StatisticTag>> tagListPerms = generateStatisticTagPerms(event.getTagList(), perms);

            rolledUpStatisticEvent = new RolledUpStatisticEvent(event, tagListPerms);
        }

        return rolledUpStatisticEvent;
    }


    private static List<List<StatisticTag>> generateStatisticTagPerms(final List<StatisticTag> eventTags,
                                                                      final Set<List<Boolean>> perms) {
        final List<List<StatisticTag>> tagListPerms = new ArrayList<>();
        final int eventTagListSize = eventTags.size();

        for (final List<Boolean> perm : perms) {
            final List<StatisticTag> tags = new ArrayList<>();
            for (int i = 0; i < eventTagListSize; i++) {
                if (perm.get(i).booleanValue() == true) {
                    // true means a rolled up tag so create a new tag with the
                    // rolled up marker
                    tags.add(new StatisticTag(eventTags.get(i).getTag(), RollUpBitMask.ROLL_UP_TAG_VALUE));
                } else {
                    // false means not rolled up so use the existing tag's value
                    tags.add(eventTags.get(i));
                }
            }
            tagListPerms.add(tags);
        }
        return tagListPerms;
    }

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
        LOGGER.debug("destroyAggregateMap - Flushing map size={}", map.size());
        statisticCache.add(map);
    }

    @Override
    public void putEvents(final List<StatisticEvent> statisticEvents, final StatisticStore statisticStore) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("putEvents - count={}", statisticEvents.size());
        }

        final Long optionalEventProcessingThresholdMs = getEventProcessingThresholdMs();

        final StatisticStoreEntity entity = (StatisticStoreEntity) Preconditions.checkNotNull(statisticStore);

        // validate the first stat in the batch to check we have a statistic
        // data source for it.
        if (validateStatisticDataSource(statisticEvents.iterator().next(), entity) == false) {
            // no StatisticsDataSource entity so don't record the stat as we
            // will have no way of querying the stat
            throw new RuntimeException(String.format("Invalid or missing statistic datasource with name %s", entity.getName()));
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
        }
    }

    @Override
    public void putEvent(final StatisticEvent statisticEvent, final StatisticStore statisticStore) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("putEvent - count=1");
        }

        final StatisticStoreEntity entity = (StatisticStoreEntity) statisticStore;

        // validate the first stat in the batch to check we have a statistic
        // data source for it.
        if (validateStatisticDataSource(statisticEvent, entity) == false) {
            // no StatisticsDataSource entity so don't record the stat as we
            // will have no way of querying the stat
            throw new RuntimeException(String.format("Invalid or missing statistic datasource with name %s", entity.getName()));
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
                throw new RuntimeException(String.format("Exception adding statistics to the aggregateMap"), ex);
            }
        }
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


    @Override
    public void putEvent(final StatisticEvent statisticEvent) {
        final StatisticStoreEntity statisticsDataSource = getStatisticsDataSource(statisticEvent.getName());
        putEvent(statisticEvent, statisticsDataSource);
    }

    @Override
    public void putEvents(final List<StatisticEvent> statisticEvents) {

        statisticEvents.stream()
                .collect(Collectors.groupingBy(StatisticEvent::getName, Collectors.toList()))
                .values()
                .forEach(this::putBatch);
    }

    /**
     * @param eventsBatch A batch of {@link StatisticEvent} all with the same statistic name
     */
    private void putBatch(final List<StatisticEvent> eventsBatch) {
        if (eventsBatch.size() > 0) {
            final StatisticEvent firstEventInBatch = eventsBatch.get(0);
            final String statName = firstEventInBatch.getName();
            final StatisticStoreEntity statisticsDataSource = getStatisticsDataSource(statName);

            if (statisticsDataSource == null) {
                throw new RuntimeException(String.format("No statistic data source exists for name %s", statName));
            }
            putEvents(eventsBatch, statisticsDataSource);
            eventsBatch.clear();
        }
    }

    protected boolean validateStatisticDataSource(final StatisticEvent statisticEvent,
                                                  final StatisticStoreEntity statisticsDataSource) {
        if (statisticsDataSourceValidator != null) {
            return statisticsDataSourceValidator.validateStatisticDataSource(statisticEvent.getName(),
                    statisticEvent.getType(), statisticsDataSource);
        } else {
            // no validator has been supplied so return true
            return true;
        }
    }

    protected StatisticStoreEntity getStatisticsDataSource(final String statisticName) {
        return statisticsDataSourceCache.getStatisticsDataSource(statisticName);
    }

    public List<DataSourceField> getSupportedFields(final List<DataSourceField> indexFields) {
        final Set<String> blackList = getIndexFieldBlackList();

        if (blackList.size() == 0) {
            // nothing blacklisted so just return the standard list from the
            // data source
            return indexFields;
        } else {
            // construct an anonymous class instance that will filter out black
            // listed index fields, as supplied by the
            // sub-class
            final List<DataSourceField> supportedIndexFields = new ArrayList<>();
            indexFields.stream()
                    .filter(indexField -> !blackList.contains(indexField.getName()))
                    .forEach(supportedIndexFields::add);

            return supportedIndexFields;
        }
    }

    public List<Set<Integer>> getFieldPositionsForBitMasks(final List<Short> maskValues) {
        if (maskValues != null) {
            final List<Set<Integer>> tagPosPermsList = new ArrayList<>();

            for (final Short maskValue : maskValues) {
                tagPosPermsList.add(RollUpBitMask.fromShort(maskValue).getTagPositions());
            }
            return tagPosPermsList;
        } else {
            return Collections.emptyList();
        }
    }


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
}