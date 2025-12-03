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

package stroom.util.logging;

import org.slf4j.Marker;
import org.slf4j.spi.LocationAwareLogger;

import java.time.Duration;
import java.util.function.Function;
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
    public void log(final LogLevel logLevel, final String message) {
        try {
            switch (logLevel) {
                case TRACE -> trace(message);
                case DEBUG -> debug(message);
                case INFO -> info(message);
                case WARN -> warn(message);
                case ERROR -> error(message);
                default -> error("Unexpected logLevel: {}", logLevel);
            }
        } catch (final Exception e) {
            error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void log(final LogLevel logLevel, final String format, final Object arg) {
        try {
            switch (logLevel) {
                case TRACE -> trace(format, arg);
                case DEBUG -> debug(format, arg);
                case INFO -> info(format, arg);
                case WARN -> warn(format, arg);
                case ERROR -> error(format, arg);
                default -> error("Unexpected logLevel: {}", logLevel);
            }
        } catch (final Exception e) {
            error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void log(final LogLevel logLevel,
                    final String format,
                    final Object arg1,
                    final Object arg2) {
        try {
            switch (logLevel) {
                case TRACE -> trace(format, arg1, arg2);
                case DEBUG -> debug(format, arg1, arg2);
                case INFO -> info(format, arg1, arg2);
                case WARN -> warn(format, arg1, arg2);
                case ERROR -> error(format, arg1, arg2);
                default -> error("Unexpected logLevel: {}", logLevel);
            }
        } catch (final Exception e) {
            error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void log(final LogLevel logLevel, final String format, final Object... args) {
        try {
            switch (logLevel) {
                case TRACE -> trace(format, args);
                case DEBUG -> debug(format, args);
                case INFO -> info(format, args);
                case WARN -> warn(format, args);
                case ERROR -> error(format, args);
                default -> error("Unexpected logLevel: {}", logLevel);
            }
        } catch (final Exception e) {
            error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void log(final LogLevel logLevel, final Supplier<String> messageSupplier) {
        try {
            switch (logLevel) {
                case TRACE -> {
                    if (isTraceEnabled()) {
                        trace(getSafeMessage(messageSupplier));
                    }
                }
                case DEBUG -> {
                    if (isDebugEnabled()) {
                        debug(getSafeMessage(messageSupplier));
                    }
                }
                case INFO -> {
                    if (isInfoEnabled()) {
                        info(getSafeMessage(messageSupplier));
                    }
                }
                case WARN -> {
                    if (isWarnEnabled()) {
                        warn(getSafeMessage(messageSupplier));
                    }
                }
                case ERROR -> {
                    if (isErrorEnabled()) {
                        error(getSafeMessage(messageSupplier));
                    }
                }
                default -> error("Unexpected logLevel: {}", logLevel);
            }
        } catch (final Exception e) {
            error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void log(final LogLevel logLevel,
                    final Supplier<String> messageSupplier,
                    final Throwable t) {
        try {
            switch (logLevel) {
                case TRACE -> {
                    if (isTraceEnabled()) {
                        trace(getSafeMessage(messageSupplier), t);
                    }
                }
                case DEBUG -> {
                    if (isDebugEnabled()) {
                        debug(getSafeMessage(messageSupplier), t);
                    }
                }
                case INFO -> {
                    if (isInfoEnabled()) {
                        info(getSafeMessage(messageSupplier), t);
                    }
                }
                case WARN -> {
                    if (isWarnEnabled()) {
                        warn(getSafeMessage(messageSupplier), t);
                    }
                }
                case ERROR -> {
                    if (isErrorEnabled()) {
                        error(getSafeMessage(messageSupplier), t);
                    }
                }
                default -> error("Unexpected logLevel: {}", logLevel);
            }
        } catch (final Exception e) {
            error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
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
    public boolean isEnabled(final LogLevel logLevel) {
        try {
            return switch (logLevel) {
                case TRACE -> logger.isTraceEnabled();
                case DEBUG -> logger.isDebugEnabled();
                case INFO -> logger.isInfoEnabled();
                case WARN -> logger.isWarnEnabled();
                case ERROR -> logger.isErrorEnabled();
                default -> throw new RuntimeException("Unexpected logLevel: " + logLevel);
            };
        } catch (final Exception e) {
            error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
            return false;
        }
    }

    @Override
    public <T> T logDurationIfTraceEnabled(final Supplier<T> timedWork,
                                           final Supplier<String> workDescriptionSupplier) {
        if (logger.isTraceEnabled()) {
            final DurationTimer durationTimer = DurationTimer.start();
            final T result = timedWork.get();
            final Duration duration = durationTimer.get();
            log(LocationAwareLogger.TRACE_INT,
                    () -> LogUtil.getDurationMessage(getSafeMessage(workDescriptionSupplier), duration),
                    null);
            return result;
        } else {
            return timedWork.get();
        }
    }

    @Override
    public <T> T logDurationIfTraceEnabled(final Supplier<T> timedWork,
                                           final Function<T, String> workDescriptionFunction) {
        if (logger.isTraceEnabled()) {
            final DurationTimer durationTimer = DurationTimer.start();
            final T result = timedWork.get();
            final Duration duration = durationTimer.get();
            log(LocationAwareLogger.TRACE_INT,
                    () -> LogUtil.getDurationMessage(getSafeMessage(workDescriptionFunction, result), duration),
                    null);
            return result;
        } else {
            return timedWork.get();
        }
    }

    @Override
    public <T> T logDurationIfDebugEnabled(final Supplier<T> timedWork,
                                           final Supplier<String> workDescriptionSupplier) {
        if (logger.isDebugEnabled()) {
            final DurationTimer durationTimer = DurationTimer.start();
            final T result = timedWork.get();
            final Duration duration = durationTimer.get();
            log(LocationAwareLogger.DEBUG_INT,
                    () -> LogUtil.getDurationMessage(getSafeMessage(workDescriptionSupplier), duration),
                    null);
            return result;
        } else {
            return timedWork.get();
        }
    }

    @Override
    public <T> T logDurationIfDebugEnabled(final Supplier<T> timedWork,
                                           final Function<T, String> workDescriptionFunction) {
        if (logger.isDebugEnabled()) {
            final DurationTimer durationTimer = DurationTimer.start();
            final T result = timedWork.get();
            final Duration duration = durationTimer.get();
            log(LocationAwareLogger.DEBUG_INT,
                    () -> LogUtil.getDurationMessage(getSafeMessage(workDescriptionFunction, result), duration),
                    null);
            return result;
        } else {
            return timedWork.get();
        }
    }

    @Override
    public <T> T logDurationIfInfoEnabled(final Supplier<T> timedWork, final Supplier<String> workDescriptionSupplier) {
        if (logger.isInfoEnabled()) {
            final DurationTimer durationTimer = DurationTimer.start();
            final T result = timedWork.get();
            final Duration duration = durationTimer.get();
            log(LocationAwareLogger.INFO_INT,
                    () -> LogUtil.getDurationMessage(getSafeMessage(workDescriptionSupplier), duration),
                    null);
            return result;
        } else {
            return timedWork.get();
        }
    }

    @Override
    public <T> T logDurationIfInfoEnabled(final Supplier<T> timedWork,
                                          final Function<T, String> workDescriptionFunction) {
        if (logger.isInfoEnabled()) {
            final DurationTimer durationTimer = DurationTimer.start();
            final T result = timedWork.get();
            final Duration duration = durationTimer.get();
            log(LocationAwareLogger.INFO_INT,
                    () -> LogUtil.getDurationMessage(getSafeMessage(workDescriptionFunction, result), duration),
                    null);
            return result;
        } else {
            return timedWork.get();
        }
    }

    @Override
    public void logDurationIfTraceEnabled(final Runnable timedWork, final Supplier<String> workDescriptionSupplier) {
        if (logger.isTraceEnabled()) {
            final Duration duration = DurationTimer.measure(timedWork);
            log(LocationAwareLogger.TRACE_INT,
                    () -> LogUtil.getDurationMessage(getSafeMessage(workDescriptionSupplier), duration),
                    null);
        } else {
            timedWork.run();
        }
    }

    @Override
    public void logDurationIfDebugEnabled(final Runnable timedWork, final Supplier<String> workDescriptionSupplier) {
        if (logger.isDebugEnabled()) {
            final Duration duration = DurationTimer.measure(timedWork);
            log(LocationAwareLogger.DEBUG_INT,
                    () -> LogUtil.getDurationMessage(getSafeMessage(workDescriptionSupplier), duration),
                    null);
        } else {
            timedWork.run();
        }
    }

    @Override
    public void logDurationIfInfoEnabled(final Runnable timedWork, final Supplier<String> workDescriptionSupplier) {
        if (logger.isInfoEnabled()) {
            final Duration duration = DurationTimer.measure(timedWork);
            log(LocationAwareLogger.INFO_INT,
                    () -> LogUtil.getDurationMessage(getSafeMessage(workDescriptionSupplier), duration),
                    null);
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

    private void log(final int severity, final Supplier<String> message, final Throwable t) {
        try {
            logger.log(null, FQCN, severity, getSafeMessage(message), null, t);

        } catch (final RuntimeException e) {
            try {
                logger.log(null,
                        FQCN,
                        LocationAwareLogger.ERROR_INT,
                        "ERROR LOGGING MESSAGE - " + e.getMessage(),
                        null,
                        e);
            } catch (final RuntimeException ex) {
                ex.printStackTrace();
            }
        }
    }

    private String getSafeMessage(final Supplier<String> supplier) {
        if (supplier != null) {
            try {
                return supplier.get();
            } catch (final RuntimeException e) {
                try {
                    logger.log(null,
                            FQCN,
                            LocationAwareLogger.ERROR_INT,
                            "ERROR LOGGING MESSAGE - " + e.getMessage(),
                            null,
                            e);
                } catch (final RuntimeException ex) {
                    ex.printStackTrace();
                }
            }
        }
        return null;
    }

    private <T> String getSafeMessage(final Function<T, String> function, final T t) {
        if (function != null) {
            try {
                return function.apply(t);
            } catch (final RuntimeException e) {
                try {
                    logger.log(null,
                            FQCN,
                            LocationAwareLogger.ERROR_INT,
                            "ERROR LOGGING MESSAGE - " + e.getMessage(),
                            null,
                            e);
                } catch (final RuntimeException ex) {
                    ex.printStackTrace();
                }
            }
        }
        return null;
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
