package stroom.db.util;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.common.ConnectionConfig;
import stroom.util.logging.LogUtil;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DbUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(DbUtil.class);
    private static final boolean USE_TEST_CONTAINERS = true;
    private static final long MAX_SLEEP_TIME_MS = 30_000;
    private static final int ACCESS_DENIED_BAD_UNAME_OR_PWORD = 1045;
    private static final int ACCESS_DENIED_BAD_DATABASE = 1044;

    private DbUtil() {
    }

    public static void decorateConnectionConfig(final ConnectionConfig connectionConfig) {
        if (USE_TEST_CONTAINERS) {
            try {
                final Class<?> clazz = Class.forName("org.testcontainers.jdbc.ContainerDatabaseDriver");
                if (clazz != null) {
                    LOGGER.info("Using test container DB connection config");

                    connectionConfig.setJdbcDriverClassName("com.mysql.jdbc.Driver");
//                        .jdbcUrl("jdbc:tc:mysql:5.6.23://localhost:3306/stroom?user=test?password=test")
                    connectionConfig.setJdbcDriverUrl("jdbc:tc:mysql:5.6.23://localhost:3306/test");
                    connectionConfig.setJdbcDriverPassword("test");
                    connectionConfig.setJdbcDriverUsername("test");

                }
            } catch (final ClassNotFoundException e) {
                LOGGER.debug("Not using test container DB connection config");
            }
        } else {
            LOGGER.debug("Not using test container DB connection config");
        }
    }

    public static void validate(final ConnectionConfig connectionConfig) {
        Preconditions.checkNotNull(connectionConfig.getJdbcDriverClassName(), "The JDBC driver class has not been supplied");
        Preconditions.checkNotNull(connectionConfig.getJdbcDriverUrl(), "The JDBC URL has not been supplied");
        Preconditions.checkNotNull(connectionConfig.getJdbcDriverUsername(), "The JDBC username has not been supplied");
        Preconditions.checkNotNull(connectionConfig.getJdbcDriverPassword(), "The JDBC password has not been supplied");

        try {
            Class.forName(connectionConfig.getJdbcDriverClassName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(LogUtil.message(
                    "Invalid JDBC driver class name {}", connectionConfig.getJdbcDriverClassName()), e);
        }
    }

    public static Connection getSingleConnection(final ConnectionConfig connectionConfig) throws SQLException {
        return DriverManager.getConnection(
                connectionConfig.getJdbcDriverUrl(),
                connectionConfig.getJdbcDriverUsername(),
                connectionConfig.getJdbcDriverPassword());
    }

    /**
     * Attempts to connect to the database using the passed connection details. If it fails
     * it will log a warning, and keep retrying. The retry interval will steadily increase.
     * <p>
     * If the connection could not be established and the reason for the failure makes a
     * retry pointless, e.g. invalid password, then an exception will be thrown.
     */
    public static void waitForConnection(ConnectionConfig connectionConfig) {
        final String jdbcUrl = connectionConfig.getJdbcDriverUrl();
        final String username = connectionConfig.getJdbcDriverUsername();
        LOGGER.info("Ensuring database connection to {} with username {}", jdbcUrl, username);

        long sleepMs = 100;
        Throwable lastThrowable = null;

        while (true) {
            try (Connection connection = getSingleConnection(connectionConfig)) {
                LOGGER.info("Successfully established connection to {} with username {}", jdbcUrl, username);
                break;
            } catch (SQLException e) {
                if (e.getErrorCode() == ACCESS_DENIED_BAD_UNAME_OR_PWORD ||
                        e.getErrorCode() == ACCESS_DENIED_BAD_DATABASE ||
                        (e.getMessage() != null && e.getMessage().startsWith("Unsupported"))) {

                    // These errors are not due to the DB not being up, so throw it
                    throw new RuntimeException(LogUtil.message(
                            "Error connecting to {} with username {}", jdbcUrl, username), e);
                }
                final Throwable cause = e.getCause();
                final String errorMsg = cause != null ? cause.getMessage() : e.getMessage();
                final int vendorCode = e.getErrorCode();
                LOGGER.warn("Unable to establish database connection due to error: [{}] " +
                                "and vendorCode [{}], will try again " +
                                "in {}ms, enable debug to see stack trace",
                        errorMsg, vendorCode, sleepMs);
                if (LOGGER.isDebugEnabled()) {
                    if (lastThrowable == null || !e.getMessage().equals(lastThrowable.getMessage())) {
                        // Only log the stack when it changes, else it fills up the log pretty quickly
                        LOGGER.debug("Unable to establish database connection due to error", e);
                    }
                    lastThrowable = e;
                }
            }
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                // Nothing to do here as the
                Thread.currentThread().interrupt();
                break;
            }

            // Gradually increase the sleep time up to a maximum
            sleepMs = (long) (sleepMs * 1.3);
            if (sleepMs >= MAX_SLEEP_TIME_MS) {
                sleepMs = MAX_SLEEP_TIME_MS;
            }
        }
    }

    public static int countEntity(final Connection connection, final String tableName) {
        try (final Statement statement = connection.createStatement()) {
            try (final ResultSet resultSet = statement.executeQuery("SELECT count(*) FROM " + tableName)) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        return 0;
    }

    public static void clearAllTables(final Connection connection) {
        final List<String> tables = new ArrayList<>();

        boolean seenInnodb = false;
        try (final PreparedStatement statement = connection.prepareStatement("SELECT table_name FROM information_schema.tables;")) {
            try (final ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final String name = resultSet.getString(1);
                    if (name.toLowerCase().contains("innodb")) {
                        seenInnodb = true;
                    } else if (seenInnodb && !name.contains("schema")) {
                        tables.add(name);
                    }
                }
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        clearTables(connection, tables);
    }

    private static void clearTables(final Connection connection, final List<String> tableNames) {
        List<String> deleteStatements = tableNames.stream()
                .map(tableName -> "DELETE FROM " + tableName)
                .collect(Collectors.toList());

        executeStatementsWithNoConstraints(connection, deleteStatements);
    }

    private static void executeStatementsWithNoConstraints(final Connection connection, final List<String> statements) {
        final List<String> allStatements = new ArrayList<>();
        allStatements.add("SET FOREIGN_KEY_CHECKS=0");
        allStatements.addAll(statements);
        allStatements.add("SET FOREIGN_KEY_CHECKS=1");

        executeStatements(connection, allStatements);
    }

//    private void executeStatement(final Connection connection, final String sql) throws SQLException {
//        executeStatements(connection, Collections.singletonList(sql));
//    }

    private static void executeStatements(final Connection connection, final List<String> sqlStatements) {
        LOGGER.debug(">>> %s", sqlStatements);
//        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        try (final Statement statement = connection.createStatement()) {
            sqlStatements.forEach(sql -> {
                try {
                    statement.addBatch(sql);
                } catch (SQLException e) {
                    throw new RuntimeException(String.format("Error adding sql [%s] to batch", sql), e);
                }
            });
            int[] results = statement.executeBatch();
            boolean isFailure = Arrays.stream(results)
                    .anyMatch(val -> val == Statement.EXECUTE_FAILED);

            if (isFailure) {
                throw new RuntimeException(String.format("Got error code for batch %s", sqlStatements));
            }

//            log(logExecutionTime,
//                    () -> Arrays.stream(results)
//                            .mapToObj(Integer::toString)
//                            .collect(Collectors.joining(",")),
//                    sqlStatements::toString,
//                    Collections.emptyList());

        } catch (final SQLException e) {
            LOGGER.error("executeStatement() - " + sqlStatements, e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
