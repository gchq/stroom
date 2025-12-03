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

package stroom.statistics.impl.sql.search;

import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValDuration;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.statistics.impl.sql.PreparedStatementUtil;
import stroom.statistics.impl.sql.SQLStatisticConstants;
import stroom.statistics.impl.sql.SQLStatisticNames;
import stroom.statistics.impl.sql.SQLStatisticsDbConnProvider;
import stroom.statistics.impl.sql.SqlBuilder;
import stroom.statistics.impl.sql.rollup.RollUpBitMask;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticType;
import stroom.task.api.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import com.google.common.base.Preconditions;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
        //called by DI
//TODO rename to StatisticsDatabaseSearchServiceImpl
class StatisticsSearchServiceImpl implements StatisticsSearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsSearchServiceImpl.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(StatisticsSearchServiceImpl.class);

    private static final String KEY_TABLE_ALIAS = "K";
    private static final String VALUE_TABLE_ALIAS = "V";
    private static final String ALIASED_TIME_MS_COL = VALUE_TABLE_ALIAS + "." + SQLStatisticNames.TIME_MS;
    private static final String ALIASED_PRECISION_COL = VALUE_TABLE_ALIAS + "." + SQLStatisticNames.PRECISION;
    private static final String ALIASED_COUNT_COL = VALUE_TABLE_ALIAS + "." + SQLStatisticNames.COUNT;
    private static final String ALIASED_VALUE_COL = VALUE_TABLE_ALIAS + "." + SQLStatisticNames.VALUE;
    private static final Map<String, List<String>> COMMON_STATIC_FIELDS_TO_COLUMNS_MAP = Map.of(
            StatisticStoreDoc.FIELD_NAME_DATE_TIME, Collections.singletonList(ALIASED_TIME_MS_COL),
            StatisticStoreDoc.FIELD_NAME_PRECISION_MS, Collections.singletonList(ALIASED_PRECISION_COL),
            StatisticStoreDoc.FIELD_NAME_COUNT, Collections.singletonList(ALIASED_COUNT_COL)
    );
    // VALUE stat only cols
    private static final Map<String, List<String>> VALUE_STAT_STATIC_FIELDS_TO_COLUMNS_MAP = Map.of(
            StatisticStoreDoc.FIELD_NAME_VALUE, Arrays.asList(ALIASED_COUNT_COL, ALIASED_VALUE_COL)
    );

    //defines how the entity fields relate to the table columns
    private final SQLStatisticsDbConnProvider sqlStatisticsDbConnProvider;
    private final SearchConfig searchConfig;

    @SuppressWarnings("unused") // Called by DI
    @Inject
    StatisticsSearchServiceImpl(final SQLStatisticsDbConnProvider sqlStatisticsDbConnProvider,
                                final SearchConfig searchConfig) {
        this.sqlStatisticsDbConnProvider = sqlStatisticsDbConnProvider;
        this.searchConfig = searchConfig;
    }

    /**
     * TODO: This is a bit simplistic as a user could create a filter that said
     * user=user1 AND user='*' which makes no sense. At the moment we would
     * assume that the user tag is being rolled up so user=user1 would never be
     * found in the data and thus would return no data.
     */
    private static RollUpBitMask buildRollUpBitMaskFromCriteria(final FindEventCriteria criteria,
                                                                final StatisticStoreDoc statisticsDataSource) {
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

    @Override
    public void search(final TaskContext taskContext,
                       final StatisticStoreDoc statisticStoreEntity,
                       final FindEventCriteria criteria,
                       final FieldIndex fieldIndex,
                       final ValuesConsumer valuesConsumer,
                       final ErrorConsumer errorConsumer) {
        try {
            final List<String> selectCols = getSelectColumns(statisticStoreEntity, fieldIndex);
            final Optional<SqlBuilder> optSql = buildSql(statisticStoreEntity, criteria, fieldIndex);

            // If there is nothing to select then there is nothing to pass to the value consumer
            optSql.ifPresent(sql -> {
                // build a mapper function to convert a resultSet row into a String[] based on the fields
                // required by all coprocessors
                final Function<ResultSet, Val[]> resultSetMapper = buildResultSetMapper(
                        fieldIndex, statisticStoreEntity);

                // the query will not be executed until somebody subscribes to the flowable
                getFlowableQueryResults(taskContext, sql, resultSetMapper, valuesConsumer);
            });
        } catch (final RuntimeException e) {
            errorConsumer.add(e);
        }
    }

    private List<String> getSelectColumns(final StatisticStoreDoc statisticStoreEntity,
                                          final FieldIndex fieldIndex) {
        //assemble a map of how fields map to 1-* select cols

        if (fieldIndex == null || fieldIndex.size() == 0) {
            // This is a slight fudge to allow a dash query with a table that only has custom cols
            // tha involve no fields, e.g. one col of 'add(1,2)'. So we just return a col of nulls.
            return Collections.singletonList("null");
        } else {
            //get all the static field mappings
            final Map<String, List<String>> fieldToColumnsMap = new HashMap<>(COMMON_STATIC_FIELDS_TO_COLUMNS_MAP);

            if (StatisticType.VALUE.equals(statisticStoreEntity.getStatisticType())) {
                fieldToColumnsMap.putAll(VALUE_STAT_STATIC_FIELDS_TO_COLUMNS_MAP);
            }

            //now add in all the dynamic tag field mappings
            statisticStoreEntity.getFieldNames().forEach(tagField ->
                    fieldToColumnsMap.computeIfAbsent(tagField, k -> new ArrayList<>())
                            .add(KEY_TABLE_ALIAS + "." + SQLStatisticNames.NAME));

            //now map the fields in use to a distinct list of columns
            return fieldToColumnsMap.entrySet().stream()
                    .flatMap(entry ->
                            entry.getValue().stream()
                                    .map(colName ->
                                            getOptFieldIndexPosition(fieldIndex, entry.getKey())
                                                    .map(val -> colName))
                                    .filter(Optional::isPresent)
                                    .map(Optional::get))
                    .distinct()
                    .collect(Collectors.toList());
        }
    }

    /**
     * Construct the sql select for the query's criteria
     * <p>
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
    private Optional<SqlBuilder> buildSql(final StatisticStoreDoc statisticStoreEntity,
                                          final FindEventCriteria criteria,
                                          final FieldIndex fieldIndex) {
        final RollUpBitMask rollUpBitMask = buildRollUpBitMaskFromCriteria(criteria, statisticStoreEntity);

        final String statNameWithMask = statisticStoreEntity.getName() + rollUpBitMask.asHexString();

        final SqlBuilder sql = new SqlBuilder();
        sql.append("SELECT ");

        final String selectColsStr = String.join(", ", getSelectColumns(statisticStoreEntity, fieldIndex));
        if (NullSafe.isNonBlankString(selectColsStr)) {
            sql.append(selectColsStr);

            // join to key table
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

            // exact match on the stat name bit of the key
            sql.append(" AND K." + SQLStatisticNames.NAME + " REGEXP ");
            sql.arg("^" + statNameWithMask + "(" + SQLStatisticConstants.NAME_SEPARATOR + "|$)");

            // add the time bounds
            sql.append(" AND V." + SQLStatisticNames.TIME_MS + " >= ");
            sql.arg(criteria.getPeriod().getFromMs());
            sql.append(" AND V." + SQLStatisticNames.TIME_MS + " < ");
            sql.arg(criteria.getPeriod().getToMs());

            // now add the query terms
            SQLTagValueWhereClauseConverter.buildTagValueWhereClause(criteria.getFilterTermsTree(), sql);

            final int maxResults = searchConfig.getMaxResults();
            sql.append(" LIMIT " + maxResults);

            LOGGER.debug("Search query: {}", sql);

            return Optional.of(sql);
        } else {
            LOGGER.debug("No fields to select");
            return Optional.empty();
        }
    }

    private Optional<Integer> getOptFieldIndexPosition(final FieldIndex fieldIndex, final String fieldName) {
        final Integer idx = fieldIndex.getPos(fieldName);
        return Optional.ofNullable(idx);
    }

    /**
     * Build a mapper function that will only extract the columns of interest from the resultSet row.
     * Assumes something external to the returned function will advance the resultSet
     */
    private Function<ResultSet, Val[]> buildResultSetMapper(
            final FieldIndex fieldIndex,
            final StatisticStoreDoc statisticStoreEntity) {

        LAMBDA_LOGGER.debug(() -> String.format("Building mapper for fieldIndexMap %s, entity %s",
                fieldIndex, statisticStoreEntity.getUuid()));

        // construct a list of field extractors that can populate the appropriate bit of the data arr
        // when given a resultSet row
        final List<ValueExtractor> valueExtractors = fieldIndex.stream()
                .map(entry -> {
                    final int idx = entry.getValue();
                    final String fieldName = entry.getKey();
                    final ValueExtractor extractor;
                    if (fieldName.equals(StatisticStoreDoc.FIELD_NAME_DATE_TIME)) {
                        extractor = buildDateValueExtractor(SQLStatisticNames.TIME_MS, idx);
                    } else if (fieldName.equals(StatisticStoreDoc.FIELD_NAME_COUNT)) {
                        extractor = buildLongValueExtractor(SQLStatisticNames.COUNT, idx);
                    } else if (fieldName.equals(StatisticStoreDoc.FIELD_NAME_PRECISION_MS)) {
                        extractor = buildPrecisionMsExtractor(idx);
                    } else if (fieldName.equals(StatisticStoreDoc.FIELD_NAME_VALUE)) {
                        final StatisticType statisticType = statisticStoreEntity.getStatisticType();
                        if (statisticType.equals(StatisticType.COUNT)) {
                            extractor = buildLongValueExtractor(SQLStatisticNames.COUNT, idx);
                        } else if (statisticType.equals(StatisticType.VALUE)) {
                            // value stat
                            extractor = buildStatValueExtractor(idx);
                        } else {
                            throw new RuntimeException(String.format("Unexpected type %s", statisticType));
                        }
                    } else if (statisticStoreEntity.getFieldNames().contains(fieldName)) {
                        // this is a tag field so need to extract the tags/values from the NAME col.
                        // We only want to do this extraction once so we cache the values
                        extractor = buildTagFieldValueExtractor(fieldName, idx);
                    } else {
                        extractor = null;
//                        throw new RuntimeException(String.format("Unexpected fieldName %s", fieldName));
                    }
                    LAMBDA_LOGGER.debug(() ->
                            String.format("Adding extraction function for field %s, idx %s", fieldName, idx));
                    return extractor;
                })
                .toList();

        final int arrSize = valueExtractors.size();

        //the mapping function that will be used on each row in the resultSet, that makes use of the ValueExtractors
        //created above
        return rs -> {
            Preconditions.checkNotNull(rs);
            try {
                if (rs.isClosed()) {
                    throw new RuntimeException("ResultSet is closed");
                }
            } catch (final SQLException e) {
                throw new RuntimeException("Error testing closed state of resultSet", e);
            }
            //the data array we are populating
            final Val[] data = new Val[arrSize];
            //state to hold while mapping this row, used to save parsing the NAME col multiple times
            final Map<String, Val> fieldValueCache = new HashMap<>();

            //run each of our field value extractors against the resultSet to fill up the data arr
            valueExtractors.forEach(valueExtractor -> {
                if (valueExtractor != null) {
                    valueExtractor.extract(rs, data, fieldValueCache);
                }
            });

            LAMBDA_LOGGER.trace(() -> {
                try {
                    return String.format("Mapped resultSet row %s to %s", rs.getRow(), Arrays.toString(data));
                } catch (final SQLException e) {
                    throw new RuntimeException(String.format("Error getting current row number: %s", e.getMessage()),
                            e);
                }
            });
            return Val.of(data);
        };
    }

    private ValueExtractor buildPrecisionMsExtractor(final int idx) {
        final ValueExtractor extractor;
        extractor = (rs, arr, cache) -> {
            // the precision in the table represents the number of zeros
            // of millisecond precision, e.g.
            // 6=1,000,000ms
            final long precisionMs;
            try {
                precisionMs = (long) Math.pow(10, rs.getInt(SQLStatisticNames.PRECISION));
            } catch (final SQLException e) {
                throw new RuntimeException("Error extracting precision field", e);
            }
            arr[idx] = ValDuration.create(precisionMs);
        };
        return extractor;
    }

    private ValueExtractor buildStatValueExtractor(final int idx) {
        final ValueExtractor extractor;
        extractor = (rs, arr, cache) -> {

            final double aggregatedValue;
            final long count;
            try {
                aggregatedValue = rs.getDouble(SQLStatisticNames.VALUE);
                count = rs.getLong(SQLStatisticNames.COUNT);
            } catch (final SQLException e) {
                throw new RuntimeException("Error extracting count and value fields", e);
            }

            // the aggregateValue is sum of all values against that
            // key/time. We therefore need to get the
            // average using the count column
            final double averagedValue = count != 0
                    ? (aggregatedValue / count)
                    : 0;

            arr[idx] = ValDouble.create(averagedValue);
        };
        return extractor;
    }

    private ValueExtractor buildLongValueExtractor(final String columnName, final int fieldIndex) {
        return (rs, arr, cache) ->
                arr[fieldIndex] = getResultSetLong(rs, columnName);
    }

    private ValueExtractor buildDateValueExtractor(final String columnName, final int fieldIndex) {
        return (rs, arr, cache) ->
                arr[fieldIndex] = getResultSetDateMs(rs, columnName);
    }

    private ValueExtractor buildTagFieldValueExtractor(final String fieldName, final int fieldIndex) {
        return (rs, arr, cache) -> {
            Val value = cache.get(fieldName);
            if (value == null) {
                //populate our cache of
                cache.putAll(extractTagsMapFromColumn(getResultSetString(rs, SQLStatisticNames.NAME)));
            }
            value = cache.get(fieldName);
            arr[fieldIndex] = value;
        };
    }

    private Val getResultSetLong(final ResultSet resultSet, final String column) {
        try {
            return ValLong.create(resultSet.getLong(column));
        } catch (final SQLException e) {
            throw new RuntimeException(String.format("Error extracting field %s", column), e);
        }
    }

    private Val getResultSetDateMs(final ResultSet resultSet, final String column) {
        try {
            return ValDate.create(resultSet.getLong(column));
        } catch (final SQLException e) {
            throw new RuntimeException(String.format("Error extracting field %s", column), e);
        }
    }

    private Val getResultSetString(final ResultSet resultSet, final String column) {
        try {
            return ValString.create(resultSet.getString(column));
        } catch (final SQLException e) {
            throw new RuntimeException(String.format("Error extracting field %s", column), e);
        }
    }

    private void getFlowableQueryResults(final TaskContext taskContext,
                                         final SqlBuilder sql,
                                         final Function<ResultSet, Val[]> resultSetMapper,
                                         final ValuesConsumer valuesConsumer) {
        long count = 0;

        try (final Connection connection = sqlStatisticsDbConnProvider.getConnection()) {
            //settings ot prevent mysql from reading the whole resultset into memory
            //see https://github.com/ontop/ontop/wiki/WorkingWithMySQL
            //Also needs 'useCursorFetch=true' on the jdbc connect string
            try (final PreparedStatement preparedStatement = connection.prepareStatement(
                    sql.toString(),
                    ResultSet.TYPE_FORWARD_ONLY,
                    ResultSet.CONCUR_READ_ONLY,
                    ResultSet.CLOSE_CURSORS_AT_COMMIT)) {

                final int fetchSize = searchConfig.getFetchSize();
                LOGGER.debug("Setting fetch size to {}", fetchSize);
                preparedStatement.setFetchSize(fetchSize);

                PreparedStatementUtil.setArguments(preparedStatement, sql.getArgs());
                LAMBDA_LOGGER.debug(() -> String.format("Created preparedStatement %s", preparedStatement));

                final String message = String.format("Executing query %s", sql);
                taskContext.info(() -> message);
                LAMBDA_LOGGER.debug(() -> message);

                try (final ResultSet resultSet = preparedStatement.executeQuery()) {

                    // TODO prob needs to change in 6.1
                    while (resultSet.next() &&
                           !Thread.currentThread().isInterrupted()) {
                        LOGGER.trace("Adding result");
                        final Val[] values = resultSetMapper.apply(resultSet);
                        valuesConsumer.accept(values);
                        count++;
                    }

                    if (Thread.currentThread().isInterrupted()) {
                        LOGGER.debug("Task is terminated/interrupted, calling onComplete");
                    } else {
                        LOGGER.debug("End of resultSet, calling onComplete");
                    }

                } catch (final SQLException e) {
                    throw new RuntimeException(String.format("Error executing query %s, %s",
                            preparedStatement, e.getMessage()), e);
                }

            } catch (final SQLException e) {
                throw new RuntimeException(String.format("Error preparing statement for sql [%s]", sql), e);
            }

        } catch (final SQLException e) {
            throw new RuntimeException("Error getting connection", e);
        }
    }

    /**
     * @param columnValue The value from the STAT_KEY.NAME column which could be of the
     *                    form 'StatName' or 'StatName¬Tag1¬Tag1Val1¬Tag2¬Tag2Val1'
     * @return A map of tag=>value, or an empty map if there are none
     */
    private Map<String, Val> extractTagsMapFromColumn(final Val columnValue) {
        final String[] tokens = columnValue.toString().split(SQLStatisticConstants.NAME_SEPARATOR);

        if (tokens.length == 1) {
            // no separators so there are no tags
            return Collections.emptyMap();
        } else if (tokens.length % 2 == 0) {
            throw new RuntimeException(
                    String.format("Expecting an odd number of tokens, columnValue: %s", columnValue));
        } else {
            final Map<String, Val> statisticTags = new HashMap<>();
            // stat name will be at pos 0 so start at 1
            for (int i = 1; i < tokens.length; i++) {
                final String tag = tokens[i++];
                final String value = tokens[i];
                if (value.equals(SQLStatisticConstants.NULL_VALUE_STRING)) {
                    statisticTags.put(tag, ValNull.INSTANCE);
                } else {
                    statisticTags.put(tag, ValString.create(value));
                }
            }
            return statisticTags;
        }
    }

    @FunctionalInterface
    private interface ValueExtractor {

        /**
         * Function for extracting values from a {@link ResultSet} and placing them into the passed String[]
         *
         * @param resultSet       The {@link ResultSet} instance to extract data from. It is assumed
         *                        the {@link ResultSet}
         *                        has already been positioned at the desired row. next() should not be called on the
         *                        resultSet.
         * @param data            The data array to populate
         * @param fieldValueCache A map of fieldName=>fieldValue that can be used to hold state while
         *                        processing a row
         */
        void extract(final ResultSet resultSet,
                     final Val[] data,
                     final Map<String, Val> fieldValueCache);
    }
}
