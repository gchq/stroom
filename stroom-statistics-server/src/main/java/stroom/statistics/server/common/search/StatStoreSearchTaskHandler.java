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
import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import stroom.dashboard.expression.FieldIndexMap;
import stroom.entity.server.util.PreparedStatementUtil;
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
import stroom.query.shared.TableSettings;
import stroom.security.SecurityContext;
import stroom.statistics.common.FindEventCriteria;
import stroom.statistics.common.StatisticDataPoint;
import stroom.statistics.common.StatisticDataSet;
import stroom.statistics.common.StatisticTag;
import stroom.statistics.common.Statistics;
import stroom.statistics.common.StatisticsFactory;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@TaskHandlerBean(task = StatStoreSearchTask.class)
@Scope(value = StroomScope.TASK)
public class StatStoreSearchTaskHandler extends AbstractTaskHandler<StatStoreSearchTask, VoidResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatStoreSearchTaskHandler.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(StatStoreSearchTaskHandler.class);

    static final String PROP_KEY_SQL_SEARCH_MAX_RESULTS = "stroom.statistics.sql.search.maxResults";

    //defines how the entity fields relate to the table columns
    private static final Map<String, List<String>> fieldToColumnsMap = ImmutableMap.<String, List<String>>builder()
            .put(StatisticStoreEntity.FIELD_NAME_DATE_TIME,
                    Collections.singletonList(SQLStatisticNames.TIME_MS))
            .put(StatisticStoreEntity.FIELD_NAME_PRECISION_MS,
                    Collections.singletonList(SQLStatisticNames.PRECISION))
            .put(StatisticStoreEntity.FIELD_NAME_PRECISION,
                    Collections.singletonList(SQLStatisticNames.PRECISION))
            .put(StatisticStoreEntity.FIELD_NAME_COUNT,
                    Collections.singletonList(SQLStatisticNames.COUNT))
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

//                final StatisticDataSet statisticDataSet;
//                if (statisticEventStore instanceof AbstractStatistics) {
//                    statisticDataSet = ((AbstractStatistics) statisticEventStore)
//                            .searchStatisticsData(task.getSearch(), entity);
//                } else {
//                    throw new RuntimeException(String.format("Unable to cast %s to %s for engineName %s",
//                            statisticEventStore.getClass().getName(),
//                            AbstractStatistics.class.getName(),
//                            entity.getEngineName()));
//                }

                // Produce payloads for each coprocessor.
                final Map<Integer, Payload> payloadMap = new HashMap<>();

                // convert the search into something stats understands
                FindEventCriteria criteria = StatStoreCriteriaBuilder.buildCriteria(
                        task.getSearch(),
                        entity);

                // build the sql from the criteria
                // TODO currently all cols are returned even if we don't need them as we have to handle the
                // needs of multiple coprocessors
                SqlBuilder sql = buildSql(entity, criteria);

                Flowable<ResultSet> flowableQueryResults = getFlowableQueryResults(sql);

                List<Consumer<String[]>> dataArrayConsumers = new ArrayList<>();

                //fieldIndexMap is common across all coprocessors as we will have a single String[] that will
                //be returned from the query and used by all coprocessors. The map is populated by the expression
                //parsing on each coprocessor
                final FieldIndexMap fieldIndexMap = new FieldIndexMap(true);

                //each coprocessor has its own settings and field requirements
                task.getCoprocessorMap().forEach((id, coprocessorSettings) -> {

                    Consumer<String[]> dataArrayConsumer = compileProcessorSettings(
                            task,
                            payloadMap,
                            fieldIndexMap,
                            id,
                            (TableCoprocessorSettings) coprocessorSettings);

                    dataArrayConsumers.add(dataArrayConsumer);
                });

                // build a mapper function to convert a resultSet row into a String[] based on the fields
                //required by all coprocessors
                Function<ResultSet, String[]> resultSetMapper = buildResultSetMapper(fieldIndexMap, entity);

                flowableQueryResults.blockingSubscribe(resultSet -> {
                    final String[] data = resultSetMapper.apply(resultSet);
                    LAMBDA_LOGGER.trace(() -> String.format("data: [%s]", Arrays.toString(data)));
                    dataArrayConsumers.forEach(resultSetConsumer -> resultSetConsumer.accept(data));
                });

                resultCollector.handle(payloadMap);
            }

            // Let the result handler know search has finished.
            resultCollector.getResultHandler().setComplete(true);

            return VoidResult.INSTANCE;

        } finally {
            securityContext.restorePermissions();
        }
    }

    private Consumer<String[]> compileProcessorSettings(
            final StatStoreSearchTask task,
            final Map<Integer, Payload> payloadMap,
            final FieldIndexMap fieldIndexMap,
            final Integer id,
            final TableCoprocessorSettings coprocessorSettings) {

        final TableSettings tableSettings = coprocessorSettings.getTableSettings();

        final CompiledDepths compiledDepths = new CompiledDepths(
                tableSettings.getFields(),
                tableSettings.showDetail());

        final CompiledFields compiledFields = new CompiledFields(
                tableSettings.getFields(),
                fieldIndexMap, task.getSearch().getParamMap());

        // Create a queue of string arrays.
        final PairQueue<String, Item> queue = new BlockingPairQueue<>(taskMonitor);
        final ItemMapper mapper = new ItemMapper(
                queue,
                compiledFields,
                compiledDepths.getMaxDepth(),
                compiledDepths.getMaxGroupDepth());

        //create a consumer of the data array that will ultimately be returned from the database query
        return (String[] data) -> {
            mapper.collect(null, data);

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
            payloadMap.put(id, payload);
        };
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

        /*
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
       */
        return statisticDataSet;
    }

    /**
     * @param columnValue The value from the STAT_KEY.NAME column which could be of the
     *                    form 'StatName' or 'StatName¬Tag1¬Tag1Val1¬Tag2¬Tag2Val1'
     * @return A list of {@link StatisticTag} objects built from the tag/value
     * token pairs in the string or an empty list if there are none.
     */
    private List<StatisticTag> extractTagsListFromColumn(final String columnValue) {
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

    /**
     * @param columnValue The value from the STAT_KEY.NAME column which could be of the
     *                    form 'StatName' or 'StatName¬Tag1¬Tag1Val1¬Tag2¬Tag2Val1'
     * @return A list of {@link StatisticTag} objects built from the tag/value
     * token pairs in the string or an empty list if there are none.
     */
    private Map<String, String> extractTagsMapFromColumn(final String columnValue) {
        final String[] tokens = columnValue.split(SQLStatisticConstants.NAME_SEPARATOR);
        final Map<String, String> statisticTags = new HashMap<>();

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
                statisticTags.put(tag, value);
            }
        }

        return statisticTags;
    }

    private SqlBuilder buildSql(final StatisticStoreEntity dataSource,
                                final FindEventCriteria criteria) {

        final RollUpBitMask rollUpBitMask = AbstractStatistics.buildRollUpBitMaskFromCriteria(criteria, dataSource);

        final String statNameWithMask = dataSource.getName() + rollUpBitMask.asHexString();

        SqlBuilder sql = new SqlBuilder();
        sql.append("SELECT");
        sql.append(" K." + SQLStatisticNames.NAME + ",");
        sql.append(" V." + SQLStatisticNames.PRECISION + ", ");
        sql.append(" V." + SQLStatisticNames.TIME_MS + ", ");
        sql.append(" V." + SQLStatisticNames.VALUE + ", ");
        sql.append(" V." + SQLStatisticNames.COUNT + " ");

        //determine which columns we want to come back as an optimisation
        //to reduce the data being process that will not be used in the query results
        //TODO if we use this optimisation then we can't rely on the fieldIndexMap
        //as each entry in the coprocessor map will have different field needs

//        final String selectCols = fieldToColumnsMap.entrySet().stream()
//                .flatMap(entry ->
//                        entry.getValue().stream()
//                                .map(colName ->
//                                        getOptFieldIndexPosition(fieldIndexMap, entry.getKey())
//                                                .map(val -> colName))
//                                .filter(Optional::isPresent)
//                                .map(Optional::get))
//                .distinct()
//                .map(col -> "V." + col)
//                .collect(Collectors.joining(","));
//        sql.append(selectCols);

        //join to key table
        sql.append(" FROM " + SQLStatisticNames.SQL_STATISTIC_KEY_TABLE_NAME + " K ");
        sql.join(SQLStatisticNames.SQL_STATISTIC_KEY_TABLE_NAME,
                "K",
                "K",
                SQLStatisticNames.ID,
                "V",
                SQLStatisticNames.SQL_STATISTIC_VALUE_FOREIGN_KEY);

        // do a like on the name first so we can hit the index before doing the slow regex matches
        sql.append(" WHERE K." + SQLStatisticNames.NAME + " LIKE ");
        sql.arg(statNameWithMask + "%");

        //exact match on the stat name bit of the key
        sql.append(" AND K." + SQLStatisticNames.NAME + " REGEXP ");
        sql.arg("^" + statNameWithMask + "(" + SQLStatisticConstants.NAME_SEPARATOR + "|$)");

        //add the time bounds
        sql.append(" AND V." + SQLStatisticNames.TIME_MS + " >= ");
        sql.arg(criteria.getPeriod().getFromMs());
        sql.append("AND V." + SQLStatisticNames.TIME_MS + " < ");
        sql.arg(criteria.getPeriod().getToMs());

        //now add the query terms
        SQLTagValueWhereClauseConverter.buildTagValueWhereClause(criteria.getFilterTermsTree(), sql);

        final int maxResults = propertyService.getIntProperty(PROP_KEY_SQL_SEARCH_MAX_RESULTS, 100000);
        sql.append(" LIMIT " + maxResults);

        LOGGER.debug("Search query: %s", sql.toString());

        return sql;
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
     * Build a mapper function that will only extarct the bits of the row
     * of interest.
     */
    private io.reactivex.functions.Function<ResultSet, String[]> buildResultSetMapper(
            final FieldIndexMap fieldIndexMap,
            final StatisticStoreEntity statisticStoreEntity) {

        final List<String> allFields = new ArrayList<>();
        allFields.add(StatisticStoreEntity.FIELD_NAME_DATE_TIME);
        allFields.add(StatisticStoreEntity.FIELD_NAME_COUNT);
        allFields.add(StatisticStoreEntity.FIELD_NAME_VALUE);
        allFields.add(StatisticStoreEntity.FIELD_NAME_PRECISION);
        allFields.add(StatisticStoreEntity.FIELD_NAME_PRECISION_MS);
        allFields.addAll(statisticStoreEntity.getFieldNames());

        //construct a list of field extractors that can populate the appropriate bit of the data arr
        //when given a resultSet row
        List<FieldExtractionFunctionWrapper> extractionWrappers = allFields.stream()
                .map(fieldName -> {
                    int idx = fieldIndexMap.get(fieldName);
                    if (idx == -1) {
                        return Optional.<FieldExtractionFunctionWrapper>empty();
                    } else {
                        ValueExtractor extractor;
                        if (fieldName.equals(StatisticStoreEntity.FIELD_NAME_DATE_TIME)) {
                            extractor = (rs, arr, cache) ->
                                    arr[idx] = getResultSetLong(rs, SQLStatisticNames.TIME_MS);
                        } else if (fieldName.equals(StatisticStoreEntity.FIELD_NAME_COUNT)) {
                            extractor = (rs, arr, cache) ->
                                    arr[idx] = getResultSetLong(rs, SQLStatisticNames.COUNT);
                        } else if (fieldName.equals(StatisticStoreEntity.FIELD_NAME_COUNT)) {
                            extractor = (rs, arr, cache) -> {

                                // the precision in the table represents the number of zeros
                                // of millisecond precision, e.g.
                                // 6=1,000,000ms
                                final long precisionMs;
                                try {
                                    precisionMs = (long) Math.pow(10, rs.getInt(SQLStatisticNames.PRECISION));
                                } catch (SQLException e) {
                                    throw new RuntimeException("Error extracting precision field", e);
                                }
                                arr[idx] = Long.toString(precisionMs);
                            };
                        } else if (fieldName.equals(StatisticStoreEntity.FIELD_NAME_VALUE)) {
                            if (statisticStoreEntity.getStatisticType().equals(StatisticType.COUNT)) {
                                extractor = (rs, arr, cache) ->
                                        arr[idx] = getResultSetLong(rs, SQLStatisticNames.COUNT);
                            } else {
                                extractor = (rs, arr, cache) -> {

                                    final double aggregatedValue;
                                    final long count;
                                    try {
                                        aggregatedValue = rs.getDouble(SQLStatisticNames.VALUE);
                                        count = rs.getLong(SQLStatisticNames.COUNT);
                                    } catch (SQLException e) {
                                        throw new RuntimeException("Error extracting count and value fields", e);
                                    }

                                    // the aggregateValue is sum of all values against that
                                    // key/time. We therefore need to get the
                                    // average using the count column
                                    final double averagedValue = count != 0 ? (aggregatedValue / count) : 0;

                                    arr[idx] = Double.toString(averagedValue);
                                };

                            }
                        } else if (statisticStoreEntity.getFieldNames().contains(fieldName)) {
                            //this is a tag field so need to extract the tags/values from the NAME col.
                            //We only want to do this extraction once so we cache the values

                            extractor = (rs, arr, cache) -> {
                                String value = cache.get(fieldName);
                                if (value == null) {
                                    //populate our cache of
                                    extractTagsMapFromColumn(getResultSetString(rs, SQLStatisticNames.NAME))
                                            .forEach(cache::put);
                                }
                                value = cache.get(fieldName);
                                if (value == null) {
                                    throw new RuntimeException("Value should never be null");
                                }
                                arr[idx] = value;
                            };
                        } else {
                            throw new RuntimeException(String.format("Unexpected fieldName %s", fieldName));
                        }
                        return Optional.of(new FieldExtractionFunctionWrapper(idx, fieldName, extractor));
                    }
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        final int arrSize = fieldIndexMap.size();

        //the mapping function that will be used on each row in the resultSet
        return rs -> {
            //the data array we are populating
            final String[] data = new String[arrSize];
            //state to hold while mapping this row
            final Map<String, String> fieldValueCache = new HashMap<>();

            //run each of our field extractors to fill up the data arr
            extractionWrappers.forEach(extractionWrapper ->
                    extractionWrapper.getExtractor().extract(rs, data, fieldValueCache));
            return data;
        };
    }


    private String getResultSetLong(final ResultSet resultSet, final String column) {
        try {
            return Long.toString(resultSet.getLong(column));
        } catch (SQLException e) {
            throw new RuntimeException(String.format("Error extracting field %s", column), e);
        }
    }

    private String getResultSetDouble(final ResultSet resultSet, final String column) {
        try {
            return Double.toString(resultSet.getDouble(column));
        } catch (SQLException e) {
            throw new RuntimeException(String.format("Error extracting field %s", column), e);
        }
    }

    private String getResultSetInt(final ResultSet resultSet, final String column) {
        try {
            return Integer.toString(resultSet.getInt(column));
        } catch (SQLException e) {
            throw new RuntimeException(String.format("Error extracting field %s", column), e);
        }
    }

    private String getResultSetString(final ResultSet resultSet, final String column) {
        try {
            return resultSet.getString(column);
        } catch (SQLException e) {
            throw new RuntimeException(String.format("Error extracting field %s", column), e);
        }
    }


    private interface ValueExtractor {
        void extract(final ResultSet resultSet, final String[] data, final Map<String, String> fieldValueCache);
    }

    private static class FieldExtractionFunctionWrapper {
        final int idx;
        final String fieldName;
        final StatStoreSearchTaskHandler.ValueExtractor extractor;

        public FieldExtractionFunctionWrapper(final int idx, final String fieldName, final ValueExtractor extractor) {
            this.idx = idx;
            this.fieldName = fieldName;
            this.extractor = extractor;
        }

        public int getIdx() {
            return idx;
        }

        public String getFieldName() {
            return fieldName;
        }

        public ValueExtractor getExtractor() {
            return extractor;
        }
    }

    private Flowable<ResultSet> getFlowableQueryResults(final SqlBuilder sql) {


        //Not thread safe as eachOnNext will get the same ResultSet instance, however its position
        //will have mode on each time.
        Flowable<ResultSet> resultSetFlowable = Flowable.using(
                PreparedStatementFactory::new,
                factory -> factory.create(statisticsDataSource, sql),
                PreparedStatementFactory::dispose)
                .flatMap(ps ->
                        Flowable.<ResultSet, ResultSet>generate(
                                ps::executeQuery,
                                (rs, emitter) -> {
                                    if (rs.next()) {
                                        emitter.onNext(rs);
                                    } else {
                                        emitter.onComplete();
                                    }
                                }));

        return resultSetFlowable;
    }

    public class PreparedStatementFactory {
        private Connection connection = null;
        private PreparedStatement preparedStatement = null;

        public Flowable<PreparedStatement> create(final DataSource dataSource, final SqlBuilder sql) {
            try {
                connection = dataSource.getConnection();
            } catch (SQLException e) {
                throw new RuntimeException("Error getting connection", e);
            }
            try {
                preparedStatement = connection.prepareStatement(sql.toString());
                preparedStatement.setFetchSize(200);

                PreparedStatementUtil.setArguments(preparedStatement, sql.getArgs());
                LAMBDA_LOGGER.debug(() -> String.format("Created preparedStatement %s", preparedStatement.toString()));
                return Flowable.just(preparedStatement);
            } catch (SQLException e) {
                throw new RuntimeException(String.format("Error preparing statement for sql [%s]", sql.toString()), e);
            }
        }

        public void dispose() {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new RuntimeException("Error closing preparedStatement", e);
                }
                preparedStatement = null;
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    throw new RuntimeException("Error closing connection", e);
                }
                connection = null;
            }
        }
    }
}
