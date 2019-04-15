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

package stroom.statistics.impl.sql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.statistics.impl.sql.shared.StatisticType;
import stroom.task.api.TaskContext;
import stroom.util.date.DateUtil;
import stroom.util.logging.LogExecutionTime;
import stroom.util.scheduler.SimpleCronScheduler;
import stroom.util.shared.ModelStringUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;

@Singleton
public class SQLStatisticAggregationTransactionHelper {
    public static final long NEWEST_SENSIBLE_STAT_AGE = DateUtil.parseNormalDateTimeString("9999-01-01T00:00:00.000Z");
    // /**
    // * The number of records to add to the aggregate from the aggregate source
    // * table on each pass
    // */
    // private static final int AGGREGATE_INCREMENT = 10000;
    public static final byte DEFAULT_PRECISION = 0;
    public static final long MS_HOUR = 1000 * 60 * 60;
    public static final long MS_DAY = MS_HOUR * 24;
    public static final long MS_MONTH = 31 * MS_DAY;
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
    public static final byte MONTH_PRECISION = (byte) Math.floor(Math.log10(MS_MONTH));
    public static final byte DAY_PRECISION = (byte) Math.floor(Math.log10(MS_DAY));
    public static final byte HOUR_PRECISION = (byte) Math.floor(Math.log10(MS_HOUR));
    private static final Logger LOGGER = LoggerFactory.getLogger(SQLStatisticAggregationTransactionHelper.class);
    private static final String AGGREGATE = "AGGREGATE";
    private static final String AGGREGATE_COUNT = "" +
            "SELECT COUNT(*) " +
            "FROM SQL_STAT_VAL_SRC ";
    private static final String DELETE_OLD_STATS = "" +
            "DELETE FROM SQL_STAT_VAL " +
            "WHERE TIME_MS < ? ";
    // Mark a batch of n records as 'processing' so we can work on them in
    // isolation
    private static final String STAGE1_MARK_PROCESSING = "" +
            "UPDATE SQL_STAT_VAL_SRC " +
            "SET " +
            "PROCESSING = 1 " +
            "LIMIT ? ";

    // @formatter:off
    private static final String STAGE1_AGGREGATE_SOURCE_KEY = "" +
            "INSERT INTO SQL_STAT_KEY (NAME, VER) " +
            "SELECT " +
            "    DISTINCT(SSVS.NAME), " +
            "    1 " +
            "FROM SQL_STAT_VAL_SRC SSVS " +
            "LEFT OUTER JOIN SQL_STAT_KEY SSK on (SSK.NAME = SSVS.NAME) " +
            "WHERE SSVS.PROCESSING = 1 " +
            "AND SSK.ID IS NULL";
    // grab the oldest n records from SVS and aggregate those records with the
    // right time range
    // then outer join them to any existing SV records and add the values
    // then insert or update the values back into SV
    // relies on SV having a unique key index on FK_SQL_STAT_KEY_ID, TIME_MS,
    // PRES, VAL_TP
//    private static final String STAGE1_UPSERT = "" +
//            "INSERT INTO SQL_STAT_VAL (FK_SQL_STAT_KEY_ID, TIME_MS, PRES, VAL_TP, VAL, CT) " +
//            "SELECT " +
//            "   AGG.FK_SQL_STAT_KEY_ID, " +
//            "   AGG.TIME_MS, " +
//            "   AGG.PRES, " +
//            "   AGG.VAL_TP, " +
//            "   AGG.VAL, " +
//            "   AGG.CT " +
//            "FROM ( " +
//            "   SELECT " +
//            "       SSVT.FK_SQL_STAT_KEY_ID as FK_SQL_STAT_KEY_ID, " +
//            "       SSVT.TIME_MS_RND as TIME_MS, " +
//            "       SSVT.PRES as PRES, " +
//            "       SSVT.VAL_TP as VAL_TP, " +
//            "       @aggregateValue:=(COALESCE(SSVT.VAL,0) + COALESCE(SSV.VAL, 0)) as VAL, " +
//            "       @aggregateCount:=(COALESCE(SSVT.CT,0) + COALESCE(SSV.CT, 0)) as CT " +
//            "   FROM ( " +
//            "       SELECT " +
//            "           ROUND(SSVS.TIME_MS, ?) AS TIME_MS_RND, " +
//            "           ? as PRES, " +
//            "           ? as VAL_TP, " +
//            "           SUM(SSVS.VAL) as VAL, " +
//            "           SUM(CASE SSVS.VAL_TP WHEN " + StatisticType.COUNT.getPrimitiveValue() + " THEN SSVS.VAL ELSE 1 END) as CT, " +
//            "           SSK.ID as FK_SQL_STAT_KEY_ID " +
//            "           FROM SQL_STAT_VAL_SRC SSVS " +
//            "           JOIN SQL_STAT_KEY SSK ON (SSK.NAME = SSVS.NAME) " +
//            "           WHERE SSVS.TIME_MS < ? " +
//            "           AND SSVS.VAL_TP = ? " +
//            "       AND SSVS.PROCESSING = 1 " +
//            "       GROUP BY FK_SQL_STAT_KEY_ID, TIME_MS_RND, SSVS.VAL_TP, PRES " +
//            "       HAVING COUNT(*) > 0 " +
//            "   ) SSVT " +
//            "   LEFT JOIN SQL_STAT_VAL SSV ON (" +
//            "       SSV.FK_SQL_STAT_KEY_ID = SSVT.FK_SQL_STAT_KEY_ID AND " +
//            "       SSV.TIME_MS = SSVT.TIME_MS_RND AND " +
//            "       SSV.PRES = SSVT.PRES AND SSV.VAL_TP = SSVT.VAL_TP" +
//            "   ) " +
//            ") AGG " +
//            "WHERE @aggregateCount > 0 " +
//            "ON DUPLICATE KEY UPDATE " +
//            "   VAL = @aggregateValue, " +
//            "   CT = @aggregateCount ";
//
//    private static final String STAGE1_UPSERT = "" +
//            "INSERT INTO SQL_STAT_VAL (FK_SQL_STAT_KEY_ID, TIME_MS, PRES, VAL_TP, VAL, CT) " +
//            "SELECT " +
//            "   AGG.FK_SQL_STAT_KEY_ID, " +
//            "   AGG.TIME_MS, " +
//            "   AGG.PRES, " +
//            "   AGG.VAL_TP, " +
//            "   AGG.VAL, " +
//            "   AGG.CT " +
//            "FROM ( " +
//            "   SELECT " +
//            "       SSVT.FK_SQL_STAT_KEY_ID as FK_SQL_STAT_KEY_ID, " +
//            "       SSVT.TIME_MS_RND as TIME_MS, " +
//            "       SSVT.PRES as PRES, " +
//            "       SSVT.VAL_TP as VAL_TP, " +
//            "       @aggregateValue:=(COALESCE(SSVT.VAL,0) + COALESCE(SSV.VAL, 0)) as VAL, " +
//            "       @aggregateCount:=(COALESCE(SSVT.CT,0) + COALESCE(SSV.CT, 0)) as CT " +
//            "   FROM ( " +
//            "       SELECT " +
//            "           ROUND(SSVS.TIME_MS, ?) AS TIME_MS_RND, " +
//            "           ? as PRES, " +
//            "           ? as VAL_TP, " +
//            "           SUM(SSVS.VAL) as VAL, " +
//            "           SUM(CASE SSVS.VAL_TP WHEN " + StatisticType.COUNT.getPrimitiveValue() + " THEN SSVS.VAL ELSE 1 END) as CT, " +
//            "           SSK.ID as FK_SQL_STAT_KEY_ID " +
//            "           FROM SQL_STAT_VAL_SRC SSVS " +
//            "           JOIN SQL_STAT_KEY SSK ON (SSK.NAME = SSVS.NAME) " +
//            "           WHERE SSVS.TIME_MS < ? " +
//            "           AND SSVS.VAL_TP = ? " +
//            "       AND SSVS.PROCESSING = 1 " +
//            "       GROUP BY FK_SQL_STAT_KEY_ID, TIME_MS_RND, SSVS.VAL_TP, PRES " +
//            "       HAVING COUNT(*) > 0 " +
//            "   ) SSVT " +
//            "   LEFT JOIN SQL_STAT_VAL SSV ON (" +
//            "       SSV.FK_SQL_STAT_KEY_ID = SSVT.FK_SQL_STAT_KEY_ID AND " +
//            "       SSV.TIME_MS = SSVT.TIME_MS_RND AND " +
//            "       SSV.PRES = SSVT.PRES AND SSV.VAL_TP = SSVT.VAL_TP" +
//            "   ) " +
//            ") AGG " +
//            "WHERE @aggregateCount > 0 " +
//            "ON DUPLICATE KEY UPDATE " +
//            "   VAL = @aggregateValue, " +
//            "   CT = @aggregateCount ";
    private static final String SPROC_STAGE1_UPSERT = "{call stage1Upsert (?, ?, ?, ?, ?)}";
    private static final String STAGE1_AGGREGATE_DELETE_SOURCE = "" +
            "DELETE FROM SQL_STAT_VAL_SRC " +
            "WHERE PROCESSING = 1 " +
            "AND TIME_MS < ? " +
            "AND VAL_TP = ?";
    // Find if records exist in STAT_VAL older than a given age for a given
    // precision
    private static final String STAGE2_FIND_ROWS_TO_MOVE = "" +
            "SELECT " +
            "   EXISTS( " +
            "       SELECT " +
            "           NULL " +
            "       FROM SQL_STAT_VAL SSV" +
            "       WHERE SSV.TIME_MS < ? " +
            "       AND SSV.PRES = ? " + // old PRES
            "       AND SSV.VAL_TP = ? " +
            "   ) ";
    // Copy stats from one precision to a coarser precision, aggregating them
    // with any stats
    // in the target precision if there are any
//    private static final String STAGE2_UPSERT = "" +
//            "INSERT INTO SQL_STAT_VAL (FK_SQL_STAT_KEY_ID, TIME_MS, PRES, VAL_TP, VAL, CT) " +
//            "SELECT " +
//            "   AGG.FK_SQL_STAT_KEY_ID, " +
//            "   AGG.TIME_MS, " +
//            "   ? as PRES, " + // target pres
//            "   ? as VAL_TP, " +
//            "   AGG.VAL AS VAL, " +
//            "   AGG.CT AS CT " +
//            "FROM (" +
//            "   SELECT " +
//            "      FK_SQL_STAT_KEY_ID, " +
//            "      TIME_MS, " +
//            "      @aggregateValue:=SUM(VAL) AS VAL, " +
//            "      @aggregateCount:=SUM(CT) AS CT " +
//            "   FROM ( " +
//            "      SELECT " + // existing values at correct precision
//            "         SSVO.TIME_MS, " +
//            "         SSVO.VAL, " +
//            "         SSVO.CT, " +
//            "         SSVO.FK_SQL_STAT_KEY_ID " +
//            "      FROM SQL_STAT_VAL SSVO " +
//            "      WHERE SSVO.PRES = ? " + // target pres
//            "      AND SSVO.VAL_TP = ? " +
//            "      AND SSVO.TIME_MS <= ROUND(?, ?) " + // only pick up records up to the point we are interested in
//            "      UNION ALL " +
//            "      SELECT " +
//            "         ROUND(SSVN.TIME_MS, ?) AS TIME_MS, " + // target pres, e.g. -9
//            "         SSVN.VAL AS VAL, " +
//            "         SSVN.CT AS CT, " +
//            "         SSVN.FK_SQL_STAT_KEY_ID AS FK_SQL_STAT_KEY_ID " +
//            "      FROM SQL_STAT_VAL SSVN " +
//            "      WHERE SSVN.TIME_MS < ? " + // stat age threshold to change precision
//            "      AND SSVN.PRES = ? " + // old PRES
//            "      AND SSVN.VAL_TP = ? " +
//            "      GROUP BY FK_SQL_STAT_KEY_ID, TIME_MS " +
//            "   ) ROUNDED " +
//            "   GROUP BY FK_SQL_STAT_KEY_ID, TIME_MS " +
//            ") AGG " +
//            "ON DUPLICATE KEY UPDATE " +
//            "VAL = @aggregateValue, " +
//            "CT =  @aggregateCount ";
//
//    private static final String STAGE2_UPSERT = new StringBuilder()
//            .append("INSERT INTO SQL_STAT_VAL (FK_SQL_STAT_KEY_ID, TIME_MS, PRES, VAL_TP, VAL, CT) ")
//            .append("SELECT ")
//            .append("   AGG.FK_SQL_STAT_KEY_ID,  ")
//            .append("   AGG.TIME_MS,  ")
//            .append("   ? as PRES,  ") // target pres
//            .append("   ? as VAL_TP,  ")
//            .append("   AGG.VAL AS VAL,  ")
//            .append("   AGG.CT AS CT ")
//            .append("FROM ( ")
//            .append("   SELECT ")
//            .append("       FK_SQL_STAT_KEY_ID,  ")
//            .append("       TIME_MS,  ")
//            .append("       SUM(VAL) AS VAL,  ")
//            .append("       SUM(CT) AS CT ")
//            .append("   FROM ( ")
//            .append("       SELECT ") // existing values at correct precision
//            .append("           SSVO.TIME_MS, ")
//            .append("           SSVO.VAL, ")
//            .append("           SSVO.CT, ")
//            .append("           SSVO.FK_SQL_STAT_KEY_ID ")
//            .append("       FROM SQL_STAT_VAL SSVO ")
//            .append("       WHERE SSVO.PRES = ? ") // target pres
//            .append("       AND SSVO.VAL_TP = ? ")
//            .append("       AND SSVO.TIME_MS <= ROUND(?, ?) ") // only pick up records up to the point we are interested in
//            .append("       UNION ALL ")
//            .append("       SELECT ")
//            .append("           ROUND(SSVN.TIME_MS, ?), ") // target pres, e.g. -9
//            .append("           SSVN.VAL, ")
//            .append("           SSVN.CT, ")
//            .append("           SSVN.FK_SQL_STAT_KEY_ID ")
//            .append("       FROM SQL_STAT_VAL SSVN ")
//            .append("       WHERE SSVN.TIME_MS < ? ") // stat age threshold to change precision
//            .append("       AND SSVN.PRES = ? ") // old PRES
//            .append("       AND SSVN.VAL_TP = ? ")
//            .append("   ) ROUNDED ")
//            .append("   GROUP BY ROUNDED.FK_SQL_STAT_KEY_ID, ROUNDED.TIME_MS ")
//            .append(") AGG ")
//            .append("ON DUPLICATE KEY UPDATE ")
//            .append("   VAL = AGG.VAL, ")
//            .append("   CT = AGG.CT ")
//            .toString();

    private static final String SPROC_STAGE2_UPSERT = "{call stage2Upsert (?, ?, ?, ?, ?, ?)}";

    // Delete records from STAT_VAL older than a certain threshold for a given
    // precision
    private static final String STAGE2_AGGREGATE_DELETE_OLD_PRECISION = "" +
            "DELETE FROM SQL_STAT_VAL " +
            "WHERE TIME_MS < ? " +
            "AND PRES = ? " +
            "AND VAL_TP = ?";
    private final ConnectionProvider connectionProvider;
    private final SQLStatisticsConfig config;

    // @formatter:on
    private final AggregateConfig[] aggregateConfig = new AggregateConfig[]{
            // Stuff Older than a month move to month precision

            // Anything that has just been moved into the stat table do now
            new AggregateConfig(StatisticType.COUNT, MS_MONTH, MONTH_PRECISION, DEFAULT_PRECISION, "* * *"),
            // Otherwise move the day precision older than a month
            new AggregateConfig(StatisticType.COUNT, MS_MONTH, MONTH_PRECISION, DAY_PRECISION, "0 0 *"),

            // Stuff Older than a a day move to day precision

            // Anything that has just been moved into the stat table do now
            new AggregateConfig(StatisticType.COUNT, MS_DAY, DAY_PRECISION, DEFAULT_PRECISION, "* * *"),
            // Otherwise move the hour precision older than a day
            new AggregateConfig(StatisticType.COUNT, MS_DAY, DAY_PRECISION, HOUR_PRECISION, "0 * *"),

            // Stuff Older than a hour move to hour precision

            new AggregateConfig(StatisticType.COUNT, MS_HOUR, HOUR_PRECISION, DEFAULT_PRECISION, "* * *"),

            new AggregateConfig(StatisticType.COUNT, -1, DEFAULT_PRECISION, DEFAULT_PRECISION, "* * *"),

            // Value aggregation as above but just skip hour precision

            new AggregateConfig(StatisticType.VALUE, MS_MONTH, MONTH_PRECISION, DEFAULT_PRECISION, "* * *"),
            new AggregateConfig(StatisticType.VALUE, MS_MONTH, MONTH_PRECISION, DAY_PRECISION, "0 0 *"),

            new AggregateConfig(StatisticType.VALUE, MS_DAY, DAY_PRECISION, DEFAULT_PRECISION, "0 * *"),
            new AggregateConfig(StatisticType.VALUE, -1, DEFAULT_PRECISION, DEFAULT_PRECISION, "* * *"),

    };

    @Inject
    SQLStatisticAggregationTransactionHelper(final ConnectionProvider connectionProvider,
                                             final SQLStatisticsConfig config) {
        this.connectionProvider = connectionProvider;
        this.config = config;
    }

    public static final long round(final long timeMs, final int precision) {
        final long scale = (long) Math.pow(10, precision);
        return ((timeMs) / scale) * scale;
    }

//    int doUpdate(final Connection connection, final TaskContext taskContext, final String prefix,
//                 String sql, final List<Object> args) throws SQLException {
//        final LogExecutionTime time = new LogExecutionTime();
//        final String trace = buildSQLTrace(sql, args);
//
//        taskContext.info("{}\n {}", prefix, trace);
//
//        for (final Object arg : args) {
//            sql = sql.replaceFirst("\\?", String.valueOf(arg));
//        }
//
//
//        try (final Statement statement = connection.createStatement()) {
//            statement.execute(sql);
//        } catch (final SQLException sqlException) {
//            throw sqlException;
//        }
//        return 1;
//
////        final int count = ConnectionUtil.executeUpdate(connection, sql, args);
////
////        logDebug("doAggretateSQL - {} - {} in {} - {}", prefix, ModelStringUtil.formatCsv(count), time, trace);
////        return count;
//    }


    protected int doAggregateSQL_Update(final Connection connection, final TaskContext taskContext, final String prefix,
                                        final String sql, final List<Object> args) throws SQLException {
        final LogExecutionTime time = new LogExecutionTime();
        final String trace = buildSQLTrace(sql, args);

        taskContext.info("{}\n {}", prefix, trace);

        final int count = ConnectionUtil.executeUpdate(connection, sql, args);

        logDebug("doAggretateSQL - {} - {} in {} - {}", prefix, ModelStringUtil.formatCsv(count), time, trace);
        return count;
    }

    protected long doLongSelect(final Connection connection, final TaskContext taskContext, final String prefix,
                                final String sql, final List<Object> args) throws SQLException {
        final LogExecutionTime time = new LogExecutionTime();
        final String trace = buildSQLTrace(sql, args);

        taskContext.info("{}\n {}", prefix, trace);

        final long result = ConnectionUtil.executeQueryLongResult(connection, sql, args);

        logDebug("doAggretateSQL - {} - {} in {} - {}", prefix, ModelStringUtil.formatCsv(result), time, trace);
        return result;
    }

    public AggregateConfig[] getAggregateConfig() {
        return aggregateConfig;
    }

    protected Long doAggregateSQL_LongResult(final Connection connection, final String sql, final List<Object> args)
            throws SQLException {
        return ConnectionUtil.executeQueryLongResult(connection, sql, args);
    }

    public Long getAggregateMinId() throws SQLException {
        try (final Connection connection = connectionProvider.getConnection()) {
            return doAggregateSQL_LongResult(connection, AGGREGATE_MIN_ID, null);
        }
    }

    public Long getAggregateMaxId() throws SQLException {
        try (final Connection connection = connectionProvider.getConnection()) {
            return doAggregateSQL_LongResult(connection, AGGREGATE_MAX_ID, null);
        }
    }

    public Long getAggregateCount() throws SQLException {
        try (final Connection connection = connectionProvider.getConnection()) {
            return doAggregateSQL_LongResult(connection, AGGREGATE_COUNT, null);
        }
    }

    public Long deleteOldStats(final TaskContext taskContext) throws SQLException {
        return deleteOldStats(System.currentTimeMillis(), taskContext);
    }

    public Long deleteOldStats(final long timeNowMs, final TaskContext taskContext) throws SQLException {
        final AggregateConfig mostCoarseLevel = new AggregateConfig(StatisticType.COUNT, MS_MONTH, MONTH_PRECISION,
                DEFAULT_PRECISION, "* * *");

        final Long retentionAgeMs = getStatsRetentionAgeMs();

        LOGGER.debug("Deleting stats using a max processing age of {}ms", retentionAgeMs);

        if (retentionAgeMs != null) {
            // convert the max age into a time bucket so we can delete
            // everything older than that time bucket
            final long oldestTimeBucketToKeep = mostCoarseLevel.getAggregateToMs(timeNowMs - retentionAgeMs);
            try (final Connection connection = connectionProvider.getConnection()) {
                final long rowsAffected = doAggregateSQL_Update(connection, taskContext, "", DELETE_OLD_STATS,
                        Arrays.asList((Object) oldestTimeBucketToKeep));
                LOGGER.info("Deleted {} stats with a time older than {}", rowsAffected,
                        DateUtil.createNormalDateTimeString(oldestTimeBucketToKeep));
                return rowsAffected;
            }
        } else {
            return 0L;
        }
    }

    protected void logDebug(final Object... args) {
        Arrays.asList(args).forEach(arg -> LOGGER.debug(arg.toString()));
    }

    public long aggregateConfigStage1(final TaskContext taskContext, final String prefix, final long batchSize,
                                      final long timeNowMs) throws SQLException {
        long processCount = 0;

        try (final Connection connection = connectionProvider.getConnection()) {
            // mark a set of records in STATVAL_SRC as being processed so all
            // DML below can filter by them
            // records are chosen at random to avoid overhead of a sort
            processCount = doAggregateSQL_Update(connection, taskContext, AGGREGATE, STAGE1_MARK_PROCESSING,
                    Arrays.asList((Object) batchSize));

            // Fill the STAT_KEY table with any new Keys
            doAggregateSQL_Update(connection, taskContext, AGGREGATE, STAGE1_AGGREGATE_SOURCE_KEY, null);

            // Stage 1 is about handling values in the source table that are
            // implied to be precision 0 and aggregating them into SQL_STAT_VAL
            // at the correct precision for their age.
            for (final AggregateConfig level : aggregateConfig) {
                if (level.getLastPrecision() == 0) {
                    final long bucketSize = (long) Math.pow(10, level.getPrecision());
                    final String bucketSizeStr = ModelStringUtil.formatDurationString(bucketSize);
                    final long aggregateToMs = level.getAggregateToMs(timeNowMs);

                    final String newPrefix = prefix + " Source " + level.getValueType() + " < "
                            + DateUtil.createNormalDateTimeString(aggregateToMs) + " P=" + level.getPrecision()
                            + " (Size " + bucketSizeStr + ")";

                    // insert a batch of data in STAT_VAL_SRC into
                    // SQL_STAT_VAL_TMP,
                    // rounding the times to the required
                    // precision and aggregating the values up
                    // if it is a count stat then both the VAL and CNT columns
                    // get the sum of the grouped stats
                    // if it is a value stat then the VAL column gets the sum of
                    // the grouped stats and the CNT column
                    // gets the count of the number of records in the group

                    final int rowsAffectedOnUpsert = callUpsert1(connection,
                            taskContext,
                            newPrefix,
                            level.getSQLPrecision(),
                            level.getPrecision(),
                            level.getValueType().getPrimitiveValue(),
                            aggregateToMs);

//                    final int rowsAffectedOnUpsert = doAggregateSQL_Update(connection, taskContext, newPrefix,
//                            STAGE1_UPSERT,
//                            Arrays.asList(level.getSQLPrecision(), level.getPrecision(),
//                                    level.getValueType().getPrimitiveValue(), aggregateToMs,
//                                    level.getValueType().getPrimitiveValue()));

                    // delete records from STAT_VAL_SRC up to maxSrcId and below
                    // aggregateToMs,
                    // i.e. all the records we have just upserted.
                    int rowsAffectedOnDelete = 0;
                    if (rowsAffectedOnUpsert > 0) {
                        rowsAffectedOnDelete = doAggregateSQL_Update(connection, taskContext, newPrefix,
                                STAGE1_AGGREGATE_DELETE_SOURCE,
                                Arrays.asList(aggregateToMs, level.getValueType().getPrimitiveValue()));
                    }

                    if (rowsAffectedOnUpsert > 0 && rowsAffectedOnDelete == 0) {
                        LOGGER.error(
                                "Deleted {} rows from SQL_STAT_VAL_SRC but didn't affect any rows in SQL_STAT_VAL, may have lost some stats",
                                rowsAffectedOnDelete);
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
                            final long aggregateToMs) throws SQLException {
        int count;
        final LogExecutionTime time = new LogExecutionTime();
        taskContext.info("{}\n {}", prefix, SPROC_STAGE1_UPSERT);

        LOGGER.debug(">>> {}", SPROC_STAGE1_UPSERT);
        try (final CallableStatement stmt = connection.prepareCall(SPROC_STAGE1_UPSERT)) {
            // Set IN parameters
            stmt.setByte(1, sqlPrecision);
            stmt.setByte(2, precision);
            stmt.setByte(3, valueType);
            stmt.setLong(4, aggregateToMs);

            // Set OUT parameter
            stmt.registerOutParameter(5, Types.INTEGER);

            // Execute stored procedure
            stmt.execute();

            // Get the OUT parameter
            count = stmt.getInt(5);
        } catch (final SQLException sqlException) {
            LOGGER.error("callUpsert1", sqlException);
            throw sqlException;
        }

        logDebug("callUpsert1 - {} - {} in {}", prefix, ModelStringUtil.formatCsv(count), time);
        return count;
    }

    private int callUpsert2(final Connection connection,
                            final TaskContext taskContext,
                            final String prefix,
                            final byte targetPrecision,
                            final byte targetSqlPrecision,
                            final byte lastPrecision,
                            final byte valueType,
                            final long aggregateToMs) throws SQLException {
        int count;
        final LogExecutionTime time = new LogExecutionTime();
        taskContext.info("{}\n {}", prefix, SPROC_STAGE2_UPSERT);

        LOGGER.debug(">>> {}", SPROC_STAGE2_UPSERT);
        try (final CallableStatement stmt = connection.prepareCall(SPROC_STAGE2_UPSERT)) {
            // Set IN parameters
            stmt.setByte(1, targetPrecision);
            stmt.setByte(2, targetSqlPrecision);
            stmt.setByte(3, lastPrecision);
            stmt.setByte(4, valueType);
            stmt.setLong(5, aggregateToMs);

            // Set OUT parameter
            stmt.registerOutParameter(6, Types.INTEGER);

            // Execute stored procedure
            stmt.execute();

            // Get the OUT parameter
            count = stmt.getInt(6);
        } catch (final SQLException sqlException) {
            LOGGER.error("callUpsert2", sqlException);
            throw sqlException;
        }

        logDebug("callUpsert2 - {} - {} in {}", prefix, ModelStringUtil.formatCsv(count), time);
        return count;
    }

    public void aggregateConfigStage2(final TaskContext taskContext, final String prefix, final long timeNowMs)
            throws SQLException {
        try (final Connection connection = connectionProvider.getConnection()) {
            // Stage 2 is about moving stats from one precision in STAT_VAL to a
            // coarser one once they have become too old for their current
            // precision
            for (final AggregateConfig level : aggregateConfig) {
                final long bucketSize = (long) Math.pow(10, level.getPrecision());
                final String bucketSizeStr = ModelStringUtil.formatDurationString(bucketSize);
                final long aggregateToMs = level.getAggregateToMs(timeNowMs);
                final byte targetPrecision = level.getPrecision();
                final byte lastPrecision = level.getLastPrecision();
                final byte targetSqlPrecision = level.getSQLPrecision();
                final byte valueType = level.getValueType().getPrimitiveValue();

                // if statement to ignore the case of DEFAULT->DEFAULT where
                // there is nothing to roll up
                if (targetPrecision != lastPrecision) {
                    final String newPrefix = prefix + " Existing " + level.getValueType() + " < "
                            + DateUtil.createNormalDateTimeString(aggregateToMs) + " P=" + level.getLastPrecision()
                            + ">" + level.getPrecision() + " (Size " + bucketSizeStr + ")";

                    // check if there are any rows that need moving to a new
                    // precision for this time range, precision
                    // and value type, returns zero if none exist, one
                    // otherwise. Don't car how many rows there are
                    // just that there are more than zero. This stops the upsert
                    // from changing rows for no reason.
                    final long rowsExist = doLongSelect(connection, taskContext, newPrefix, STAGE2_FIND_ROWS_TO_MOVE,
                            Arrays.asList(aggregateToMs, lastPrecision, valueType));

                    if (rowsExist == 1) {
                        // Find any stats that are too old for their current
                        // precision and roll them up into a
                        // coarser precision, aggregating with existing stats if
                        // required. Does an update or
                        // insert depending on if the target precision has a
                        // record or not.


//                        LOGGER.info("--------BEFORE--------");
//                        report(connection);
//
//
//                        final String createTempTable = "    CREATE TEMPORARY TABLE TEMP_AGG AS (\n" +
//                                "        SELECT\n" +
//                                "            FK_SQL_STAT_KEY_ID,\n" +
//                                "            TIME_MS,\n" +
//                                "            SUM(VAL) AS VAL,\n" +
//                                "            SUM(CT) AS CT\n" +
//                                "        FROM (\n" +
//                                "            SELECT \n" +
//                                "                SSVO.TIME_MS,\n" +
//                                "                SSVO.VAL,\n" +
//                                "                SSVO.CT,\n" +
//                                "                SSVO.FK_SQL_STAT_KEY_ID\n" +
//                                "            FROM SQL_STAT_VAL SSVO\n" +
//                                "            WHERE SSVO.PRES = " + targetPrecision + " \n" +
//                                "            AND SSVO.VAL_TP = " + valueType + "\n" +
//                                "            AND SSVO.TIME_MS <= ROUND(" + aggregateToMs + ", " + targetSqlPrecision + ") \n" +
//                                "            UNION ALL\n" +
//                                "            SELECT\n" +
//                                "                ROUND(SSVN.TIME_MS, " + targetSqlPrecision + "), \n" +
//                                "                SSVN.VAL,\n" +
//                                "                SSVN.CT,\n" +
//                                "                SSVN.FK_SQL_STAT_KEY_ID\n" +
//                                "            FROM SQL_STAT_VAL SSVN\n" +
//                                "            WHERE SSVN.TIME_MS < " + aggregateToMs + "\n" +
//                                "            AND SSVN.PRES = " + lastPrecision + " \n" +
//                                "            AND SSVN.VAL_TP = " + valueType + "\n" +
//                                "        ) ROUNDED\n" +
//                                "        GROUP BY ROUNDED.FK_SQL_STAT_KEY_ID, ROUNDED.TIME_MS\n" +
//                                "    );\n";
//
//                        final String insert = "    INSERT INTO SQL_STAT_VAL (FK_SQL_STAT_KEY_ID, TIME_MS, PRES, VAL_TP, VAL, CT)\n" +
//                                "    SELECT\n" +
//                                "        TEMP_AGG.FK_SQL_STAT_KEY_ID,\n" +
//                                "        TEMP_AGG.TIME_MS,\n" +
//                                "        " + targetPrecision + " as PRES, \n" +
//                                "        " + valueType + " as VAL_TP,\n" +
//                                "        TEMP_AGG.VAL AS VAL,\n" +
//                                "        TEMP_AGG.CT AS CT\n" +
//                                "    FROM TEMP_AGG\n" +
//                                "    ON DUPLICATE KEY UPDATE\n" +
//                                "        VAL = TEMP_AGG.VAL,\n" +
//                                "        CT = TEMP_AGG.CT;\n";
//
//                        final String dropTempTable = "    DROP TEMPORARY TABLE TEMP_AGG;";


//                        int upsertCount;
//                        ConnectionUtil.executeStatement(connection, createTempTable);
//                        try (final Statement statement = connection.createStatement()) {
//                            upsertCount = statement.executeUpdate(insert);
//                        }
//                        ConnectionUtil.executeStatement(connection, dropTempTable);


                        final int upsertCount = callUpsert2(connection,
                                taskContext,
                                newPrefix,
                                targetPrecision,
                                targetSqlPrecision,
                                lastPrecision,
                                valueType,
                                aggregateToMs);


//                        final int upsertCount = doAggregateSQL_Update(connection, taskContext, newPrefix, STAGE2_UPSERT,
//                                Arrays.asList(targetPrecision, valueType, targetPrecision, valueType,
//                                        aggregateToMs, targetSqlPrecision, targetSqlPrecision, aggregateToMs,
//                                        lastPrecision, valueType));

//                        LOGGER.info("--------AFTER--------");
//                        report(connection);

                        if (upsertCount > 0) {
                            // now delete the old stats that we just copied up
                            // into the new precision
                            doAggregateSQL_Update(connection, taskContext, newPrefix,
                                    STAGE2_AGGREGATE_DELETE_OLD_PRECISION,
                                    Arrays.asList(aggregateToMs, lastPrecision, valueType));
                        }
                    } else {
                        LOGGER.debug("No rows to move");
                    }
                }
            }
        }
    }

//    private void report(final Connection connection) {
//        try {
//            final StringBuilder sb = new StringBuilder();
//            try (final Statement statement = connection.createStatement()) {
//                try (final ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM SQL_STAT_VAL")) {
//                    if (resultSet.next()) {
//                        sb.append("SQL_STAT_VAL RECORD COUNT = " + resultSet.getInt(1) + "\n");
//                    }
//                }
//            }
//            try (final Statement statement = connection.createStatement()) {
//                try (final ResultSet resultSet = statement.executeQuery("SELECT TIME_MS, PRES, VAL_TP, VAL, CT, FK_SQL_STAT_KEY_ID FROM SQL_STAT_VAL")) {
//                    if (resultSet.next()) {
//                        sb.append(resultSet.getLong(1) + "|" +
//                                resultSet.getByte(2) + "|" +
//                                resultSet.getByte(3) + "|" +
//                                resultSet.getLong(4) + "|" +
//                                resultSet.getLong(5) + "|" +
//                                resultSet.getLong(6) + "\n");
//                    }
//                }
//            }
//            LOGGER.info("\n" + sb.toString());
//        } catch (final SQLException e) {
//            LOGGER.error(e.getMessage(), e);
//        }
//    }

    private Long getStatsRetentionAgeMs() {
        final String propVal = config.getMaxProcessingAge();
        final Long ageMs = ModelStringUtil.parseDurationString(propVal);
        return ageMs;
    }

    public void truncateTable(final String tableName) throws SQLException {
        LOGGER.debug(">>> {}", tableName);
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        try (final Connection connection = connectionProvider.getConnection()) {
            try (final Statement statement = connection.createStatement()) {
                final String sql = TRUNCATE_TABLE_SQL + tableName;
                // (TRUNCATE_TABLE_SQL
                // +
                // tableName);
                statement.execute(sql);

                LOGGER.debug("Truncated table {} in {}ms", tableName, logExecutionTime.getDuration());

            } catch (final SQLException sqlException) {
                LOGGER.error("truncating table {}", tableName, sqlException);
                throw sqlException;
            }
        }
    }

    public void clearTable(final String tableName) throws SQLException {
        LOGGER.debug(">>> {}", tableName);
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        try (final Connection connection = connectionProvider.getConnection()) {
            try (final Statement statement = connection.createStatement()) {
                final String sql = CLEAR_TABLE_SQL + tableName;
                statement.execute(sql);
                LOGGER.debug("Cleared table {} in {}ms", tableName, logExecutionTime.getDuration());

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
                sqlString.append(args.get(arg++));
            } else {
                sqlString.append(c);
            }
        }
        return sqlString.toString();
    }

    public static class AggregateConfig {
        // How
        private final long ageMs;
        private final byte precision;
        private final byte lastPrecision;
        private final StatisticType valueType;
        private final SimpleCronScheduler simpleCronScheduler;

        public AggregateConfig(final StatisticType valueType, final long ageMs, final byte precision,
                               final byte lastPrecision, final String simpleCronExpression) {
            this.valueType = valueType;
            this.ageMs = ageMs;
            this.precision = precision;
            this.lastPrecision = lastPrecision;
            this.simpleCronScheduler = new SimpleCronScheduler(simpleCronExpression);
        }

        public long getAgeMs() {
            return ageMs;
        }

        public byte getPrecision() {
            return precision;
        }

        public byte getSQLPrecision() {
            return (byte) (-1 * precision);
        }

        public byte getLastPrecision() {
            return lastPrecision;
        }

        public StatisticType getValueType() {
            return valueType;
        }

        public long doGetAggregateToMs(final long timeNow) {
            if (ageMs == -1) {
                return NEWEST_SENSIBLE_STAT_AGE;
            }
            final long scale = (long) Math.pow(10, precision);
            final long roundedValue = ((timeNow - ageMs) / scale) * scale;
            return roundedValue;
        }

        public Long getAggregateToMs(final Long timeNowOverride) {
            return doGetAggregateToMs(timeNowOverride);
        }

        public SimpleCronScheduler getSimpleCronScheduler() {
            return simpleCronScheduler;
        }

        public boolean execute(final long timeNow) {
            return simpleCronScheduler.execute(timeNow);
        }

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
            builder.append(",simpleCronScheduler=");
            builder.append(simpleCronScheduler);
            return builder.toString();
        }

    }
}
