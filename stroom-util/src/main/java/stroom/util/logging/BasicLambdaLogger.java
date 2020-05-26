package stroom.util.logging;

import org.slf4j.Logger;
import org.slf4j.Marker;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;
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
        } catch (final RuntimeException e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void trace(final Supplier<String> message, final Throwable t) {
        try {
            if (logger.isTraceEnabled()) {
                logger.trace(message.get(), t);
            }
        } catch (final RuntimeException e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void debug(final Supplier<String> message) {
        try {
            if (logger.isDebugEnabled()) {
                String msg = message.get();
                if (msg != null) {
                    logger.debug(msg);
                }
            }
        } catch (final RuntimeException e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void debug(final Supplier<String> message, final Throwable t) {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug(message.get(), t);
            }
        } catch (final RuntimeException e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void info(final Supplier<String> message) {
        try {
            if (logger.isInfoEnabled()) {
                logger.info(message.get());
            }
        } catch (final RuntimeException e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void info(final Supplier<String> message, final Throwable t) {
        try {
            if (logger.isInfoEnabled()) {
                logger.info(message.get(), t);
            }
        } catch (final RuntimeException e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void warn(final Supplier<String> message) {
        try {
            if (logger.isWarnEnabled()) {
                logger.warn(message.get());
            }
        } catch (final RuntimeException e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void warn(final Supplier<String> message, final Throwable t) {
        try {
            if (logger.isWarnEnabled()) {
                logger.warn(message.get(), t);
            }
        } catch (final RuntimeException e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void error(final Supplier<String> message) {
        try {
            if (logger.isErrorEnabled()) {
                logger.error(message.get());
            }
        } catch (final RuntimeException e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void error(final Supplier<String> message, final Throwable t) {
        try {
            if (logger.isErrorEnabled()) {
                logger.error(message.get(), t);
            }
        } catch (final RuntimeException e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    @Override
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    @Override
    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    @Override
    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }

    @Override
    public <T> T logDurationIfTraceEnabled(final Supplier<T> timedWork,
                                           final Supplier<String> workDescriptionSupplier) {
        if (logger.isTraceEnabled()) {
            final Instant startTime = Instant.now();
            T result = timedWork.get();
            try {
                logger.trace("Completed [{}] in {}", workDescriptionSupplier.get(), Duration.between(startTime, Instant.now()));
            } catch (final RuntimeException e) {
                logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
            }
            return result;
        } else {
            return timedWork.get();
        }
    }

    @Override
    public <T> T logDurationIfTraceEnabled(final Supplier<T> timedWork,
                                           final Function<T, String> workDescriptionFunction) {
        if (logger.isTraceEnabled()) {
            final Instant startTime = Instant.now();
            T result = timedWork.get();
            try {
                logger.trace("Completed [{}] in {}",
                        workDescriptionFunction.apply(result),
                        Duration.between(startTime, Instant.now()));
            } catch (final RuntimeException e) {
                logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
            }
            return result;
        } else {
            return timedWork.get();
        }
    }

    @Override
    public <T> T logDurationIfDebugEnabled(final Supplier<T> timedWork,
                                           final Supplier<String> workDescriptionSupplier) {
        if (logger.isDebugEnabled()) {
            final Instant startTime = Instant.now();
            T result = timedWork.get();
            try {
                logger.debug("Completed [{}] in {}",
                        workDescriptionSupplier.get(),
                        Duration.between(startTime, Instant.now()));
            } catch (final RuntimeException e) {
                logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
            }
            return result;
        } else {
            return timedWork.get();
        }
    }

    @Override
    public <T> T logDurationIfDebugEnabled(final Supplier<T> timedWork,
                                           final Function<T, String> workDescriptionFunction) {
        if (logger.isDebugEnabled()) {
            final Instant startTime = Instant.now();
            T result = timedWork.get();
            try {
                logger.debug("Completed [{}] in {}",
                        workDescriptionFunction.apply(result),
                        Duration.between(startTime, Instant.now()));
            } catch (final RuntimeException e) {
                logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
            }
            return result;
        } else {
            return timedWork.get();
        }
    }

    @Override
    public <T> T logDurationIfInfoEnabled(final Supplier<T> timedWork,
                                          final Supplier<String> workDescriptionSupplier) {
        if (logger.isInfoEnabled()) {
            final Instant startTime = Instant.now();
            T result = timedWork.get();
            try {
                logger.info("Completed [{}] in {}",
                        workDescriptionSupplier.get(),
                        Duration.between(startTime, Instant.now()));
            } catch (final RuntimeException e) {
                logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
            }
            return result;
        } else {
            return timedWork.get();
        }
    }

    @Override
    public <T> T logDurationIfInfoEnabled(final Supplier<T> timedWork,
                                          final Function<T, String> workDescriptionFunction) {
        if (logger.isInfoEnabled()) {
            final Instant startTime = Instant.now();
            T result = timedWork.get();
            try {
                logger.info("Completed [{}] in {}",
                        workDescriptionFunction.apply(result),
                        Duration.between(startTime, Instant.now()));
            } catch (final RuntimeException e) {
                logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
            }
            return result;
        } else {
            return timedWork.get();
        }
    }

    @Override
    public void logDurationIfTraceEnabled(final Runnable timedWork,
                                          final Supplier<String> workDescriptionSupplier) {
        if (logger.isTraceEnabled()) {
            final Instant startTime = Instant.now();
            timedWork.run();
            try {
                logger.trace("Completed [{}] in {}",
                        workDescriptionSupplier.get(),
                        Duration.between(startTime, Instant.now()));
            } catch (final RuntimeException e) {
                logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
            }
        } else {
            timedWork.run();
        }
    }

    @Override
    public void logDurationIfDebugEnabled(final Runnable timedWork,
                                          final Supplier<String> workDescriptionSupplier) {
        if (logger.isDebugEnabled()) {
            final Instant startTime = Instant.now();
            timedWork.run();
            try {
                logger.debug("Completed [{}] in {}",
                        workDescriptionSupplier.get(),
                        Duration.between(startTime, Instant.now()));
            } catch (final RuntimeException e) {
                logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
            }
        } else {
            timedWork.run();
        }
    }

    @Override
    public void logDurationIfInfoEnabled(final Runnable timedWork,
                                         final Supplier<String> workDescriptionSupplier) {
        if (logger.isInfoEnabled()) {
            final Instant startTime = Instant.now();
            timedWork.run();
            try {
                logger.info("Completed [{}] in {}",
                        workDescriptionSupplier.get(),
                        Duration.between(startTime, Instant.now()));
            } catch (final RuntimeException e) {
                logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
            }
        } else {
            timedWork.run();
        }
    }

    @Override
    public void doIfTraceEnabled(final Runnable work) {
        if (logger.isTraceEnabled()) {
            work.run();
        }
    }

    @Override
    public void doIfDebugEnabled(final Runnable work) {
        if (logger.isDebugEnabled()) {
            work.run();
        }
    }

    @Override
    public void doIfInfoEnabled(final Runnable work) {
        if (logger.isInfoEnabled()) {
            work.run();
        }
    }

    public String getName() {
        return logger.getName();
    }

    public void trace(final String msg) {
        logger.trace(msg);
    }

    public void trace(final String format, final Object arg) {
        logger.trace(format, arg);
    }

    public void trace(final String format, final Object arg1, final Object arg2) {
        logger.trace(format, arg1, arg2);
    }

    public void trace(final String format, final Object... arguments) {
        logger.trace(format, arguments);
    }

    public void trace(final String msg, final Throwable t) {
        logger.trace(msg, t);
    }

    public boolean isTraceEnabled(final Marker marker) {
        return logger.isTraceEnabled(marker);
    }

    public void trace(final Marker marker, final String msg) {
        logger.trace(marker, msg);
    }

    public void trace(final Marker marker, final String format, final Object arg) {
        logger.trace(marker, format, arg);
    }

    public void trace(final Marker marker, final String format, final Object arg1, final Object arg2) {
        logger.trace(marker, format, arg1, arg2);
    }

    public void trace(final Marker marker, final String format, final Object... argArray) {
        logger.trace(marker, format, argArray);
    }

    public void trace(final Marker marker, final String msg, final Throwable t) {
        logger.trace(marker, msg, t);
    }

    public void debug(final String msg) {
        logger.debug(msg);
    }

    public void debug(final String format, final Object arg) {
        logger.debug(format, arg);
    }

    public void debug(final String format, final Object arg1, final Object arg2) {
        logger.debug(format, arg1, arg2);
    }

    public void debug(final String format, final Object... arguments) {
        logger.debug(format, arguments);
    }

    public void debug(final String msg, final Throwable t) {
        logger.debug(msg, t);
    }

    public boolean isDebugEnabled(final Marker marker) {
        return logger.isDebugEnabled(marker);
    }

    public void debug(final Marker marker, final String msg) {
        logger.debug(marker, msg);
    }

    public void debug(final Marker marker, final String format, final Object arg) {
        logger.debug(marker, format, arg);
    }

    public void debug(final Marker marker, final String format, final Object arg1, final Object arg2) {
        logger.debug(marker, format, arg1, arg2);
    }

    public void debug(final Marker marker, final String format, final Object... arguments) {
        logger.debug(marker, format, arguments);
    }

    public void debug(final Marker marker, final String msg, final Throwable t) {
        logger.debug(marker, msg, t);
    }

    public void info(final String msg) {
        logger.info(msg);
    }

    public void info(final String format, final Object arg) {
        logger.info(format, arg);
    }

    public void info(final String format, final Object arg1, final Object arg2) {
        logger.info(format, arg1, arg2);
    }

    public void info(final String format, final Object... arguments) {
        logger.info(format, arguments);
    }

    public void info(final String msg, final Throwable t) {
        logger.info(msg, t);
    }

    public boolean isInfoEnabled(final Marker marker) {
        return logger.isInfoEnabled(marker);
    }

    public void info(final Marker marker, final String msg) {
        logger.info(marker, msg);
    }

    public void info(final Marker marker, final String format, final Object arg) {
        logger.info(marker, format, arg);
    }

    public void info(final Marker marker, final String format, final Object arg1, final Object arg2) {
        logger.info(marker, format, arg1, arg2);
    }

    public void info(final Marker marker, final String format, final Object... arguments) {
        logger.info(marker, format, arguments);
    }

    public void info(final Marker marker, final String msg, final Throwable t) {
        logger.info(marker, msg, t);
    }

    public void warn(final String msg) {
        logger.warn(msg);
    }

    public void warn(final String format, final Object arg) {
        logger.warn(format, arg);
    }

    public void warn(final String format, final Object... arguments) {
        logger.warn(format, arguments);
    }

    public void warn(final String format, final Object arg1, final Object arg2) {
        logger.warn(format, arg1, arg2);
    }

    public void warn(final String msg, final Throwable t) {
        logger.warn(msg, t);
    }

    public boolean isWarnEnabled(final Marker marker) {
        return logger.isWarnEnabled(marker);
    }

    public void warn(final Marker marker, final String msg) {
        logger.warn(marker, msg);
    }

    public void warn(final Marker marker, final String format, final Object arg) {
        logger.warn(marker, format, arg);
    }

    public void warn(final Marker marker, final String format, final Object arg1, final Object arg2) {
        logger.warn(marker, format, arg1, arg2);
    }

    public void warn(final Marker marker, final String format, final Object... arguments) {
        logger.warn(marker, format, arguments);
    }

    public void warn(final Marker marker, final String msg, final Throwable t) {
        logger.warn(marker, msg, t);
    }

    public void error(final String msg) {
        logger.error(msg);
    }

    public void error(final String format, final Object arg) {
        logger.error(format, arg);
    }

    public void error(final String format, final Object arg1, final Object arg2) {
        logger.error(format, arg1, arg2);
    }

    public void error(final String format, final Object... arguments) {
        logger.error(format, arguments);
    }

    public void error(final String msg, final Throwable t) {
        logger.error(msg, t);
    }

    public boolean isErrorEnabled(final Marker marker) {
        return logger.isErrorEnabled(marker);
    }

    public void error(final Marker marker, final String msg) {
        logger.error(marker, msg);
    }

    public void error(final Marker marker, final String format, final Object arg) {
        logger.error(marker, format, arg);
    }

    public void error(final Marker marker, final String format, final Object arg1, final Object arg2) {
        logger.error(marker, format, arg1, arg2);
    }

    public void error(final Marker marker, final String format, final Object... arguments) {
        logger.error(marker, format, arguments);
    }

    public void error(final Marker marker, final String msg, final Throwable t) {
        logger.error(marker, msg, t);
    }
}
