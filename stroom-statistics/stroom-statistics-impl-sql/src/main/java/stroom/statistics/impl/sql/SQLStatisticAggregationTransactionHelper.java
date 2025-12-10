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

import stroom.statistics.impl.sql.shared.StatisticType;
import stroom.task.api.TaskContext;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Singleton
public class SQLStatisticAggregationTransactionHelper {

    public static final long NEWEST_SENSIBLE_STAT_AGE = DateUtil.parseNormalDateTimeString("9999-01-01T00:00:00.000Z");
    // The number of records to add to the aggregate from the aggregate source
    // table on each pass
    //
    // private static final int AGGREGATE_INCREMENT = 10000;
    public static final long MS_HOUR = ChronoUnit.HOURS.getDuration().toMillis();
    public static final long MS_DAY = ChronoUnit.DAYS.getDuration().toMillis();
    public static final long MS_MONTH = 31 * MS_DAY;

    public static final byte DEFAULT_PRECISION = 0;
    public static final byte MONTH_PRECISION = (byte) Math.floor(Math.log10(MS_MONTH)); // 9
    public static final byte DAY_PRECISION = (byte) Math.floor(Math.log10(MS_DAY)); // 7
    public static final byte HOUR_PRECISION = (byte) Math.floor(Math.log10(MS_HOUR)); // 6

    public static final String AGGREGATE_MAX_ID = "" +
            "SELECT MAX(" +
            SQLStatisticNames.ID +
            ") FROM "
            + SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME;

    public static final String AGGREGATE_MIN_ID = "" +
            "SELECT MIN(" +
            SQLStatisticNames.ID +
            ") FROM " +
            SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME;
    public static final String TRUNCATE_TABLE_SQL = "TRUNCATE TABLE ";
    public static final String CLEAR_TABLE_SQL = "DELETE FROM ";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(
            SQLStatisticAggregationTransactionHelper.class);

    private static final String AGGREGATE = "AGGREGATE";

    private static final String AGGREGATE_COUNT = condenseSql("" +
            "SELECT COUNT(*) " +
            "FROM SQL_STAT_VAL_SRC ");

    private static final String DELETE_OLD_STATS = condenseSql("" +
            "DELETE FROM SQL_STAT_VAL " +
            "WHERE PRES = ? " +
            "AND VAL_TP = ? " +
            "AND TIME_MS < ? ");

    private static final String DELETE_UNUSED_KEYS = condenseSql("" +
            "DELETE FROM SQL_STAT_KEY " +
            "WHERE NOT EXISTS ( " +
            "        SELECT null " +
            "        FROM SQL_STAT_VAL " +
            "        WHERE FK_SQL_STAT_KEY_ID = SQL_STAT_KEY.ID " +
            ")");

    // Find the highest ID out of the top n rows of the table. This ID or below then marks
    // the batch to work on. This means we always work on the oldest n rows which is fairer
    // and avoids conflict with the new rows being added to the top of the table.
    // TODO The max on a sub-select triggers a 'filesort' which may not be optimal.
    //   Other options are:
    //   select id from SQL_STAT_VAL_SRC order by id limit ?,1;  where ? is batch size - 1 but
    //     this will return null if there are less rows than batch size.
    //   select max(id), count(1) from SQL_STAT_VAL_SRC;  then if count is less than batch size
    //     use the max(id) else use the limit ?,1 approach with ? = count - 1
    // For info these are timings (secs) for various approaches to getting the max id of the top n ids
    // with 1.6 mil rows.
    // | 0.03840500 | select max(id), count(1) from SQL_STAT_VAL_SRC                                    |
    // | 0.11568625 | select id from SQL_STAT_VAL_SRC order by id limit 999999,1                        |
    // | 0.42600850 | select max(id) from (select id from SQL_STAT_VAL_SRC order by id limit 1000000) v |
    private static final String STAGE1_ESTABLISH_BATCH = condenseSql("""
            SELECT MAX(ID) \
            FROM
                (SELECT ID
                FROM SQL_STAT_VAL_SRC \
                ORDER BY ID\
                LIMIT ?) v""");

    // @formatter:off
    private static final String STAGE1_AGGREGATE_SOURCE_KEY = condenseSql("" +
            "INSERT INTO SQL_STAT_KEY (NAME, VER) " +
            "SELECT " +
            "    DISTINCT(SSVS.NAME), " +
            "    1 " +
            "FROM SQL_STAT_VAL_SRC SSVS " +
            "LEFT OUTER JOIN SQL_STAT_KEY SSK on (SSK.NAME = SSVS.NAME) " +
            "WHERE SSVS.ID <= ? " +
            "AND SSK.ID IS NULL");

    // grab the oldest n records from SVS and aggregate those records with the
    // right time range
    // then outer join them to any existing SV records and add the values
    // then insert or update the values back into SV
    // relies on SV having a unique key index on FK_SQL_STAT_KEY_ID, TIME_MS,
    // PRES, VAL_TP
    private static final String SPROC_STAGE1_UPSERT = "{call stage1Upsert (?, ?, ?, ?, ?, ?)}";

    private static final String STAGE1_AGGREGATE_DELETE_SOURCE = condenseSql("" +
            "DELETE FROM SQL_STAT_VAL_SRC " +
            "WHERE ID <= ? " +
            "AND TIME_MS < ? " +
            "AND VAL_TP = ?");

    // Copy stats from one precision to a coarser precision, aggregating them
    // with any stats
    // in the target precision if there are any
    private static final String SPROC_STAGE2_UPSERT =
            "{call stage2Upsert (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";

    private final SQLStatisticsDbConnProvider sqlStatisticsDbConnProvider;
    private final Provider<SQLStatisticsConfig> sqlStatisticsConfigProvider;
    private final ConnectionUtil connectionUtil;

    private final AggregateConfig[] aggregateConfig = new AggregateConfig[]{
            // Stuff Older than a month move to month precision

            // Anything that has just been moved into the stat table do now
            new AggregateConfig(StatisticType.COUNT, MS_MONTH, MONTH_PRECISION, DEFAULT_PRECISION),
            // Otherwise move the day precision older than a month
            new AggregateConfig(StatisticType.COUNT, MS_MONTH, MONTH_PRECISION, DAY_PRECISION),

            // Stuff Older than a a day move to day precision

            // Anything that has just been moved into the stat table do now
            new AggregateConfig(StatisticType.COUNT, MS_DAY, DAY_PRECISION, DEFAULT_PRECISION),
            // Otherwise move the hour precision older than a day
            new AggregateConfig(StatisticType.COUNT, MS_DAY, DAY_PRECISION, HOUR_PRECISION),

            // Stuff Older than a hour move to hour precision

            new AggregateConfig(StatisticType.COUNT, MS_HOUR, HOUR_PRECISION, DEFAULT_PRECISION),

            new AggregateConfig(StatisticType.COUNT, -1, DEFAULT_PRECISION, DEFAULT_PRECISION),

            // Value aggregation as above but just skip hour precision

            new AggregateConfig(StatisticType.VALUE, MS_MONTH, MONTH_PRECISION, DEFAULT_PRECISION),
            new AggregateConfig(StatisticType.VALUE, MS_MONTH, MONTH_PRECISION, DAY_PRECISION),

            new AggregateConfig(StatisticType.VALUE, MS_DAY, DAY_PRECISION, DEFAULT_PRECISION),
            new AggregateConfig(StatisticType.VALUE, -1, DEFAULT_PRECISION, DEFAULT_PRECISION),
    };

    @Inject
    SQLStatisticAggregationTransactionHelper(final SQLStatisticsDbConnProvider sqlStatisticsDbConnProvider,
                                             final Provider<SQLStatisticsConfig> sqlStatisticsConfigProvider,
                                             final ConnectionUtil connectionUtil) {
        this.sqlStatisticsDbConnProvider = sqlStatisticsDbConnProvider;
        this.sqlStatisticsConfigProvider = sqlStatisticsConfigProvider;
        this.connectionUtil = connectionUtil;
    }

    public static final long round(final long timeMs, final int precision) {
        final long scale = (long) Math.pow(10, precision);
        return ((timeMs) / scale) * scale;
    }

    protected int doAggregateSQL_Update(final Connection connection,
                                        final TaskContext taskContext,
                                        final String prefix,
                                        final String sql,
                                        final List<Object> args) throws SQLException {

        final LogExecutionTime time = new LogExecutionTime();
        final String trace = buildSQLTrace(sql, args);

        taskContext.info(() -> prefix + "\n " + trace);

        final int count = connectionUtil.executeUpdate(connection, sql, args);

        LOGGER.debug(() -> LogUtil.message("doAggretateSQL - {} - {} in {} - {}",
                prefix, ModelStringUtil.formatCsv(count), time, trace));
        return count;
    }

    protected Optional<Long> doLongSelect(final Connection connection,
                                          final TaskContext taskContext,
                                          final String prefix,
                                          final String sql,
                                          final List<Object> args) throws SQLException {
        final LogExecutionTime time = new LogExecutionTime();
        final String trace = buildSQLTrace(sql, args);

        taskContext.info(() -> prefix + "\n " + trace);

        final Long result = connectionUtil.executeQueryLongResult(connection, sql, args);

        LOGGER.debug("doAggretateSQL - {} - {} in {} - {}", prefix, ModelStringUtil.formatCsv(result), time, trace);
        return Optional.ofNullable(result);
    }

    public AggregateConfig[] getAggregateConfig() {
        return aggregateConfig;
    }

    protected Long doAggregateSQL_LongResult(final Connection connection,
                                             final String sql,
                                             final List<Object> args)
            throws SQLException {
        return connectionUtil.executeQueryLongResult(connection, sql, args);
    }

    public Long getAggregateMinId() throws SQLException {
        try (final Connection connection = sqlStatisticsDbConnProvider.getConnection()) {
            return doAggregateSQL_LongResult(connection, AGGREGATE_MIN_ID, null);
        }
    }

    public Long getAggregateMaxId() throws SQLException {
        try (final Connection connection = sqlStatisticsDbConnProvider.getConnection()) {
            return doAggregateSQL_LongResult(connection, AGGREGATE_MAX_ID, null);
        }
    }

    public Long getAggregateCount() throws SQLException {
        try (final Connection connection = sqlStatisticsDbConnProvider.getConnection()) {
            return doAggregateSQL_LongResult(connection, AGGREGATE_COUNT, null);
        }
    }

    public long deleteOldStats(final Instant timeNow,
                               final TaskContext taskContext) throws SQLException {
        final AggregateConfig mostCoarseLevel = new AggregateConfig(
                StatisticType.COUNT,
                MS_MONTH,
                MONTH_PRECISION,
                DEFAULT_PRECISION);

        final StroomDuration maxProcessingAge = sqlStatisticsConfigProvider.get().getMaxProcessingAge();

        if (maxProcessingAge != null) {
            LOGGER.debug("Deleting stats using a max processing age of {}ms", maxProcessingAge);
            // convert the max age into a time bucket so we can delete
            // everything older than that time bucket
            final long oldestTimeBucketToKeep = mostCoarseLevel.getAggregateToMs(
                    timeNow.minus(maxProcessingAge.getDuration()));
            try (final Connection connection = sqlStatisticsDbConnProvider.getConnection()) {
                long totalRowsAffected = 0;
                // Do one delete per level so we can interrupt sooner and can use the
                // PK index more efficiently, i.e. (PRES = X AND VAL_TP = Y AND TIME_MS <= Z)
                for (final AggregateConfig level : aggregateConfig) {
                    final String prefix = LogUtil.message(
                            "Deleting stats. Precision {}, type: {} " +
                                    "and a time older than {}",
                            level.getPrecision(),
                            level.getValueType(),
                            DateUtil.createNormalDateTimeString(oldestTimeBucketToKeep));

                    final long rowsAffected = doAggregateSQL_Update(
                            connection,
                            taskContext,
                            prefix,
                            DELETE_OLD_STATS,
                            List.of(
                                    level.getPrecision(),
                                    level.getValueType().getPrimitiveValue(),
                                    oldestTimeBucketToKeep));

                    LOGGER.debug(() -> LogUtil.message(
                            "Deleted {} stats with precision {}, type: {} " +
                                    "and a time older than {}",
                            rowsAffected,
                            level.getPrecision(),
                            level.getValueType(),
                            DateUtil.createNormalDateTimeString(oldestTimeBucketToKeep)));

                    totalRowsAffected += rowsAffected;
                    if (Thread.currentThread().isInterrupted()) {
                        LOGGER.debug(() -> LogUtil.message(
                                "Thread interrupted in stage 2 aggregation (batch level). " +
                                        "targetPrecision: {}, lastPrecision: {}, valueType: {} " +
                                        "Safely stopping here.",
                                level.getPrecision(), level.getLastPrecision(), level.getValueType()));
                        break;
                    }
                }
                LOGGER.debug("Deleted {} stats with a time older than {}", totalRowsAffected,
                        DateUtil.createNormalDateTimeString(oldestTimeBucketToKeep));
                return totalRowsAffected;
            }
        } else {
            LOGGER.debug("No maxProcessingAge configured so don't delete any old stats");
            return 0L;
        }
    }

    // Must be done under cluster lock and at the right time so we don't delete keys that have just
    // been created but don't yet have child records.
    public int deleteUnusedKeys(final TaskContext taskContext) throws SQLException {

        LOGGER.debug("Deleting unused keys");

        // convert the max age into a time bucket so we can delete
        // everything older than that time bucket
        try (final Connection connection = sqlStatisticsDbConnProvider.getConnection()) {

            final LogExecutionTime time = new LogExecutionTime();

            final String sql = DELETE_UNUSED_KEYS;

            taskContext.info(() -> "\n " + sql);

            final int count = connectionUtil.executeUpdate(
                    connection,
                    DELETE_UNUSED_KEYS,
                    Collections.emptyList());

            LOGGER.debug(() -> LogUtil.message("deleteUnusedKeys - {} in {} - {}",
                    ModelStringUtil.formatCsv(count), time, condenseSql(sql)));

            return count;
        }
    }

    public long aggregateConfigStage1(final TaskContext taskContext,
                                      final String prefix,
                                      final long batchSize,
                                      final Instant timeNow) throws SQLException {
        long processCount = 0;

        try (final Connection connection = sqlStatisticsDbConnProvider.getConnection()) {
            // Find the highest ID of the bottom n rows of the sql_stat_val_src table.
            // We can then use this ID as the predicate in later statements to ensure we
            // are processing the right rows.
            final Optional<Long> optBatchMaxId = doLongSelect(
                    connection,
                    taskContext,
                    "Establishing batch to process",
                    STAGE1_ESTABLISH_BATCH,
                    List.of(batchSize));

            if (optBatchMaxId.isEmpty()) {
                LOGGER.debug("No rows found in {}, nothing to do for stage 1",
                        SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME);

            } else {
                final long batchMaxId = optBatchMaxId.get();
                // Fill the STAT_KEY table with any new Keys
                doAggregateSQL_Update(
                        connection,
                        taskContext,
                        AGGREGATE,
                        STAGE1_AGGREGATE_SOURCE_KEY,
                        List.of(batchMaxId));

                // Stage 1 is about handling values in the source table that are
                // implied to be precision 0 and aggregating them into SQL_STAT_VAL
                // at the correct precision for their age.
                for (final AggregateConfig level : aggregateConfig) {
                    if (level.getLastPrecision() == 0) {
                        // precision => bucket size
                        // DEFAULT: 0 => 1 ms
                        // HOUR:    6 => 1_000_000 ms (true hour: 3_600_000 ms)
                        // DAY:     7 => 10_000_000 ms (true day: 86_400_000 ms)
                        // MONTH:   9 => 1_000_000_000 ms (true 31day month = 2_678_400_000 ms)
                        final long bucketSize = (long) Math.pow(10, level.getPrecision());
                        final String bucketSizeStr = ModelStringUtil.formatDurationString(bucketSize);
                        final long aggregateToMs = level.getAggregateToMs(timeNow);

                        final String newPrefix = prefix
                                + " type: " + level.getValueType()
                                + " aggregateTo: < " + DateUtil.createNormalDateTimeString(aggregateToMs)
                                + " Precision: " + level.getPrecision()
                                + " (Bucket size " + bucketSizeStr + ")"
                                + " batchMaxId: " + batchMaxId
                                + " batchSize: " + batchSize;

                        // insert a batch of data in STAT_VAL_SRC into
                        // SQL_STAT_VAL_TMP,
                        // rounding the times to the required
                        // precision and aggregating the values up
                        // if it is a count stat then both the VAL and CNT columns
                        // get the sum of the grouped stats
                        // if it is a value stat then the VAL column gets the sum of
                        // the grouped stats and the CNT column
                        // gets the count of the number of records in the group

                        final int rowsAffectedOnUpsert = callUpsert1(
                                connection,
                                taskContext,
                                newPrefix,
                                level.getSQLPrecision(),
                                level.getPrecision(),
                                level.getValueType().getPrimitiveValue(),
                                aggregateToMs,
                                batchMaxId);
                        LOGGER.debug("rowsAffectedOnUpsert: {}", rowsAffectedOnUpsert);
                        processCount += rowsAffectedOnUpsert;

                        // delete records from STAT_VAL_SRC up to maxSrcId and below
                        // aggregateToMs,
                        // i.e. all the records we have just upserted.
                        int rowsAffectedOnDelete = 0;
                        if (rowsAffectedOnUpsert > 0) {
                            rowsAffectedOnDelete = doAggregateSQL_Update(
                                    connection,
                                    taskContext,
                                    newPrefix,
                                    STAGE1_AGGREGATE_DELETE_SOURCE,
                                    List.of(batchMaxId,
                                            aggregateToMs,
                                            level.getValueType().getPrimitiveValue()));
                            LOGGER.debug("rowsAffectedOnDelete: {}", rowsAffectedOnDelete);
                        }

                        if (rowsAffectedOnUpsert > 0 && rowsAffectedOnDelete == 0) {
                            LOGGER.error(
                                    "Deleted {} rows from SQL_STAT_VAL_SRC but didn't affect any " +
                                            "rows in SQL_STAT_VAL, may have lost some stats",
                                    rowsAffectedOnDelete);
                        }
                    }

                    // If we bail here then we have ensured all keys for our batch, merged all data for
                    // this aggregateConfig into SQL_STAT_VAL and deleted the SQL_STAT_VAL_SRC rows
                    // for this aggregateConfig.  Thus, next time round it can just form a new batch
                    // with whatever is left.
                    if (Thread.currentThread().isInterrupted()) {
                        LOGGER.debug("Thread interrupted during stage 1 aggregation. " +
                                "Safely stopping here.");
                        break;
                    }
                }
            }
        }
        return processCount;
    }

    private int callUpsert1(final Connection connection,
                            final TaskContext taskContext,
                            final String prefix,
                            final byte sqlPrecision,
                            final byte precision,
                            final byte valueType,
                            final long aggregateToMs,
                            final long batchMaxId) throws SQLException {
        final int count;
        final LogExecutionTime time = new LogExecutionTime();
        taskContext.info(() -> prefix);

        try (final CallableStatement stmt = connection.prepareCall(SPROC_STAGE1_UPSERT)) {
            // Set IN parameters
            stmt.setByte(1, sqlPrecision);
            stmt.setByte(2, precision);
            stmt.setByte(3, valueType);
            stmt.setLong(4, aggregateToMs);
            stmt.setLong(5, batchMaxId);

            // Set OUT parameter
            stmt.registerOutParameter(6, Types.INTEGER);

            // Execute stored procedure
            stmt.execute();

            // Get the OUT parameter
            count = stmt.getInt(6);
        } catch (final SQLException sqlException) {
            LOGGER.error("callUpsert1", sqlException);
            throw sqlException;
        }

        LOGGER.debug("callUpsert1 - {} - {} in {}", prefix, ModelStringUtil.formatCsv(count), time);
        return count;
    }

    private int callUpsert2(final Connection connection,
                            final TaskContext taskContext,
                            final String prefix,
                            final byte targetPrecision,
                            final byte targetSqlPrecision,
                            final byte lastPrecision,
                            final byte valueType,
                            final long aggregateToMs,
                            final int batchSize,
                            final int batchNo) throws SQLException {
        final int upsertCount;
        final int deleteCount;
        final LogExecutionTime time = new LogExecutionTime();

        final Supplier<String> infoSupplier = () -> prefix +
                " - Type: " + valueType
                + ", aggregateTo: < " + DateUtil.createNormalDateTimeString(aggregateToMs)
                + ", precision: " + lastPrecision + "=>" + targetPrecision
                + ", batch no.: " + batchNo
                + ", batchSize: " + batchSize;
        LOGGER.debug(() -> LogUtil.message("aggregateToMs: {} ({})",
                aggregateToMs, Instant.ofEpochMilli(aggregateToMs)));

        taskContext.info(infoSupplier);

        try (final CallableStatement stmt = connection.prepareCall(SPROC_STAGE2_UPSERT)) {
            // Set IN parameters
            stmt.setByte(1, targetPrecision);
            stmt.setByte(2, targetSqlPrecision);
            stmt.setByte(3, lastPrecision);
            stmt.setByte(4, valueType);
            stmt.setLong(5, aggregateToMs);
            stmt.setInt(6, batchSize);
            stmt.setBoolean(7, LOGGER.isTraceEnabled());
            // Set OUT parameters
            final int upsertCountParamNum = 8;
            final int deleteCountParamNum = 9;
            final int logParamNum = 10;
            stmt.registerOutParameter(upsertCountParamNum, Types.INTEGER);
            stmt.registerOutParameter(deleteCountParamNum, Types.INTEGER);
            stmt.registerOutParameter(logParamNum, Types.VARCHAR);

            // Execute stored procedure
            stmt.execute();

            // Get the OUT parameter
            upsertCount = stmt.getInt(upsertCountParamNum);
            deleteCount = stmt.getInt(deleteCountParamNum);

            // If trace is enabled the sproc will return useful logging
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("stage2Upsert log:{}", stmt.getString(logParamNum));
            }
        } catch (final SQLException sqlException) {
            LOGGER.error("callUpsert2", sqlException);
            throw sqlException;
        }

        LOGGER.debug(() -> LogUtil.message("callUpsert2 - {} - upsertCount: {}, deleteCount: {} in {}",
                infoSupplier.get(),
                ModelStringUtil.formatCsv(upsertCount),
                ModelStringUtil.formatCsv(deleteCount),
                time));
        // The deleteCount is what we care about as that tells us how many were moved up to the next
        // level
        return deleteCount;
    }

    public long aggregateConfigStage2(final TaskContext taskContext,
                                      final String prefix,
                                      final int stage2BatchSize,
                                      final Instant timeNow)
            throws SQLException {
        long totalCount = 0;
        final List<Integer> iterationsLog = new ArrayList<>(aggregateConfig.length);
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        try (final Connection connection = sqlStatisticsDbConnProvider.getConnection()) {
            // Stage 2 is about moving stats from one precision in STAT_VAL to a
            // coarser one once they have become too old for their current
            // precision
            for (final AggregateConfig level : aggregateConfig) {
                final long aggregateToMs = level.getAggregateToMs(timeNow);
                final byte targetPrecision = level.getPrecision();
                final byte lastPrecision = level.getLastPrecision();
                final byte targetSqlPrecision = level.getSQLPrecision();
                final byte valueType = level.getValueType().getPrimitiveValue();

                // if statement to ignore the case of DEFAULT->DEFAULT where
                // there is nothing to roll up
                if (targetPrecision != lastPrecision) {

                    int deleteCount;
                    int batchNo = 0;
                    // Keep calling it till we get a partial batch
                    do {
                        deleteCount = callUpsert2(
                                connection,
                                taskContext,
                                prefix,
                                targetPrecision,
                                targetSqlPrecision,
                                lastPrecision,
                                valueType,
                                aggregateToMs,
                                stage2BatchSize,
                                ++batchNo);

                        totalCount += deleteCount;
                        if (Thread.currentThread().isInterrupted()) {
                            LOGGER.debug("Thread interrupted in stage 2 aggregation (batch level). " +
                                    "targetPrecision: {}, lastPrecision: {}, valueType: {} " +
                                    "batchNo: {}." +
                                    "Safely stopping here.",
                                    targetPrecision, lastPrecision, valueType, batchNo);
                            break;
                        }
                    } while (deleteCount >= stage2BatchSize);
                    iterationsLog.add(batchNo);
                }
                if (Thread.currentThread().isInterrupted()) {
                    LOGGER.debug("Thread interrupted in stage 2 aggregation. " +
                                    "targetPrecision: {}, lastPrecision: {}, valueType: {}." +
                                    "Safely stopping here.",
                            targetPrecision, lastPrecision, valueType);
                    break;
                }
            }
        }
        LOGGER.info("Completed stage 2 aggregation with max iterations {} in {}{}",
                iterationsLog.stream().max(Comparator.naturalOrder()).orElse(0),
                logExecutionTime.getDuration(),
                (Thread.currentThread().isInterrupted()
                        ? " (INTERRUPTED)"
                        : ""));

        return totalCount;
    }

    public void truncateTable(final String tableName) throws SQLException {
        LOGGER.debug(">>> {}", tableName);
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        try (final Connection connection = sqlStatisticsDbConnProvider.getConnection()) {
            try (final Statement statement = connection.createStatement()) {
                final String sql = TRUNCATE_TABLE_SQL + tableName;
                statement.execute(sql);

                LOGGER.debug("Truncated table {} in {}ms", tableName, logExecutionTime.getDurationMs());

            } catch (final SQLException sqlException) {
                LOGGER.error("truncating table {}", tableName, sqlException);
                throw sqlException;
            }
        }
    }

    public void clearTable(final String tableName) throws SQLException {
        LOGGER.debug(">>> {}", tableName);
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        try (final Connection connection = sqlStatisticsDbConnProvider.getConnection()) {
            try (final Statement statement = connection.createStatement()) {
                final String sql = CLEAR_TABLE_SQL + tableName;
                statement.execute(sql);
                LOGGER.debug("Cleared table {} in {}ms", tableName, logExecutionTime.getDurationMs());

            } catch (final SQLException sqlException) {
                LOGGER.error("Clearing table {}", tableName, sqlException);
                throw sqlException;
            }
        }
    }

    private static String buildSQLTrace(final String sql, final List<Object> args) {
        final StringBuilder sqlString = new StringBuilder();
        int arg = 0;
        for (int i = 0; i < sql.length(); i++) {
            final char c = sql.charAt(i);
            if (c == '?') {
                try {
                    sqlString.append(args.get(arg++));
                } catch (final IndexOutOfBoundsException e) {
                    LOGGER.warn("Mismatch between '?' and args. sql: {}, args: {}", sql, args);
                    sqlString.append(c);
                }
            } else {
                sqlString.append(c);
            }
        }
        return condenseSql(sqlString.toString());
    }


    /**
     * Replace all contiguous white space with one space so we can format the SQL nicely
     * in source but store have java hold it in condensed form.
     */
    static String condenseSql(final String sql) {
        return sql.replaceAll("\\s+", " ")
                .trim();
    }

    public static class AggregateConfig {

        // How
        private final long ageMs;
        private final byte precision;
        private final byte lastPrecision;
        private final StatisticType valueType;
//        private final SimpleCronScheduler simpleCronScheduler;

        public AggregateConfig(final StatisticType valueType,
                               final long ageMs,
                               final byte precision,
                               final byte lastPrecision) {
//                               final String simpleCronExpression) {
            this.valueType = valueType;
            this.ageMs = ageMs;
            this.precision = precision;
            this.lastPrecision = lastPrecision;
//            this.simpleCronScheduler = new SimpleCronScheduler(simpleCronExpression);
        }

        public long getAgeMs() {
            return ageMs;
        }

        public byte getPrecision() {
            return precision;
        }

        /**
         * Used for the MySQL ROUND(number , precision) func
         */
        public byte getSQLPrecision() {
            return (byte) (-1 * precision);
        }

        public byte getLastPrecision() {
            return lastPrecision;
        }

        public StatisticType getValueType() {
            return valueType;
        }

        public long getAggregateToMs(final Instant timeNow) {
            if (ageMs == -1) {
                return NEWEST_SENSIBLE_STAT_AGE;
            }
            final long scale = (long) Math.pow(10, precision);
            final long roundedValue = ((timeNow.toEpochMilli() - ageMs) / scale) * scale;
            return roundedValue;
        }

//        public Long getAggregateToMs(final Long timeNowOverride) {
//            return doGetAggregateToMs(timeNowOverride);
//        }

//        public SimpleCronScheduler getSimpleCronScheduler() {
//            return simpleCronScheduler;
//        }

//        public boolean execute(final long timeNow) {
//            return simpleCronScheduler.execute(timeNow);
//        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("age=");
            builder.append(ModelStringUtil.formatDurationString(ageMs));
            builder.append(",precision=");
            builder.append(precision);
            builder.append("(e.g.");
            final long eg = (long) Math.pow(10, precision);
            builder.append(eg);
            builder.append("(e.g.");
            builder.append(ModelStringUtil.formatDurationString(eg));
            builder.append(")");
//            builder.append(",simpleCronScheduler=");
//            builder.append(simpleCronScheduler);
            return builder.toString();
        }

    }
}
