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

package stroom.statistics.server.common.search;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import stroom.dashboard.expression.FieldIndexMap;
import stroom.entity.server.util.SqlBuilder;
import stroom.mapreduce.BlockingPairQueue;
import stroom.mapreduce.PairQueue;
import stroom.mapreduce.UnsafePairQueue;
import stroom.node.server.StroomPropertyService;
import stroom.query.CompiledDepths;
import stroom.query.CompiledFields;
import stroom.query.Item;
import stroom.query.ItemMapper;
import stroom.query.ItemPartitioner;
import stroom.query.Payload;
import stroom.query.TableCoprocessorSettings;
import stroom.query.TablePayload;
import stroom.query.shared.CoprocessorSettings;
import stroom.query.shared.TableSettings;
import stroom.security.SecurityContext;
import stroom.statistics.common.*;
import stroom.statistics.common.rollup.RollUpBitMask;
import stroom.statistics.server.common.AbstractStatistics;
import stroom.statistics.shared.EventStoreTimeIntervalEnum;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.statistics.shared.StatisticType;
import stroom.statistics.sql.SQLStatisticConstants;
import stroom.statistics.sql.SQLStatisticNames;
import stroom.statistics.sql.SQLTagValueWhereClauseConverter;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskMonitor;

import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.Map.Entry;

@TaskHandlerBean(task = StatStoreSearchTask.class)
@Scope(value = StroomScope.TASK)
public class StatStoreSearchTaskHandler extends AbstractTaskHandler<StatStoreSearchTask, VoidResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatStoreSearchTaskHandler.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(StatStoreSearchTaskHandler.class);

    static final String PROP_KEY_SQL_SEARCH_MAX_RESULTS = "stroom.statistics.sql.search.maxResults";

    private static final Map<String, List<String>> fieldToColumnsMap = ImmutableMap.<String, List<String>>builder()
            .put(StatisticStoreEntity.FIELD_NAME_DATE_TIME, Arrays.asList(SQLStatisticNames.TIME_MS))
            .put(StatisticStoreEntity.FIELD_NAME_PRECISION_MS, Arrays.asList(SQLStatisticNames.PRECISION))
            .put(StatisticStoreEntity.FIELD_NAME_PRECISION, Arrays.asList(SQLStatisticNames.PRECISION))
            .put(StatisticStoreEntity.FIELD_NAME_COUNT, Arrays.asList(SQLStatisticNames.COUNT))
            .put(StatisticStoreEntity.FIELD_NAME_VALUE,
                    Arrays.asList(SQLStatisticNames.COUNT, SQLStatisticNames.VALUE))
            .build();


    private static final String STAT_QUERY_SKELETON =
            "SELECT " +
            "K." + SQLStatisticNames.NAME + ", " +
            "V." + SQLStatisticNames.PRECISION + ", " +
            "V." + SQLStatisticNames.TIME_MS + ", " +
            "V." + SQLStatisticNames.VALUE_TYPE + ", " +
            "V." + SQLStatisticNames.VALUE + ", " +
            "V." + SQLStatisticNames.COUNT + " ";

    private static final String STAT_QUERY_FROM_AND_WHERE_PARTS =
            "FROM " + SQLStatisticNames.SQL_STATISTIC_KEY_TABLE_NAME + " K " +
            "JOIN " + SQLStatisticNames.SQL_STATISTIC_VALUE_TABLE_NAME + " V " +
            "ON (K." + SQLStatisticNames.ID + " = V." + SQLStatisticNames.SQL_STATISTIC_KEY_FOREIGN_KEY + ") " +
            "WHERE K." + SQLStatisticNames.NAME + " LIKE ? " +
            "AND K." + SQLStatisticNames.NAME + " REGEXP ? " +
            "AND V." + SQLStatisticNames.TIME_MS + " >= ? " +
            "AND V." + SQLStatisticNames.TIME_MS + " < ?";


    private final TaskMonitor taskMonitor;
    private final StatisticsFactory statisticsFactory;
    private final SecurityContext securityContext;
    private final DataSource statisticsDataSource;
    private final StroomPropertyService propertyService;

    @Inject
    StatStoreSearchTaskHandler(final TaskMonitor taskMonitor,
                               final StatisticsFactory statisticsFactory,
                               @Named("statisticsDataSource") final DataSource statisticsDataSource,
                               final StroomPropertyService propertyService,
                               final SecurityContext securityContext) {
        this.taskMonitor = taskMonitor;
        this.statisticsFactory = statisticsFactory;
        this.statisticsDataSource = statisticsDataSource;
        this.propertyService = propertyService;
        this.securityContext = securityContext;
    }

    @Override
    public VoidResult exec(final StatStoreSearchTask task) {
        try {
            securityContext.elevatePermissions();

            final StatStoreSearchResultCollector resultCollector = task.getResultCollector();

            if (!task.isTerminated()) {
                taskMonitor.info(task.getSearchName() + " - initialising");

                final StatisticStoreEntity entity = task.getEntity();

                Preconditions.checkNotNull(entity);

                // Get the statistic store service class based on the engine of the
                // datasource being searched
                final Statistics statisticEventStore;
                statisticEventStore = statisticsFactory.instance(entity.getEngineName());

                final StatisticDataSet statisticDataSet;
                if (statisticEventStore instanceof AbstractStatistics) {
                    statisticDataSet = ((AbstractStatistics) statisticEventStore)
                            .searchStatisticsData(task.getSearch(), entity);
                } else {
                    throw new RuntimeException(String.format("Unable to cast %s to %s for engineName %s",
                            statisticEventStore.getClass().getName(),
                            AbstractStatistics.class.getName(),
                            entity.getEngineName()));
                }

                // Produce payloads for each coprocessor.
                Map<Integer, Payload> payloadMap = null;

                final FieldIndexMap fieldIndexMap = new FieldIndexMap(true);
                for (final Entry<Integer, CoprocessorSettings> entry : task.getCoprocessorMap().entrySet()) {
                    final TableSettings tableSettings = ((TableCoprocessorSettings) entry.getValue()).getTableSettings();
                    final CompiledDepths compiledDepths = new CompiledDepths(tableSettings.getFields(),
                            tableSettings.showDetail());
                    final CompiledFields compiledFields = new CompiledFields(tableSettings.getFields(),
                            fieldIndexMap, task.getSearch().getParamMap());

                    // Create a queue of string arrays.
                    final PairQueue<String, Item> queue = new BlockingPairQueue<>(taskMonitor);
                    final ItemMapper mapper = new ItemMapper(queue, compiledFields, compiledDepths.getMaxDepth(),
                            compiledDepths.getMaxGroupDepth());

                    performSearch(entity, mapper, statisticDataSet, fieldIndexMap);

                    // partition and reduce based on table settings.
                    final UnsafePairQueue<String, Item> outputQueue = new UnsafePairQueue<>();

                    // Create a partitioner to perform result reduction if needed.
                    final ItemPartitioner partitioner = new ItemPartitioner(compiledDepths.getDepths(),
                            compiledDepths.getMaxDepth());
                    partitioner.setOutputCollector(outputQueue);

                    // Partition the data prior to forwarding to the target node.
                    partitioner.read(queue);

                    // Perform partitioning.
                    partitioner.partition();

                    final Payload payload = new TablePayload(outputQueue);
                    if (payloadMap == null) {
                        payloadMap = new HashMap<>();
                    }
                    payloadMap.put(entry.getKey(), payload);
                }

                resultCollector.handle(payloadMap);
            }

            // Let the result handler know search has finished.
            resultCollector.getResultHandler().setComplete(true);

            return VoidResult.INSTANCE;

        } finally {
            securityContext.restorePermissions();
        }
    }

    private void performSearch(final StatisticStoreEntity dataSource,
                               final ItemMapper mapper,
                               final StatisticDataSet statisticDataSet,
                               final FieldIndexMap fieldIndexMap) {
        final List<String> tagsForStatistic = dataSource.getFieldNames();

        final int[] indexes = new int[7 + tagsForStatistic.size()];
        int i = 0;

        indexes[i++] = fieldIndexMap.get(StatisticStoreEntity.FIELD_NAME_DATE_TIME);
        indexes[i++] = fieldIndexMap.get(StatisticStoreEntity.FIELD_NAME_COUNT);
        indexes[i++] = fieldIndexMap.get(StatisticStoreEntity.FIELD_NAME_VALUE);
        indexes[i++] = fieldIndexMap.get(StatisticStoreEntity.FIELD_NAME_MIN_VALUE);
        indexes[i++] = fieldIndexMap.get(StatisticStoreEntity.FIELD_NAME_MAX_VALUE);
        indexes[i++] = fieldIndexMap.get(StatisticStoreEntity.FIELD_NAME_PRECISION);
        indexes[i++] = fieldIndexMap.get(StatisticStoreEntity.FIELD_NAME_PRECISION_MS);

        for (final String tag : tagsForStatistic) {
            indexes[i++] = fieldIndexMap.get(tag);
        }

        for (final StatisticDataPoint dataPoint : statisticDataSet) {
            final Map<String, String> tagMap = dataPoint.getTagsAsMap();

            final long precisionMs = dataPoint.getPrecisionMs();

            final EventStoreTimeIntervalEnum interval = EventStoreTimeIntervalEnum.fromColumnInterval(precisionMs);
            String precisionText;
            if (interval != null) {
                precisionText = interval.longName();
            } else {
                // could be a precision that doesn't match one of our interval
                // sizes
                precisionText = "-";
            }

            final String[] data = new String[fieldIndexMap.size()];
            i = 0;

            if (indexes[i] != -1) {
                data[indexes[i]] = DateUtil.createNormalDateTimeString(dataPoint.getTimeMs());
            }
            i++;

            if (indexes[i] != -1) {
                data[indexes[i]] = String.valueOf(dataPoint.getCount());
            }
            i++;

            if (indexes[i] != -1) {
                data[indexes[i]] = String.valueOf(dataPoint.getValue());
            }
            i++;

            if (indexes[i] != -1) {
                data[indexes[i]] = String.valueOf(dataPoint.getMinValue());
            }
            i++;

            if (indexes[i] != -1) {
                data[indexes[i]] = String.valueOf(dataPoint.getMaxValue());
            }
            i++;

            if (indexes[i] != -1) {
                data[indexes[i]] = precisionText;
            }
            i++;

            if (indexes[i] != -1) {
                data[indexes[i]] = Long.toString(precisionMs);
            }
            i++;

            for (final String tag : tagsForStatistic) {
                if (indexes[i] != -1) {
                    data[indexes[i]] = tagMap.get(tag);
                }
                i++;
            }

            mapper.collect(null, data);
        }
    }

    private StatisticDataSet performStatisticQuery(final StatisticStoreEntity dataSource,
                                                   final FindEventCriteria criteria) {
        final Set<StatisticDataPoint> dataPoints = new HashSet<StatisticDataPoint>();

        // TODO need to fingure out how we get the precision
        final StatisticDataSet statisticDataSet = new StatisticDataSet(dataSource.getName(),
                dataSource.getStatisticType(), 1000L, dataPoints);

        try (final Connection connection = statisticsDataSource.getConnection()) {
            try (final PreparedStatement preparedStatement = buildSearchPreparedStatement(dataSource, criteria, connection)) {
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
                            statisticDataPoint = StatisticDataPoint.countInstance(timeMs, precisionMs, statisticTags,
                                    resultSet.getLong(SQLStatisticNames.COUNT));
                        } else {
                            final double aggregatedValue = resultSet.getDouble(SQLStatisticNames.VALUE);
                            final long count = resultSet.getLong(SQLStatisticNames.COUNT);

                            // the aggregateValue is sum of all values against that
                            // key/time. We therefore need to get the
                            // average using the count column
                            final double averagedValue = count != 0 ? (aggregatedValue / count) : 0;

                            // min/max are not supported by SQL stats so use -1
                            statisticDataPoint = StatisticDataPoint.valueInstance(timeMs, precisionMs, statisticTags,
                                    averagedValue, count, -1, -1);
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
     * Will map the current row of resultSet to an array of string values
     *
     * @param resultSet
     * @return
     */
    private String[] mapResultSetRow(final ResultSet resultSet) {

    }

    /**
     * @param columnValue The value from the STAT_KEY.NAME column which could be of the
     *                    form 'StatName' or 'StatName¬Tag1¬Tag1Val1¬Tag2¬Tag2Val1'
     * @return A list of {@link StatisticTag} objects built from the tag/value
     * token pairs in the string or an empty list if there are none.
     */
    private List<StatisticTag> extractStatisticTagsFromColumn(final String columnValue) {
        final String[] tokens = columnValue.split(SQLStatisticConstants.NAME_SEPARATOR);
        final List<StatisticTag> statisticTags = new ArrayList<StatisticTag>();

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
                                                           final FindEventCriteria criteria,
                                                           final FieldIndexMap fieldIndexMap,
                                                           final Connection connection) throws SQLException {
        final RollUpBitMask rollUpBitMask = AbstractStatistics.buildRollUpBitMaskFromCriteria(criteria, dataSource);

        final String statNameWithMask = dataSource.getName() + rollUpBitMask.asHexString();

        final List<String> bindVariables = new ArrayList<>();

        SqlBuilder sql = new SqlBuilder();
        sql.append("SELECT ");

        //determine which columns we want to come back as an optimisation
        //to reduce the data being process that will not be used in the query results
        fieldToColumnsMap.entrySet().stream()
                .flatMap(entry ->
                        entry.getValue().stream()
                        .map(colName -> getOptFieldIndexPosition(fieldIndexMap, entry.getKey())
                                .map(val -> colName))
                        .filter(Optional::isPresent)
                        .map(Optional::get))
                .distinct()
                .forEach(sql::append);



        final String whereClause = SQLTagValueWhereClauseConverter
                .buildTagValueWhereClause(criteria.getFilterTermsTree(), bindVariables);

        if (whereClause != null && whereClause.length() != 0) {
            sqlQuery += " AND " + whereClause;
        }

        final int maxResults = propertyService.getIntProperty(PROP_KEY_SQL_SEARCH_MAX_RESULTS, 100000);
        sqlQuery += " LIMIT " + maxResults;

        LOGGER.debug("Search query: %s", sqlQuery);

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

        for (final String bindVariable : bindVariables) {
            ps.setString(position++, bindVariable);
        }

        return ps;
    }

    private Optional<Integer> getOptFieldIndexPosition(final FieldIndexMap fieldIndexMap, final String fieldName) {
       return Optional.of(fieldIndexMap.get(fieldName));
    }

    private void addSelectColIfRequired(final SqlBuilder sql,
                                        final FieldIndexMap fieldIndexMap,
                                        final String fieldName) {
        getOptFieldIndexPosition(fieldIndexMap, fieldName)
                .ifPresent(idx -> sql.append(fieldName));
    }

    /**
     * Will map the current row of resultSet to an array of string values
     * @param resultSet
     * @return
     */
    private String[] mapResultSetRow(final ResultSet resultSet) {

    }
}
