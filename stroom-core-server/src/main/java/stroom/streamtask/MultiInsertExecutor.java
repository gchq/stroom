package stroom.streamtask;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.util.PreparedStatementUtil;
import stroom.util.collections.BatchingIterator;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Class for doing INSERT INTO x VALUES (...), (...), ... (...) type statements,
 * with a manageable batch size and re-using the prepared statements.
 * This is to avoid using a hibernate native sql approach that will
 * cache a query plan for each unique query. An insert with two rows is considered different to
 * an insert with three rows so the cache quickly fills up with hugh insert queries, each with
 * MANY param objects.
 */
class MultiInsertExecutor implements AutoCloseable {
    private final Logger LOGGER = LoggerFactory.getLogger(MultiInsertExecutor.class);

    private final Map<Integer, PreparedStatement> preparedStatements = new HashMap<>();
    private final Connection connection;
    private final int columnCount;
    private final int maxBatchSize;
    private final String sqlHeader;
    private final String argsStr;

    /**
     * @param connection  The DB Connection
     * @param tableName   The name of the table, case sensitive if applicable
     * @param columnNames List of columns to insert values into, case sensitive if the DB is
     */
    MultiInsertExecutor(final Connection connection,
                        final String tableName,
                        final List<String> columnNames,
                        final int maxBatchSize) {
        this.connection = Preconditions.checkNotNull(connection);
        Preconditions.checkNotNull(tableName);
        Preconditions.checkNotNull(columnNames);
        this.columnCount = columnNames.size();
        this.maxBatchSize = maxBatchSize;

        final String columnNamesStr = String.join(",", columnNames);

        // Build up the sql stmt
        sqlHeader = "INSERT INTO " +
                tableName +
                " (" +
                columnNamesStr +
                ") VALUES ";

        // Build args for one row
        this.argsStr = "(" + columnNames.stream()
                .map(c -> "?")
                .collect(Collectors.joining(",")) + ")";
    }


    /**
     * This method inserts multiple rows into a table, with many rows per statement as controlled
     * by a batch size property. This is to avoid using a hibernate native sql approach that will
     * cache a query plan for each unique query. An insert with two rows is considered different to
     * an insert with three rows so the cache quickly fills up with huge insert queries, each with
     * MANY param objects.
     *
     * @param argsList A List of args (in columnName order), one list of args for each row, will
     *                 be inserted in list order. Each sub list must have the same size as columnNames
     */
    public void execute(final List<List<Object>> argsList) {
        execute(argsList, false);
    }

    /**
     * This method inserts multiple rows into a table, with many rows per statement as controlled
     * by a batch size property. This is to avoid using a hibernate native sql approach that will
     * cache a query plan for each unique query. An insert with two rows is considered different to
     * an insert with three rows so the cache quickly fills up with huge insert queries, each with
     * MANY param objects.
     *
     * @param argsList A List of args (in columnName order), one list of args for each row, will
     *                 be inserted in list order. Each sub list must have the same size as columnNames
     * @return The generated IDs for each row inserted
     */
    public List<Long> executeAndFetchKeys(final List<List<Object>> argsList) {
        return execute(argsList, true);
    }

    private List<Long> execute(final List<List<Object>> argsList,
                               boolean areKeysRequired) {

        final Instant startTime = Instant.now();
        final List<Long> ids;
        if (argsList.size() > 0) {
            validateArgsList(argsList);

            //batch up the inserts
            ids = BatchingIterator.batchedStreamOf(argsList.stream(), maxBatchSize)
                    .flatMap(argsBatch -> {
                        List<Long> batchIds = executeBatch(argsBatch, areKeysRequired);
                        return batchIds.stream();
                    })
                    .collect(Collectors.toList());
        } else {
            ids = Collections.emptyList();
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("execute completed in %s for %s rows",
                    Duration.between(startTime, Instant.now()), argsList.size());
        }
        return ids;
    }

    private PreparedStatement getOrCreatePreparedStatement(final int batchSize,
                                                           final boolean areKeysRequired) {

        PreparedStatement preparedStatement = preparedStatements.get(batchSize);
        if (preparedStatement == null) {
            preparedStatement = createPreparedStatement(batchSize, areKeysRequired);
            preparedStatements.put(batchSize, preparedStatement);
        } else {
            //ensure it is cleaned after last use
            try {
                preparedStatement.clearParameters();
            } catch (SQLException e) {
                throw new RuntimeException("Error clearing parameters on preparedStatement", e);
            }
        }
        return preparedStatement;
    }

    private PreparedStatement createPreparedStatement(final int batchSize,
                                                      final boolean areKeysRequired) {

        //combine all row's args together
        final String argsSection = IntStream.rangeClosed(1, batchSize)
                .boxed()
                .map(i -> argsStr)
                .collect(Collectors.joining(","));

        StringBuilder sql = new StringBuilder(sqlHeader)
                .append(argsSection);

        try {
            if (areKeysRequired) {
                return connection.prepareStatement(sql.toString(), Statement.RETURN_GENERATED_KEYS);
            } else {
                return connection.prepareStatement(sql.toString());
            }
        } catch (SQLException e) {
            throw new RuntimeException(String.format("Error creating prepared statement for sql: %s",
                    sql.toString()), e);
        }
    }

    private List<Long> executeBatch(final List<List<Object>> argsList,
                                    final boolean areKeysRequired) {

        final int batchSize = argsList.size();
        final PreparedStatement preparedStatement = getOrCreatePreparedStatement(
                batchSize,
                areKeysRequired);

        final List<Object> allArgs = argsList.stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        final List<Long> keyList;
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        try {
            PreparedStatementUtil.setArguments(preparedStatement, allArgs);
            final int result = preparedStatement.executeUpdate();

            if (areKeysRequired) {
                keyList = new ArrayList<>();
                try (ResultSet keySet = preparedStatement.getGeneratedKeys()) {
                    while (keySet.next()) {
                        keyList.add(keySet.getLong(1));
                    }
                }
            } else {
                keyList = Collections.emptyList();
            }

            log(logExecutionTime, () -> Integer.toString(result), preparedStatement::toString, allArgs);
            return keyList;

        } catch (final SQLException sqlException) {
            LOGGER.error("executeUpdate() - " + preparedStatement.toString() + " " + allArgs, sqlException);
            throw new RuntimeException(String.format("Error executing preparedStatement: %s",
                    preparedStatement), sqlException);
        }
    }

    private void validateArgsList(final List<List<Object>> argsList) {
        boolean areAllArgsCorrectLength = argsList.stream()
                .allMatch(args -> args.size() == columnCount);

        if (!areAllArgsCorrectLength) {
            String arsSizes = argsList.stream()
                    .map(args -> String.valueOf(args.size()))
                    .distinct()
                    .collect(Collectors.joining(","));

            throw new RuntimeException(String.format("Not all args match the number of columns [%s], distinct args counts: [%s]",
                    columnCount, arsSizes));
        }
    }

    @Override
    public void close() {
        final boolean allClosedWithoutError = preparedStatements.values().stream()
                .filter(Objects::nonNull)
                .allMatch(stmt -> {
                    try {
                        stmt.close();
                    } catch (SQLException e) {
                        LOGGER.error(String.format("Error closing prepareStatement %s", stmt), e);
                        //swallow exception so we can keep closing statements (we will handle it later)
                        return false;
                    }
                    return true;
                });
        if (!allClosedWithoutError) {
            throw new RuntimeException("Error closing prepareStatements, see ERRORs in logs");
        }
    }

    private void log(final LogExecutionTime logExecutionTime,
                     final Supplier<String> resultSupplier,
                     final Supplier<String> sqlSupplier,
                     final List<Object> args) {
        final long time = logExecutionTime.getDuration();
        if (LOGGER.isDebugEnabled() || time > 1000) {
            final String message = "<<< " + sqlSupplier.get() + " " + args + " took " + ModelStringUtil.formatDurationString(time)
                    + " with result " + resultSupplier.get();
            if (time > 1000) {
                LOGGER.warn(message);
            } else {
                LOGGER.debug(message);
            }
        }
    }
}