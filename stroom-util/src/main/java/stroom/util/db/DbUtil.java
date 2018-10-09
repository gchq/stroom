package stroom.util.db;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.thread.ThreadUtil;

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
     * @return True if the connection is establish or false if the connection could not be established
     * and the reason for the failure makes a retry pointless, e.g. invalid password.
     */
    public static boolean waitForConnection(
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
        boolean result = true;

        while (true) {
            try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
                LOGGER.info("Successfully established connection to {} with username {}", jdbcUrl, username);
                break;
            } catch (SQLException e) {
                if (e.getErrorCode() == ACCESS_DENIED_BAD_UNAME_OR_PWORD ||
                        e.getErrorCode() == ACCESS_DENIED_BAD_DATABASE ||
                        (e.getMessage() != null && e.getMessage().startsWith("Unsupported"))) {

                    // These errors are not due to the DB not being up, so no point retrying
                    LOGGER.error("Error connecting to {} with username {}", jdbcUrl, username, e);
                    result = false;
                    break;
                }
                final Throwable cause = e.getCause();
                LOGGER.warn("Unable to establish database connection due to error: [{}], will try again in {}ms, enable debug to see stack trace",
                        cause != null ? cause.getMessage() : e.getMessage(), sleepMs);
                if (LOGGER.isDebugEnabled()) {
                    if (lastThrowable == null || !e.getMessage().equals(lastThrowable.getMessage())) {
                        // Only log the stack when it changes, else it fills up the log pretty quickly
                        LOGGER.debug("Unable to establish database connection due to error", e);
                    }
                }
                lastThrowable = e;
            }
            ThreadUtil.sleep(sleepMs);

            // Gradually increase the sleep time up to a maximum
            sleepMs = (long) (sleepMs * 1.3);
            if (sleepMs >= MAX_SLEEP_TIME_MS) {
                sleepMs = MAX_SLEEP_TIME_MS;
            }
        }
        return result;
    }
}
