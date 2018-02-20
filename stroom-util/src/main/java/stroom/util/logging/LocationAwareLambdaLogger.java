package stroom.util.logging;

import org.slf4j.spi.LocationAwareLogger;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

public final class LocationAwareLambdaLogger implements LambdaLogger {

    private static final String FQCN = LocationAwareLambdaLogger.class.getName();
    private final LocationAwareLogger logger;

    // Use a private constructor as this is only made via the static factory.
    LocationAwareLambdaLogger(final LocationAwareLogger logger) {
        this.logger = logger;
    }

    @Override
    public void trace(final Supplier<String> message) {
        trace(message, null);
    }

    @Override
    public void trace(final Supplier<String> message, final Throwable t) {
        if (logger.isTraceEnabled()) {
            log(LocationAwareLogger.TRACE_INT, message, t);
        }
    }

    @Override
    public void debug(final Supplier<String> message) {
        debug(message, null);
    }

    @Override
    public void debug(final Supplier<String> message, final Throwable t) {
        if (logger.isDebugEnabled()) {
            log(LocationAwareLogger.DEBUG_INT, message, t);
        }
    }

    @Override
    public void info(final Supplier<String> message) {
        info(message, null);
    }

    @Override
    public void info(final Supplier<String> message, final Throwable t) {
        if (logger.isInfoEnabled()) {
            log(LocationAwareLogger.INFO_INT, message, t);
        }
    }

    @Override
    public void warn(final Supplier<String> message) {
        warn(message, null);
    }

    @Override
    public void warn(final Supplier<String> message, final Throwable t) {
        if (logger.isWarnEnabled()) {
            log(LocationAwareLogger.WARN_INT, message, t);
        }
    }

    @Override
    public void error(final Supplier<String> message) {
        error(message, null);
    }

    @Override
    public void error(final Supplier<String> message, final Throwable t) {
        if (logger.isErrorEnabled()) {
            log(LocationAwareLogger.ERROR_INT, message, t);
        }
    }

    @Override
    public <T> T logDurationIfTraceEnabled(final Supplier<T> timedWork, final String workDescription) {
        if (logger.isTraceEnabled()) {
            final Instant startTime = Instant.now();
            T result = timedWork.get();
            log(LocationAwareLogger.TRACE_INT,
                    () -> LambdaLogger.buildMessage(
                            "Completed [{}] in {}",
                            workDescription,
                            Duration.between(startTime, Instant.now())),
                    null);
            return result;
        } else {
            return timedWork.get();
        }
    }

    @Override
    public <T> T logDurationIfDebugEnabled(final Supplier<T> timedWork, final String workDescription) {
        if (logger.isDebugEnabled()) {
            final Instant startTime = Instant.now();
            T result = timedWork.get();
            log(LocationAwareLogger.DEBUG_INT,
                    () -> LambdaLogger.buildMessage(
                            "Completed [{}] in {}",
                            workDescription,
                            Duration.between(startTime, Instant.now())),
                    null);
            return result;
        } else {
            return timedWork.get();
        }
    }

    @Override
    public <T> T logDurationIfInfoEnabled(final Supplier<T> timedWork, final String workDescription) {
        if (logger.isInfoEnabled()) {
            final Instant startTime = Instant.now();
            T result = timedWork.get();
            log(LocationAwareLogger.INFO_INT,
                    () -> LambdaLogger.buildMessage(
                            "Completed [{}] in {}",
                            workDescription,
                            Duration.between(startTime, Instant.now())),
                    null);
            return result;
        } else {
            return timedWork.get();
        }
    }

    private void log(final int severity, final Supplier<String> message, final Throwable t) {
        try {
            logger.log(null, FQCN, severity, message.get(), null, t);
        } catch (final Exception e) {
            try {
                logger.log(null, FQCN, LocationAwareLogger.ERROR_INT, "ERROR LOGGING MESSAGE - " + e.getMessage(), null, e);
            } catch (final Exception e2) {
                // Ignore.
            }
        }
    }
}
