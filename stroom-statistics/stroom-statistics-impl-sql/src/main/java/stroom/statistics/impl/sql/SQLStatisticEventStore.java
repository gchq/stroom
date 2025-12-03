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

package stroom.statistics.impl.sql;

import stroom.query.api.datasource.QueryField;
import stroom.security.api.SecurityContext;
import stroom.statistics.impl.sql.entity.StatisticStoreCache;
import stroom.statistics.impl.sql.entity.StatisticStoreValidator;
import stroom.statistics.impl.sql.exception.StatisticsEventValidationException;
import stroom.statistics.impl.sql.rollup.RollUpBitMask;
import stroom.statistics.impl.sql.rollup.RolledUpStatisticEvent;
import stroom.statistics.impl.sql.shared.CustomRollUpMask;
import stroom.statistics.impl.sql.shared.StatisticRollUpType;
import stroom.statistics.impl.sql.shared.StatisticStore;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.task.api.TaskContextFactory;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;
import stroom.util.time.TimeUtils;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObjectInfo;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Singleton
public class SQLStatisticEventStore implements Statistics, HasSystemInfo {

    public static final Logger LOGGER = LoggerFactory.getLogger(SQLStatisticEventStore.class);

    private static final Set<String> BLACK_LISTED_INDEX_FIELDS = Collections.emptySet();
    /**
     * Keep half the time out of our SQL insert threshold
     */
    private final StatisticStoreValidator statisticsDataSourceValidator;
    private final StatisticStoreCache statisticsDataSourceCache;
    private final SQLStatisticCache statisticCache;
    private final Provider<SQLStatisticsConfig> configProvider;
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;

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

    private GenericObjectPool<SQLStatisticAggregateMap> objectPool;
    private AtomicBoolean isShutdown = new AtomicBoolean(false);

    @Inject
    SQLStatisticEventStore(final StatisticStoreValidator statisticsDataSourceValidator,
                           final StatisticStoreCache statisticsDataSourceCache,
                           final SQLStatisticCache statisticCache,
                           final Provider<SQLStatisticsConfig> configProvider,
                           final SecurityContext securityContext,
                           final TaskContextFactory taskContextFactory) {
        this.statisticsDataSourceValidator = statisticsDataSourceValidator;
        this.configProvider = configProvider;
        this.statisticsDataSourceCache = statisticsDataSourceCache;
        this.statisticCache = statisticCache;
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;

        initPool(getObjectPoolConfig(configProvider.get()));
    }

    // TODO could go further up the chain so is store agnostic
    public static RolledUpStatisticEvent generateTagRollUps(final StatisticEvent event,
                                                            final StatisticStoreDoc statisticsDataSource) {
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
            for (final CustomRollUpMask mask : statisticsDataSource.getConfig()
                    .getCustomRollUpMasks()) {
                final RollUpBitMask rollUpBitMask = RollUpBitMask.fromTagPositions(mask.getRolledUpTagPosition());

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

    private GenericObjectPoolConfig<SQLStatisticAggregateMap> getObjectPoolConfig(
            final SQLStatisticsConfig sqlStatisticsConfig) {

        final GenericObjectPoolConfig<SQLStatisticAggregateMap> config = new GenericObjectPoolConfig<>();
        // Max number of idle items .... same as our pool size
        config.setMaxIdle(sqlStatisticsConfig.getInMemAggregatorPoolSize());
        // Pool size
        config.setMaxTotal(sqlStatisticsConfig.getInMemAggregatorPoolSize());
        // Returns the minimum amount of time an object may sit idle in the pool
        // before it is eligible for eviction by the idle object evictor
        // Here if it is idle for 10 min's it will simply return It will also
        // return by validateObject if it is simple more than 10min old
        config.setMinEvictableIdleTimeMillis(sqlStatisticsConfig.getInMemPooledAggregatorAgeThreshold().toMillis());
        // Check for idle objects never .... we will do this with task sytstem
        config.setTimeBetweenEvictionRunsMillis(0);
        // Must cause other threads to block to wait for a object
        config.setBlockWhenExhausted(true);
        config.setJmxEnabled(false);
        // Check item on just before returning to pool
        config.setTestOnReturn(true);

        return config;
    }

    public void evict() {
        taskContextFactory.current().info(() -> "Evicting expired objects");
        LOGGER.debug("evict");
        try {
            objectPool.evict();
        } catch (final Exception e) {
            LOGGER.error("evict", e);
        }
    }

    private void initPool(final GenericObjectPoolConfig<SQLStatisticAggregateMap> config) {
        objectPool = new GenericObjectPool<>(new ObjectFactory(), config);
    }

    /**
     * @return A predicate that will test if an event is within the processing age threshold, or if there
     * is no configured max age then process all events
     */
    private Predicate<StatisticEvent> getInsideProcessingThresholdPredicate() {
        return Optional
                .ofNullable(configProvider.get().getMaxProcessingAge())
                .map(TimeUtils::durationToThreshold)
                .map(threshold ->
                        (Predicate<StatisticEvent>) statisticEvent ->
                                statisticEvent.getTimeMs() > threshold.toEpochMilli())
                .orElse(statisticEvent -> true);
    }

    public SQLStatisticAggregateMap createAggregateMap() {
        return new SQLStatisticAggregateMap();
    }

    public void destroyAggregateMap(final SQLStatisticAggregateMap map) {
        LOGGER.debug("destroyAggregateMap - Flushing map size={}", map.size());
        statisticCache.add(map);
    }

    @Override
    public void putEvents(final List<StatisticEvent> statisticEvents,
                          final StatisticStore statisticStore) {
        Objects.requireNonNull(statisticEvents);
        Objects.requireNonNull(statisticStore);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("putEvents - count={}", statisticEvents.size());
        }

        if (!isShutdown.get()) {
            final StatisticStoreDoc entity = (StatisticStoreDoc) statisticStore;

            // validate the first stat in the batch to check we have a statistic
            // data source for it.
            if (!validateStatisticDataSource(statisticEvents.iterator().next(), entity)) {
                // no StatisticsDataSource entity so don't record the stat as we
                // will have no way of querying the stat
                throw new RuntimeException(String.format("Invalid or missing statistic data source with name %s",
                        entity.getName()));
            }

            final Predicate<StatisticEvent> insideProcessingThresholdPredicate =
                    getInsideProcessingThresholdPredicate();

            try {
                final SQLStatisticAggregateMap statisticAggregateMap = objectPool.borrowObject();
                try {
                    for (final StatisticEvent statisticEvent : statisticEvents) {
                        // Only process a stat if it is inside the processing
                        // threshold

                        if (insideProcessingThresholdPredicate.test(statisticEvent)) {
                            final RolledUpStatisticEvent rolledUpStatisticEvent = generateTagRollUps(statisticEvent,
                                    entity);
                            statisticAggregateMap.addRolledUpEvent(rolledUpStatisticEvent, entity.getPrecision());
                        }
                    }
                } finally {
                    objectPool.returnObject(statisticAggregateMap);
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.error("putEvent()", e);
            } catch (final Exception e) {
                throw new RuntimeException("Exception adding statistics to the aggregateMap: " + e.getMessage(), e);
            }
        } else {
            LOGGER.error("Unable to proccess batch of statistic events with size {} as the system is shutting down",
                    statisticEvents.size());
        }
    }

    @Override
    public void putEvent(final StatisticEvent statisticEvent, final StatisticStore statisticStore) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("putEvent - count=1");
        }

        final StatisticStoreDoc entity = (StatisticStoreDoc) statisticStore;

        // validate the first stat in the batch to check we have a statistic
        // data source for it.
        if (!validateStatisticDataSource(statisticEvent, entity)) {
            // no StatisticsDataSource entity so don't record the stat as we
            // will have no way of querying the stat
            throw new RuntimeException(String.format("Invalid or missing statistic datasource with name %s",
                    entity.getName()));
        }

        final Predicate<StatisticEvent> insideProcessingThresholdPredicate = getInsideProcessingThresholdPredicate();

        // Only process a stat if it is inside the processing threshold
        if (insideProcessingThresholdPredicate.test(statisticEvent)) {
            final RolledUpStatisticEvent rolledUpStatisticEvent = generateTagRollUps(statisticEvent, entity);
            try {
                // Will block until an object is available to borrow
                final SQLStatisticAggregateMap statisticAggregateMap = objectPool.borrowObject();
                try {
                    statisticAggregateMap.addRolledUpEvent(rolledUpStatisticEvent, entity.getPrecision());
                } finally {
                    objectPool.returnObject(statisticAggregateMap);
                }
            } catch (final StatisticsEventValidationException seve) {
                throw new RuntimeException(seve.getMessage(), seve);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.error("putEvent()", e);
                throw new RuntimeException("Exception adding statistics to the aggregateMap", e);
            } catch (final Exception e) {
                LOGGER.error("putEvent()", e);
                throw new RuntimeException("Exception adding statistics to the aggregateMap", e);
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
        // Mark the shutdown as happening so we can stop new things being borrowed from the pool
        isShutdown.set(true);

        // now we just need to worry about maps already borrowed or anyone blocked waiting.

        // Wait for anyone blocked waiting to borrow. If we don't wait for these then when close
        // is called on the pool they will be interupted and thus the stats are lost
        securityContext.asProcessingUser(() -> {
            if (objectPool.getNumWaiters() > 0) {
                LOGGER.info("Waiting for SQLStatisticAggregateMaps waiters, active: {}, waiting: {}",
                        objectPool.getNumActive(),
                        objectPool.getNumWaiters());
                while (objectPool.getNumWaiters() > 0) {
                    try {
                        Thread.sleep(200);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        LOGGER.error("Thread interrupted while waiting for object pool waiters", e);
                    }
                }
            }

            // Anything in the pool will be flushed, any borrowed items will be flushed when they are returned
            LOGGER.info("Closing object pool");
            objectPool.close();

            if (objectPool.getNumActive() > 0) {
                LOGGER.info("Waiting for SQLStatisticAggregateMaps to be returned to the pool, active: {}, waiting: {}",
                        objectPool.getNumActive(),
                        objectPool.getNumWaiters());
                while (objectPool.getNumActive() > 0) {
                    try {
                        Thread.sleep(200);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        LOGGER.error("Thread interrupted while waiting for pool objects to be returned", e);
                    }
                }
            }

            // Now all our maps have been given to the cache, flush the cache.
            LOGGER.info("Flushing map cache");
            statisticCache.flush(true);
        });
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
        final StatisticStoreDoc statisticsDataSource = getStatisticsDataSource(statisticEvent.getName());
        putEvents(Collections.singletonList(statisticEvent), statisticsDataSource);
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
            final StatisticStoreDoc statisticsDataSource = getStatisticsDataSource(statName);

            if (statisticsDataSource == null) {
                throw new RuntimeException(String.format("No statistic data source exists for name %s", statName));
            }
            putEvents(eventsBatch, statisticsDataSource);
        }
    }

    protected boolean validateStatisticDataSource(final StatisticEvent statisticEvent,
                                                  final StatisticStoreDoc statisticsDataSource) {
        if (statisticsDataSourceValidator != null) {
            return statisticsDataSourceValidator.validateStatisticDataSource(statisticEvent.getName(),
                    statisticEvent.getType(), statisticsDataSource);
        } else {
            // no validator has been supplied so return true
            return true;
        }
    }

    protected StatisticStoreDoc getStatisticsDataSource(final String statisticName) {
        return statisticsDataSourceCache.getStatisticsDataSource(statisticName);
    }

    @Override
    public List<QueryField> getSupportedFields(final List<QueryField> indexFields) {
        final Set<String> blackList = getIndexFieldBlackList();

        if (blackList.size() == 0) {
            // nothing blacklisted so just return the standard list from the
            // data source
            return indexFields;
        } else {
            // construct an anonymous class instance that will filter out black
            // listed index fields, as supplied by the
            // sub-class
            final List<QueryField> supportedIndexFields = new ArrayList<>();
            indexFields.stream()
                    .filter(indexField -> !blackList.contains(indexField.getFldName()))
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

    @Override
    public SystemInfoResult getSystemInfo() {
        final List<String> poolDetails;
        if (objectPool == null) {
            poolDetails = null;
        } else {
            poolDetails = objectPool.listAllObjects()
                    .stream()
                    .map(DefaultPooledObjectInfo::getPooledObjectToString)
                    .collect(Collectors.toList());
        }

        return SystemInfoResult.builder(this)
                .addDetail("poolObjects", poolDetails)
                .build();
    }

    private class ObjectFactory extends BasePooledObjectFactory<SQLStatisticAggregateMap> {

        @Override
        public SQLStatisticAggregateMap create() {
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
        public boolean validateObject(final PooledObject<SQLStatisticAggregateMap> pooledObject) {
            final SQLStatisticsConfig sqlStatisticsConfig = configProvider.get();
            if (pooledObject.getObject().size() >= sqlStatisticsConfig.getInMemPooledAggregatorSizeThreshold()) {
                return false;
            }
            final long pooledObjectAgeMs = System.currentTimeMillis() - pooledObject.getCreateTime();
            if (pooledObjectAgeMs > sqlStatisticsConfig.getInMemPooledAggregatorAgeThreshold().toMillis()) {
                return false;
            }

            return super.validateObject(pooledObject);
        }
    }
}
