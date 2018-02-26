package stroom.util.logging;

import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

public final class BasicLambdaLogger implements LambdaLogger {
    private final Logger logger;

    // Use a private constructor as this is only made via the static factory.
    BasicLambdaLogger(final Logger logger) {
        this.logger = logger;
    }

    @Override
    public void trace(final Supplier<String> message) {
        try {
            if (logger.isTraceEnabled()) {
                logger.trace(message.get());
            }
        } catch (final Exception e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void trace(final Supplier<String> message, final Throwable t) {
        try {
            if (logger.isTraceEnabled()) {
                logger.trace(message.get(), t);
            }
        } catch (final Exception e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void debug(final Supplier<String> message) {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug(message.get());
            }
        } catch (final Exception e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void debug(final Supplier<String> message, final Throwable t) {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug(message.get(), t);
            }
        } catch (final Exception e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void info(final Supplier<String> message) {
        try {
            if (logger.isInfoEnabled()) {
                logger.info(message.get());
            }
        } catch (final Exception e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void info(final Supplier<String> message, final Throwable t) {
        try {
            if (logger.isInfoEnabled()) {
                logger.info(message.get(), t);
            }
        } catch (final Exception e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void warn(final Supplier<String> message) {
        try {
            if (logger.isWarnEnabled()) {
                logger.warn(message.get());
            }
        } catch (final Exception e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void warn(final Supplier<String> message, final Throwable t) {
        try {
            if (logger.isWarnEnabled()) {
                logger.warn(message.get(), t);
            }
        } catch (final Exception e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void error(final Supplier<String> message) {
        try {
            if (logger.isErrorEnabled()) {
                logger.error(message.get());
            }
        } catch (final Exception e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void error(final Supplier<String> message, final Throwable t) {
        try {
            if (logger.isErrorEnabled()) {
                logger.error(message.get(), t);
            }
        } catch (final Exception e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public <T> T logDurationIfTraceEnabled(final Supplier<T> timedWork, final String workDescription) {
        if (logger.isTraceEnabled()) {
            final Instant startTime = Instant.now();
            T result = timedWork.get();
            logger.trace("Completed [{}] in {}", workDescription, Duration.between(startTime, Instant.now()));
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
            logger.debug("Completed [{}] in {}", workDescription, Duration.between(startTime, Instant.now()));
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
            logger.info("Completed [{}] in {}", workDescription, Duration.between(startTime, Instant.now()));
            return result;
        } else {
            return timedWork.get();
        }
    }
}
