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

import org.slf4j.Logger;
import org.slf4j.Marker;

import java.time.Duration;
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
                logger.trace(getSafeMessage(message));
            }
        } catch (final RuntimeException e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void trace(final Supplier<String> message, final Throwable t) {
        try {
            if (logger.isTraceEnabled()) {
                logger.trace(getSafeMessage(message), t);
            }
        } catch (final RuntimeException e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void debug(final Supplier<String> message) {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug(getSafeMessage(message));
            }
        } catch (final RuntimeException e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void debug(final Supplier<String> message, final Throwable t) {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug(getSafeMessage(message), t);
            }
        } catch (final RuntimeException e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void info(final Supplier<String> message) {
        try {
            if (logger.isInfoEnabled()) {
                logger.info(getSafeMessage(message));
            }
        } catch (final RuntimeException e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void info(final Supplier<String> message, final Throwable t) {
        try {
            if (logger.isInfoEnabled()) {
                logger.info(getSafeMessage(message), t);
            }
        } catch (final RuntimeException e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void warn(final Supplier<String> message) {
        try {
            if (logger.isWarnEnabled()) {
                logger.warn(getSafeMessage(message));
            }
        } catch (final RuntimeException e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void warn(final Supplier<String> message, final Throwable t) {
        try {
            if (logger.isWarnEnabled()) {
                logger.warn(getSafeMessage(message), t);
            }
        } catch (final RuntimeException e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void error(final Supplier<String> message) {
        try {
            if (logger.isErrorEnabled()) {
                logger.error(getSafeMessage(message));
            }
        } catch (final RuntimeException e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void error(final Supplier<String> message, final Throwable t) {
        try {
            if (logger.isErrorEnabled()) {
                logger.error(getSafeMessage(message), t);
            }
        } catch (final RuntimeException e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void log(final LogLevel logLevel, final String message) {
        try {
            switch (logLevel) {
                case TRACE -> logger.trace(message);
                case DEBUG -> logger.debug(message);
                case INFO -> logger.info(message);
                case WARN -> logger.warn(message);
                case ERROR -> logger.error(message);
                default -> logger.error("Unexpected logLevel: {}", logLevel);
            }
        } catch (final Exception e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void log(final LogLevel logLevel, final String format, final Object arg) {
        try {
            switch (logLevel) {
                case TRACE -> logger.trace(format, arg);
                case DEBUG -> logger.debug(format, arg);
                case INFO -> logger.info(format, arg);
                case WARN -> logger.warn(format, arg);
                case ERROR -> logger.error(format, arg);
                default -> logger.error("Unexpected logLevel: {}", logLevel);
            }
        } catch (final Exception e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void log(final LogLevel logLevel,
                    final String format,
                    final Object arg1,
                    final Object arg2) {
        try {
            switch (logLevel) {
                case TRACE -> logger.trace(format, arg1, arg2);
                case DEBUG -> logger.debug(format, arg1, arg2);
                case INFO -> logger.info(format, arg1, arg2);
                case WARN -> logger.warn(format, arg1, arg2);
                case ERROR -> logger.error(format, arg1, arg2);
                default -> logger.error("Unexpected logLevel: {}", logLevel);
            }
        } catch (final Exception e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void log(final LogLevel logLevel, final String format, final Object... args) {
        try {
            switch (logLevel) {
                case TRACE -> logger.trace(format, args);
                case DEBUG -> logger.debug(format, args);
                case INFO -> logger.info(format, args);
                case WARN -> logger.warn(format, args);
                case ERROR -> logger.error(format, args);
                default -> logger.error("Unexpected logLevel: {}", logLevel);
            }
        } catch (final Exception e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void log(final LogLevel logLevel, final Supplier<String> messageSupplier) {
        try {
            switch (logLevel) {
                case TRACE -> {
                    if (logger.isTraceEnabled()) {
                        logger.trace(getSafeMessage(messageSupplier));
                    }
                }
                case DEBUG -> {
                    if (logger.isDebugEnabled()) {
                        logger.debug(getSafeMessage(messageSupplier));
                    }
                }
                case INFO -> {
                    if (logger.isInfoEnabled()) {
                        logger.info(getSafeMessage(messageSupplier));
                    }
                }
                case WARN -> {
                    if (logger.isWarnEnabled()) {
                        logger.warn(getSafeMessage(messageSupplier));
                    }
                }
                case ERROR -> {
                    if (logger.isErrorEnabled()) {
                        logger.error(getSafeMessage(messageSupplier));
                    }
                }
                default -> logger.error("Unexpected logLevel: {}", logLevel);
            }
        } catch (final Exception e) {
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
        }
    }

    @Override
    public void log(final LogLevel logLevel,
                    final Supplier<String> messageSupplier,
                    final Throwable t) {
        try {
            switch (logLevel) {
                case TRACE -> {
                    if (logger.isTraceEnabled()) {
                        logger.trace(getSafeMessage(messageSupplier), t);
                    }
                }
                case DEBUG -> {
                    if (logger.isDebugEnabled()) {
                        logger.debug(getSafeMessage(messageSupplier), t);
                    }
                }
                case INFO -> {
                    if (logger.isInfoEnabled()) {
                        logger.info(getSafeMessage(messageSupplier), t);
                    }
                }
                case WARN -> {
                    if (logger.isWarnEnabled()) {
                        logger.warn(getSafeMessage(messageSupplier), t);
                    }
                }
                case ERROR -> {
                    if (logger.isErrorEnabled()) {
                        logger.error(getSafeMessage(messageSupplier), t);
                    }
                }
                default -> logger.error("Unexpected logLevel: {}", logLevel);
            }
        } catch (final Exception e) {
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
            logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
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
            try {
                logger.trace(LogUtil.getDurationMessage(getSafeMessage(workDescriptionSupplier), duration));
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
            final DurationTimer durationTimer = DurationTimer.start();
            final T result = timedWork.get();
            final Duration duration = durationTimer.get();
            try {
                logger.trace(LogUtil.getDurationMessage(getSafeMessage(workDescriptionFunction, result), duration));
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
            final DurationTimer durationTimer = DurationTimer.start();
            final T result = timedWork.get();
            final Duration duration = durationTimer.get();
            try {
                logger.debug(LogUtil.getDurationMessage(getSafeMessage(workDescriptionSupplier), duration));
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
            final DurationTimer durationTimer = DurationTimer.start();
            final T result = timedWork.get();
            final Duration duration = durationTimer.get();
            try {
                logger.debug(LogUtil.getDurationMessage(getSafeMessage(workDescriptionFunction, result), duration));
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
            final DurationTimer durationTimer = DurationTimer.start();
            final T result = timedWork.get();
            final Duration duration = durationTimer.get();
            try {
                logger.info(LogUtil.getDurationMessage(getSafeMessage(workDescriptionSupplier), duration));
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
            final DurationTimer durationTimer = DurationTimer.start();
            final T result = timedWork.get();
            final Duration duration = durationTimer.get();
            try {
                logger.info(LogUtil.getDurationMessage(getSafeMessage(workDescriptionFunction, result), duration));
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
            final Duration duration = DurationTimer.measure(timedWork);
            try {
                logger.trace(LogUtil.getDurationMessage(getSafeMessage(workDescriptionSupplier), duration));
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
            final Duration duration = DurationTimer.measure(timedWork);
            try {
                logger.debug(LogUtil.getDurationMessage(getSafeMessage(workDescriptionSupplier), duration));
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
            final Duration duration = DurationTimer.measure(timedWork);
            try {
                logger.info(LogUtil.getDurationMessage(getSafeMessage(workDescriptionSupplier), duration));
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


    private String getSafeMessage(final Supplier<String> supplier) {
        if (supplier != null) {
            try {
                return supplier.get();
            } catch (final RuntimeException e) {
                logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
            }
        }
        return null;
    }

    private <T> String getSafeMessage(final Function<T, String> function, final T t) {
        if (function != null) {
            try {
                return function.apply(t);
            } catch (final RuntimeException e) {
                logger.error("ERROR LOGGING MESSAGE - " + e.getMessage(), e);
            }
        }
        return null;
    }
}
