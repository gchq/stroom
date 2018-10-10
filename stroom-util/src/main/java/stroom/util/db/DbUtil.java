package stroom.util.db;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.logging.LambdaLogger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DbUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(DbUtil.class);
    private static final long MAX_SLEEP_TIME_MS = 30_000;
    private static final int ACCESS_DENIED_BAD_UNAME_OR_PWORD = 1045;
    private static final int ACCESS_DENIED_BAD_DATABASE = 1044;

    private DbUtil() {
    }

    /**
     * Attempts to connect to the database using the passed connection details. If it fails
     * it will log a warning, and keep retrying. The retry interval will steadily increase.
     *
     * If the connection could not be established and the reason for the failure makes a
     * retry pointless, e.g. invalid password, then an exception will be thrown.
     */
    public static void waitForConnection(
            final String driverClass,
            final String jdbcUrl,
            final String username,
            final String password) {

        Preconditions.checkNotNull(driverClass);
        Preconditions.checkNotNull(jdbcUrl);
        Preconditions.checkNotNull(username);
        Preconditions.checkNotNull(password);

        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(LambdaLogger.buildMessage(
                    "Invalid JDBC driver class name {}", driverClass), e);
        }

        LOGGER.info("Ensuring database connection to {} with username {}", jdbcUrl, username);

        long sleepMs = 100;
        Throwable lastThrowable = null;

        while (true) {
            try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
                LOGGER.info("Successfully established connection to {} with username {}", jdbcUrl, username);
                break;
            } catch (SQLException e) {
                if (e.getErrorCode() == ACCESS_DENIED_BAD_UNAME_OR_PWORD ||
                        e.getErrorCode() == ACCESS_DENIED_BAD_DATABASE ||
                        (e.getMessage() != null && e.getMessage().startsWith("Unsupported"))) {

                    // These errors are not due to the DB not being up, so throw it
                    throw new RuntimeException(LambdaLogger.buildMessage(
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
}
