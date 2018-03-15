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
import stroom.query.*;
import stroom.query.shared.TableSettings;
import stroom.security.SecurityContext;
import stroom.statistics.common.FindEventCriteria;
import stroom.statistics.common.StatisticTag;
import stroom.statistics.common.StatisticsFactory;
import stroom.statistics.common.rollup.RollUpBitMask;
import stroom.statistics.server.common.AbstractStatistics;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.statistics.shared.StatisticType;
import stroom.statistics.sql.SQLStatisticConstants;
import stroom.statistics.sql.SQLStatisticNames;
import stroom.statistics.sql.SQLTagValueWhereClauseConverter;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;

@SuppressWarnings("unused") // Instantiated by TaskManager
@TaskHandlerBean(task = StatStoreSearchTask.class)
@Scope(value = StroomScope.TASK)
public class StatStoreSearchTaskHandler extends AbstractTaskHandler<StatStoreSearchTask, VoidResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatStoreSearchTaskHandler.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(StatStoreSearchTaskHandler.class);

    private static final String PROP_KEY_SQL_SEARCH_MAX_RESULTS = "stroom.statistics.sql.search.maxResults";

    //defines how the entity fields relate to the table columns
//    private static final Map<String, List<String>> fieldToColumnsMap = ImmutableMap.<String, List<String>>builder()
//            .put(StatisticStoreEntity.FIELD_NAME_DATE_TIME,
//                    Collections.singletonList(SQLStatisticNames.TIME_MS))
//            .put(StatisticStoreEntity.FIELD_NAME_PRECISION_MS,
//                    Collections.singletonList(SQLStatisticNames.PRECISION))
//            .put(StatisticStoreEntity.FIELD_NAME_PRECISION,
//                    Collections.singletonList(SQLStatisticNames.PRECISION))
//            .put(StatisticStoreEntity.FIELD_NAME_COUNT,
//                    Collections.singletonList(SQLStatisticNames.COUNT))
//            .put(StatisticStoreEntity.FIELD_NAME_VALUE,
//                    Arrays.asList(SQLStatisticNames.COUNT, SQLStatisticNames.VALUE))
//            .build();


//    private static final String STAT_QUERY_SKELETON =
//            "SELECT " +
//                    "K." + SQLStatisticNames.NAME + ", " +
//                    "V." + SQLStatisticNames.PRECISION + ", " +
//                    "V." + SQLStatisticNames.TIME_MS + ", " +
//                    "V." + SQLStatisticNames.VALUE_TYPE + ", " +
//                    "V." + SQLStatisticNames.VALUE + ", " +
//                    "V." + SQLStatisticNames.COUNT + " ";
//
//    private static final String STAT_QUERY_FROM_AND_WHERE_PARTS =
//            "FROM " + SQLStatisticNames.SQL_STATISTIC_KEY_TABLE_NAME + " K " +
//                    "JOIN " + SQLStatisticNames.SQL_STATISTIC_VALUE_TABLE_NAME + " V " +
//                    "ON (K." + SQLStatisticNames.ID + " = V." + SQLStatisticNames.SQL_STATISTIC_KEY_FOREIGN_KEY + ") " +
//                    "WHERE K." + SQLStatisticNames.NAME + " LIKE ? " +
//                    "AND K." + SQLStatisticNames.NAME + " REGEXP ? " +
//                    "AND V." + SQLStatisticNames.TIME_MS + " >= ? " +
//                    "AND V." + SQLStatisticNames.TIME_MS + " < ?";


    private final TaskMonitor taskMonitor;
    private final StatisticsFactory statisticsFactory;
    private final SecurityContext securityContext;
    private final DataSource statisticsDataSource;
    private final StroomPropertyService propertyService;

    @SuppressWarnings("unused") // Called by the task manager
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

                // each coporcessor will have its own consumer of the String[]
                List<Consumer<String[]>> dataArrayConsumers = new ArrayList<>();

                //fieldIndexMap is common across all coprocessors as we will have a single String[] that will
                //be returned from the query and used by all coprocessors. The map is populated by the expression
                //parsing on each coprocessor
                final FieldIndexMap fieldIndexMap = new FieldIndexMap(true);

                //each coprocessor has its own settings and field requirements
                task.getCoprocessorMap().forEach((id, coprocessorSettings) -> {

                    //build a consumer that will accept a String[] and feed it into the
                    //item mapper for the coprocessor
                    Consumer<String[]> dataArrayConsumer = buildDataArrayConsumer(
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

                // subscribe to the flowable, mapping each resultSet to a String[]
                flowableQueryResults
                        .map(resultSetMapper) // convert the ResultSet into a String[]
                        .subscribe(
                                data -> {
                                    LAMBDA_LOGGER.trace(() -> String.format("data: [%s]", Arrays.toString(data)));
                                    // give the data array to each of our coprocessor consumers
                                    dataArrayConsumers.forEach(dataArrayConsumer ->
                                            dataArrayConsumer.accept(data));
                                },
                                throwable -> {
                                    throw new RuntimeException(String.format("Error in flow, %s",
                                            throwable.getMessage()), throwable);
                                },
                                () -> LOGGER.debug("onComplete called"));

                // give the processed results to the collector
                resultCollector.handle(payloadMap);
            }

            // Let the result handler know search has finished.
            resultCollector.getResultHandler().setComplete(true);

            return VoidResult.INSTANCE;

        } finally {
            securityContext.restorePermissions();
        }
    }

    private Consumer<String[]> buildDataArrayConsumer(
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

    /**
     * Construct the sql select for the query's criteria
     */
    private SqlBuilder buildSql(final StatisticStoreEntity dataSource,
                                final FindEventCriteria criteria) {
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

        final RollUpBitMask rollUpBitMask = AbstractStatistics.buildRollUpBitMaskFromCriteria(criteria, dataSource);

        final String statNameWithMask = dataSource.getName() + rollUpBitMask.asHexString();

        SqlBuilder sql = new SqlBuilder();
        sql.append("SELECT");
        sql.append(" K." + SQLStatisticNames.NAME + ",");
        sql.append(" V." + SQLStatisticNames.PRECISION + ",");
        sql.append(" V." + SQLStatisticNames.TIME_MS + ",");
        sql.append(" V." + SQLStatisticNames.VALUE + ",");
        sql.append(" V." + SQLStatisticNames.COUNT);

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
        sql.append(" FROM " + SQLStatisticNames.SQL_STATISTIC_KEY_TABLE_NAME + " K");
        sql.join(SQLStatisticNames.SQL_STATISTIC_VALUE_TABLE_NAME,
                "V",
                "K",
                SQLStatisticNames.ID,
                "V",
                SQLStatisticNames.SQL_STATISTIC_KEY_FOREIGN_KEY);

        // do a like on the name first so we can hit the index before doing the slow regex matches
        sql.append(" WHERE K." + SQLStatisticNames.NAME + " LIKE ");
        sql.arg(statNameWithMask + "%");

        //exact match on the stat name bit of the key
        sql.append(" AND K." + SQLStatisticNames.NAME + " REGEXP ");
        sql.arg("^" + statNameWithMask + "(" + SQLStatisticConstants.NAME_SEPARATOR + "|$)");

        //add the time bounds
        sql.append(" AND V." + SQLStatisticNames.TIME_MS + " >= ");
        sql.arg(criteria.getPeriod().getFromMs());
        sql.append(" AND V." + SQLStatisticNames.TIME_MS + " < ");
        sql.arg(criteria.getPeriod().getToMs());

        //now add the query terms
        SQLTagValueWhereClauseConverter.buildTagValueWhereClause(criteria.getFilterTermsTree(), sql);

        final int maxResults = propertyService.getIntProperty(PROP_KEY_SQL_SEARCH_MAX_RESULTS, 100000);
        sql.append(" LIMIT " + maxResults);

        LOGGER.debug("Search query: {}", sql.toString());

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

        LAMBDA_LOGGER.debug(() -> String.format("Building mapper for fieldIndexMap %s, entity %s",
                fieldIndexMap, statisticStoreEntity.getUuid()));

        final List<String> allFields = new ArrayList<>();
        allFields.add(StatisticStoreEntity.FIELD_NAME_DATE_TIME);
        allFields.add(StatisticStoreEntity.FIELD_NAME_COUNT);
        allFields.add(StatisticStoreEntity.FIELD_NAME_VALUE);
        allFields.add(StatisticStoreEntity.FIELD_NAME_PRECISION);
        allFields.add(StatisticStoreEntity.FIELD_NAME_PRECISION_MS);
        allFields.addAll(statisticStoreEntity.getFieldNames());

        //construct a list of field extractors that can populate the appropriate bit of the data arr
        //when given a resultSet row
        List<ValueExtractor> valueExtractors = allFields.stream()
                .map(fieldName -> {
                    int idx = fieldIndexMap.get(fieldName);
                    if (idx == -1) {
                        return Optional.<ValueExtractor>empty();
                    } else {
                        ValueExtractor extractor;
                        if (fieldName.equals(StatisticStoreEntity.FIELD_NAME_DATE_TIME)) {
                            extractor = (rs, arr, cache) ->
                                    arr[idx] = getResultSetLong(rs, SQLStatisticNames.TIME_MS);
                        } else if (fieldName.equals(StatisticStoreEntity.FIELD_NAME_COUNT)) {
                            extractor = (rs, arr, cache) ->
                                    arr[idx] = getResultSetLong(rs, SQLStatisticNames.COUNT);
                        } else if (fieldName.equals(StatisticStoreEntity.FIELD_NAME_PRECISION_MS)) {
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
                        LAMBDA_LOGGER.debug(() ->
                                String.format("Adding extraction function for field %s, idx %s",
                                        fieldName, idx));
                        return Optional.of(extractor);
                    }
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        final int arrSize = fieldIndexMap.size();

        //the mapping function that will be used on each row in the resultSet
        return rs -> {
            Preconditions.checkNotNull(rs);
            if (rs.isClosed()) {
                throw new RuntimeException("ResultSet is closed");
            }
            //the data array we are populating
            final String[] data = new String[arrSize];
            //state to hold while mapping this row
            final Map<String, String> fieldValueCache = new HashMap<>();

            //run each of our field extractors against the resultSet to fill up the data arr
            valueExtractors.forEach(valueExtractor ->
                    valueExtractor.extract(rs, data, fieldValueCache));

            LAMBDA_LOGGER.trace(() -> {
                try {
                    return String.format("Mapped resultSet row %s to %s", rs.getRow(), Arrays.toString(data));
                } catch (SQLException e) {
                    throw new RuntimeException(String.format("Error getting current row number: %s", e.getMessage()), e);
                }
            });
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

    private String getResultSetString(final ResultSet resultSet, final String column) {
        try {
            return resultSet.getString(column);
        } catch (SQLException e) {
            throw new RuntimeException(String.format("Error extracting field %s", column), e);
        }
    }

    @FunctionalInterface
    private interface ValueExtractor {
        /**
         * Function for extracting values from a {@link ResultSet} and placing them into the passed String[]
         * @param resultSet The {@link ResultSet} instance to extract data from. It is assumed the {@link ResultSet}
         *                  has already been positioned at the desired row. next() should not be called on the
         *                  resultSet.
         * @param data      The data array to populate
         * @param fieldValueCache   A map of fieldName=>fieldValue that can be used to hold state while
         *                          processing a row
         */
        void extract(final ResultSet resultSet,
                     final String[] data,
                     final Map<String, String> fieldValueCache);
    }

    private Flowable<ResultSet> getFlowableQueryResults(final SqlBuilder sql) {

        //Not thread safe as each onNext will get the same ResultSet instance, however its position
        //will have mode on each time.
        Flowable<ResultSet> resultSetFlowable = Flowable
                .using(
                        () -> new PreparedStatementResourceHolder(statisticsDataSource, sql),
                        factory -> {
                            LOGGER.debug("Converting factory to a flowable");
                            Preconditions.checkNotNull(factory);
                            PreparedStatement ps = factory.getPreparedStatement();
                            return Flowable.generate(
                                    () -> {
                                        LAMBDA_LOGGER.debug(() -> String.format("Executing query %s", ps.toString()));
                                        try {
                                            return ps.executeQuery();
                                        } catch (SQLException e) {
                                            throw new RuntimeException(String.format("Error executing query %s, %s",
                                                    ps.toString(), e.getMessage()), e);
                                        }
                                    },
                                    (rs, emitter) -> {
                                        //advance the resultSet, if it is a row emit it, else finish the flow
                                        if (rs.next()) {
                                            LOGGER.trace("calling onNext");
                                            emitter.onNext(rs);
                                        } else {
                                            LOGGER.debug("calling onComplete");
                                            emitter.onComplete();
                                        }
                                    });

                        },
                        PreparedStatementResourceHolder::dispose);

        LOGGER.debug("Returning flowable");
        return resultSetFlowable;
    }

    private class PreparedStatementResourceHolder {
        private Connection connection;
        private PreparedStatement preparedStatement;

        PreparedStatementResourceHolder(final DataSource dataSource, final SqlBuilder sql) {
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

            } catch (SQLException e) {
                throw new RuntimeException(String.format("Error preparing statement for sql [%s]", sql.toString()), e);
            }
        }

        PreparedStatement getPreparedStatement() {
            return preparedStatement;
        }

        void dispose() {
            if (preparedStatement != null) {
                try {
                    LOGGER.debug("Closing preparedStatement");
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new RuntimeException("Error closing preparedStatement", e);
                }
                preparedStatement = null;
            }
            if (connection != null) {
                try {
                    LOGGER.debug("Closing connection");
                    connection.close();
                } catch (SQLException e) {
                    throw new RuntimeException("Error closing connection", e);
                }
                connection = null;
            }
        }
    }
}
