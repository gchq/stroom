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
import stroom.entity.shared.Period;
import stroom.entity.shared.Range;
import stroom.node.server.StroomPropertyService;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.DateExpressionParser;
import stroom.statistics.server.sql.datasource.StatisticStoreCache;
import stroom.statistics.server.sql.datasource.StatisticStoreValidator;
import stroom.statistics.server.sql.exception.StatisticsEventValidationException;
import stroom.statistics.server.sql.rollup.RollUpBitMask;
import stroom.statistics.server.sql.rollup.RolledUpStatisticEvent;
import stroom.statistics.server.sql.search.CountStatisticDataPoint;
import stroom.statistics.server.sql.search.FilterTermsTree;
import stroom.statistics.server.sql.search.FilterTermsTreeBuilder;
import stroom.statistics.server.sql.search.FindEventCriteria;
import stroom.statistics.server.sql.search.StatisticDataPoint;
import stroom.statistics.server.sql.search.StatisticDataSet;
import stroom.statistics.server.sql.search.ValueStatisticDataPoint;
import stroom.statistics.shared.StatisticStore;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.statistics.shared.StatisticType;
import stroom.statistics.shared.common.CustomRollUpMask;
import stroom.statistics.shared.common.StatisticRollUpType;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;
import stroom.util.spring.StroomFrequencySchedule;

import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class SQLStatisticEventStore implements Statistics {
    public static final Logger LOGGER = LoggerFactory.getLogger(SQLStatisticEventStore.class);
    public static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(SQLStatisticEventStore.class);


    static final String PROP_KEY_SQL_SEARCH_MAX_RESULTS = "stroom.statistics.sql.search.maxResults";

    private static final List<ExpressionTerm.Condition> SUPPORTED_DATE_CONDITIONS = Arrays.asList(ExpressionTerm.Condition.BETWEEN);


    private static final int DEFAULT_POOL_SIZE = 10;
    private static final long DEFAULT_SIZE_THRESHOLD = 1000000L;
    private static final Set<String> BLACK_LISTED_INDEX_FIELDS = Collections.emptySet();
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
    private final StatisticStoreValidator statisticsDataSourceValidator;
    private final StatisticStoreCache statisticsDataSourceCache;
    private final SQLStatisticCache statisticCache;
    private final DataSource statisticsDataSource;
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
                           @Named("statisticsDataSource") final DataSource statisticsDataSource,
                           final StroomPropertyService propertyService) {

        this.statisticsDataSourceValidator = statisticsDataSourceValidator;
        this.propertyService = propertyService;
        this.statisticsDataSourceCache = statisticsDataSourceCache;
        this.statisticCache = statisticCache;
        this.statisticsDataSource = statisticsDataSource;

        initPool(getObjectPoolConfig());
    }

    public SQLStatisticEventStore(final int poolSize,
                                  final long aggregatorSizeThreshold,
                                  final long poolAgeMsThreshold,
                                  final StatisticStoreValidator statisticsDataSourceValidator,
                                  final StatisticStoreCache statisticsDataSourceCache,
                                  final SQLStatisticCache statisticCache,
                                  final DataSource statisticsDataSource,
                                  final StroomPropertyService propertyService) {
        this.statisticsDataSourceValidator = statisticsDataSourceValidator;
        this.statisticsDataSourceCache = statisticsDataSourceCache;
        this.statisticCache = statisticCache;
        this.statisticsDataSource = statisticsDataSource;

        this.aggregatorSizeThreshold = aggregatorSizeThreshold;
        this.poolAgeMsThreshold = poolAgeMsThreshold;
        this.poolSize = poolSize;
        this.propertyService = propertyService;

        initPool(getObjectPoolConfig());
    }

    protected static FindEventCriteria buildCriteria(final SearchRequest searchRequest,
                                                     final StatisticStoreEntity dataSource) {
        LOGGER.trace("buildCriteria called for statistic {}", dataSource.getName());

        // Get the current time in millis since epoch.
        final long nowEpochMilli = System.currentTimeMillis();

        // object looks a bit like this
        // AND
        // Date Time between 2014-10-22T23:00:00.000Z,2014-10-23T23:00:00.000Z

        final ExpressionOperator topLevelExpressionOperator = searchRequest.getQuery().getExpression();

        if (topLevelExpressionOperator == null || topLevelExpressionOperator.getOp() == null) {
            throw new IllegalArgumentException(
                    "The top level operator for the query must be one of [" + ExpressionOperator.Op.values() + "]");
        }

        final List<ExpressionItem> childExpressions = topLevelExpressionOperator.getChildren();
        int validDateTermsFound = 0;
        int dateTermsFound = 0;

        // Identify the date term in the search criteria. Currently we must have
        // a exactly one BETWEEN operator on the
        // datetime
        // field to be able to search. This is because of the way the search in
        // hbase is done, ie. by start/stop row
        // key.
        // It may be possible to expand the capability to make multiple searches
        // but that is currently not in place
        ExpressionTerm dateTerm = null;
        if (childExpressions != null) {
            for (final ExpressionItem expressionItem : childExpressions) {
                if (expressionItem instanceof ExpressionTerm) {
                    final ExpressionTerm expressionTerm = (ExpressionTerm) expressionItem;

                    if (expressionTerm.getField() == null) {
                        throw new IllegalArgumentException("Expression term does not have a field specified");
                    }

                    if (expressionTerm.getField().equals(StatisticStoreEntity.FIELD_NAME_DATE_TIME)) {
                        dateTermsFound++;

                        if (SUPPORTED_DATE_CONDITIONS.contains(expressionTerm.getCondition())) {
                            dateTerm = expressionTerm;
                            validDateTermsFound++;
                        }
                    }
                } else if (expressionItem instanceof ExpressionOperator) {
                    if (((ExpressionOperator) expressionItem).getOp() == null) {
                        throw new IllegalArgumentException(
                                "An operator in the query is missing a type, it should be one of " + ExpressionOperator.Op.values());
                    }
                }
            }
        }

        // ensure we have a date term
        if (dateTermsFound != 1 || validDateTermsFound != 1) {
            throw new UnsupportedOperationException(
                    "Search queries on the statistic store must contain one term using the '"
                            + StatisticStoreEntity.FIELD_NAME_DATE_TIME
                            + "' field with one of the following condtitions [" + SUPPORTED_DATE_CONDITIONS.toString()
                            + "].  Please amend the query");
        }

        // ensure the value field is not used in the query terms
        if (contains(searchRequest.getQuery().getExpression(), StatisticStoreEntity.FIELD_NAME_VALUE)) {
            throw new UnsupportedOperationException("Search queries containing the field '"
                    + StatisticStoreEntity.FIELD_NAME_VALUE + "' are not supported.  Please remove it from the query");
        }

        // if we have got here then we have a single BETWEEN date term, so parse
        // it.
        final Range<Long> range = extractRange(dateTerm, searchRequest.getDateTimeLocale(), nowEpochMilli);

        final List<ExpressionTerm> termNodesInFilter = new ArrayList<>();
        findAllTermNodes(topLevelExpressionOperator, termNodesInFilter);

        final Set<String> rolledUpFieldNames = new HashSet<>();

        for (final ExpressionTerm term : termNodesInFilter) {
            // add any fields that use the roll up marker to the black list. If
            // somebody has said user=* then we do not
            // want that in the filter as it will slow it down. The fact that
            // they have said user=* means it will use
            // the statistic name appropriate for that rollup, meaning the
            // filtering is built into the stat name.
            if (term.getValue().equals(RollUpBitMask.ROLL_UP_TAG_VALUE)) {
                rolledUpFieldNames.add(term.getField());
            }
        }

        if (!rolledUpFieldNames.isEmpty()) {
            if (dataSource.getRollUpType().equals(StatisticRollUpType.NONE)) {
                throw new UnsupportedOperationException(
                        "Query contains rolled up terms but the Statistic Data Source does not support any roll-ups");
            } else if (dataSource.getRollUpType().equals(StatisticRollUpType.CUSTOM)) {
                if (!dataSource.isRollUpCombinationSupported(rolledUpFieldNames)) {
                    throw new UnsupportedOperationException(String.format(
                            "The query contains a combination of rolled up fields %s that is not in the list of custom roll-ups for the statistic data source",
                            rolledUpFieldNames));
                }
            }
        }

        // Date Time is handled spearately to the the filter tree so ignore it
        // in the conversion
        final Set<String> blackListedFieldNames = new HashSet<>();
        blackListedFieldNames.addAll(rolledUpFieldNames);
        blackListedFieldNames.add(StatisticStoreEntity.FIELD_NAME_DATE_TIME);

        final FilterTermsTree filterTermsTree = FilterTermsTreeBuilder
                .convertExpresionItemsTree(topLevelExpressionOperator, blackListedFieldNames);

        final FindEventCriteria criteria = FindEventCriteria.instance(new Period(range.getFrom(), range.getTo()),
                dataSource.getName(), filterTermsTree, rolledUpFieldNames);

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Searching statistics store with criteria: {}", criteria.toString());
        }

        return criteria;
    }

    /**
     * Recursive method to populates the passed list with all enabled
     * {@link ExpressionTerm} nodes found in the tree.
     */
    public static void findAllTermNodes(final ExpressionItem node, final List<ExpressionTerm> termsFound) {
        // Don't go any further down this branch if this node is disabled.
        if (node.enabled()) {
            if (node instanceof ExpressionTerm) {
                final ExpressionTerm termNode = (ExpressionTerm) node;

                termsFound.add(termNode);

            } else if (node instanceof ExpressionOperator) {
                for (final ExpressionItem childNode : ((ExpressionOperator) node).getChildren()) {
                    findAllTermNodes(childNode, termsFound);
                }
            }
        }
    }

    public static boolean contains(final ExpressionItem expressionItem, final String fieldToFind) {
        boolean hasBeenFound = false;

        if (expressionItem instanceof ExpressionOperator) {
            if (((ExpressionOperator) expressionItem).getChildren() != null) {
                for (final ExpressionItem item : ((ExpressionOperator) expressionItem).getChildren()) {
                    hasBeenFound = contains(item, fieldToFind);
                    if (hasBeenFound) {
                        break;
                    }
                }
            }
        } else {
            if (((ExpressionTerm) expressionItem).getField() != null) {
                hasBeenFound = ((ExpressionTerm) expressionItem).getField().equals(fieldToFind);
            }
        }

        return hasBeenFound;
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

    private static Range<Long> extractRange(final ExpressionTerm dateTerm, final String timeZoneId, final long nowEpochMilli) {
        final String[] dateArr = dateTerm.getValue().split(",");

        if (dateArr.length != 2) {
            throw new RuntimeException("DateTime term is not a valid format, term: " + dateTerm.toString());
        }

        long rangeFrom = DateExpressionParser.parse(dateArr[0], timeZoneId, nowEpochMilli)
                .map(time -> time.toInstant().toEpochMilli())
                .orElse(0L);
        // add one to make it exclusive
        long rangeTo = DateExpressionParser.parse(dateArr[1], timeZoneId, nowEpochMilli)
                .map(time -> time.toInstant().toEpochMilli() + 1)
                .orElse(Long.MAX_VALUE);

        final Range<Long> range = new Range<>(rangeFrom, rangeTo);

        return range;
    }

    private static long parseDateTime(final String type, final String value, final String timeZoneId, final long nowEpochMilli) {
        final ZonedDateTime dateTime;
        try {
            dateTime = DateExpressionParser.parse(value, timeZoneId, nowEpochMilli).get();
        } catch (final Exception e) {
            throw new RuntimeException("DateTime term has an invalid '" + type + "' value of '" + value + "'");
        }

        if (dateTime == null) {
            throw new RuntimeException("DateTime term has an invalid '" + type + "' value of '" + value + "'");
        }

        return dateTime.toInstant().toEpochMilli();
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

    /**
     * TODO: This is a bit simplistic as a user could create a filter that said
     * user=user1 AND user='*' which makes no sense. At the moment we would
     * assume that the user tag is being rolled up so user=user1 would never be
     * found in the data and thus would return no data.
     */
    public static RollUpBitMask buildRollUpBitMaskFromCriteria(final FindEventCriteria criteria,
                                                               final StatisticStoreEntity statisticsDataSource) {
        final Set<String> rolledUpTagsFound = criteria.getRolledUpFieldNames();

        final RollUpBitMask result;

        if (rolledUpTagsFound.size() > 0) {
            final List<Integer> rollUpTagPositionList = new ArrayList<>();

            for (final String tag : rolledUpTagsFound) {
                final Integer position = statisticsDataSource.getPositionInFieldList(tag);
                if (position == null) {
                    throw new RuntimeException(String.format("No field position found for tag %s", tag));
                }
                rollUpTagPositionList.add(position);
            }
            result = RollUpBitMask.fromTagPositions(rollUpTagPositionList);

        } else {
            result = RollUpBitMask.ZERO_MASK;
        }
        return result;
    }

//    public static boolean isDataStoreEnabled(final String engineName, final StroomPropertyService propertyService) {
//        final String enabledEngines = propertyService
//                .getProperty(CommonStatisticConstants.STROOM_STATISTIC_ENGINES_PROPERTY_NAME);
//
//        if (LOGGER.isDebugEnabled()) {
//            LOGGER.debug("{} property value: {}", CommonStatisticConstants.STROOM_STATISTIC_ENGINES_PROPERTY_NAME,
//                    enabledEngines);
//        }
//
//        boolean result = false;
//
//        if (enabledEngines != null) {
//            for (final String engine : enabledEngines.split(",")) {
//                if (engine.equals(engineName)) {
//                    result = true;
//                }
//            }
//        }
//        return result;
//    }

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

    public StatisticDataSet searchStatisticsData(final SearchRequest searchRequest, final StatisticStoreEntity dataSource) {
        final FindEventCriteria criteria = buildCriteria(searchRequest, dataSource);
        return performStatisticQuery(dataSource, criteria);
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

    private StatisticDataSet performStatisticQuery(final StatisticStoreEntity dataSource,
                                                   final FindEventCriteria criteria) {
        final Set<StatisticDataPoint> dataPoints = new HashSet<>();

        // TODO need to fingure out how we get the precision
        final StatisticDataSet statisticDataSet = new StatisticDataSet(dataSource.getName(),
                dataSource.getStatisticType(), 1000L, dataPoints);

        try (final Connection connection = statisticsDataSource.getConnection()) {
            try (final PreparedStatement preparedStatement = buildSearchPreparedStatement(dataSource, criteria, connection)) {
                LAMBDA_LOGGER.debug(() -> LambdaLogger.buildMessage("About to execute query: {}",
                        preparedStatement.toString()));

                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        final StatisticType statisticType = StatisticType.PRIMITIVE_VALUE_CONVERTER
                                .fromPrimitiveValue(resultSet.getByte(SQLStatisticNames.VALUE_TYPE));

                        final List<StatisticTag> statisticTags = extractStatisticTagsFromColumn(
                                resultSet.getString(SQLStatisticNames.NAME));
                        final long timeMs = resultSet.getLong(SQLStatisticNames.TIME_MS);

                        // the precision in the table represents the number of zeros
                        // of millisecond precision, e.g.
                        // 6=1,000,000ms
                        final long precisionMs = (long) Math.pow(10, resultSet.getInt(SQLStatisticNames.PRECISION));

                        StatisticDataPoint statisticDataPoint;

                        if (StatisticType.COUNT.equals(statisticType)) {
                            statisticDataPoint = new CountStatisticDataPoint(timeMs, precisionMs, statisticTags,
                                    resultSet.getLong(SQLStatisticNames.COUNT));
                        } else {
                            final double aggregatedValue = resultSet.getDouble(SQLStatisticNames.VALUE);
                            final long count = resultSet.getLong(SQLStatisticNames.COUNT);

                            // the aggregateValue is sum of all values against that
                            // key/time. We therefore need to get the
                            // average using the count column
                            final double averagedValue = count != 0 ? (aggregatedValue / count) : 0;

                            // min/max are not supported by SQL stats so use -1
                            statisticDataPoint = new ValueStatisticDataPoint(timeMs, precisionMs, statisticTags,
                                    count, averagedValue);
                        }

                        statisticDataSet.addDataPoint(statisticDataPoint);
                    }
                }
            }
        } catch (final SQLException sqlEx) {
            LOGGER.error("performStatisticQuery failed", sqlEx);
            throw new RuntimeException("performStatisticQuery failed", sqlEx);
        }
        return statisticDataSet;
    }

    /**
     * @param columnValue The value from the STAT_KEY.NAME column which could be of the
     *                    form 'StatName' or 'StatName¬Tag1¬Tag1Val1¬Tag2¬Tag2Val1'
     * @return A list of {@link StatisticTag} objects built from the tag/value
     * token pairs in the string or an empty list if there are none.
     */
    private List<StatisticTag> extractStatisticTagsFromColumn(final String columnValue) {
        final String[] tokens = columnValue.split(SQLStatisticConstants.NAME_SEPARATOR);
        final List<StatisticTag> statisticTags = new ArrayList<>();

        if (tokens.length == 1) {
            // no separators so there are no tags
        } else if (tokens.length % 2 == 0) {
            throw new RuntimeException(
                    String.format("Expecting an odd number of tokens, columnValue: %s", columnValue));
        } else {
            // stat name will be at pos 0 so start at 1
            for (int i = 1; i < tokens.length; i++) {
                final String tag = tokens[i++];
                String value = tokens[i];
                if (value.equals(SQLStatisticConstants.NULL_VALUE_STRING)) {
                    value = null;
                }
                final StatisticTag statisticTag = new StatisticTag(tag, value);
                statisticTags.add(statisticTag);
            }
        }

        return statisticTags;
    }

    private PreparedStatement buildSearchPreparedStatement(final StatisticStoreEntity dataSource,
                                                           final FindEventCriteria criteria, final Connection connection) throws SQLException {
        final RollUpBitMask rollUpBitMask = SQLStatisticEventStore.buildRollUpBitMaskFromCriteria(criteria, dataSource);

        final String statNameWithMask = dataSource.getName() + rollUpBitMask.asHexString();

        final List<String> extraBindVariables = new ArrayList<>();

        String sqlQuery = STAT_QUERY_SKELETON + " ";

        final String whereClause = SQLTagValueWhereClauseConverter
                .buildTagValueWhereClause(criteria.getFilterTermsTree(), extraBindVariables);

        if (whereClause != null && whereClause.length() != 0) {
            sqlQuery += " AND " + whereClause;
        }

        final int maxResults = propertyService.getIntProperty(PROP_KEY_SQL_SEARCH_MAX_RESULTS, 100000);
        sqlQuery += " LIMIT " + maxResults;

        LOGGER.debug("Search query: {}", sqlQuery);

        final PreparedStatement ps = connection.prepareStatement(sqlQuery);
        int position = 1;

        // do a like on the name first so we can hit the index before doing the
        // slow regex matches
        ps.setString(position++, statNameWithMask + "%");
        // regex to match on the stat name which is always at the start of the
        // string and either has a
        ps.setString(position++, "^" + statNameWithMask + "(" + SQLStatisticConstants.NAME_SEPARATOR + "|$)");

        // set the start/end dates
        ps.setLong(position++, criteria.getPeriod().getFromMs());
        ps.setLong(position++, criteria.getPeriod().getToMs());

        for (final String bindVariable : extraBindVariables) {
            ps.setString(position++, bindVariable);
        }

        return ps;
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

//    public boolean isDataStoreEnabled() {
//        return SQLStatisticEventStore.isDataStoreEnabled(getEngineName(), propertyService);
//    }

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