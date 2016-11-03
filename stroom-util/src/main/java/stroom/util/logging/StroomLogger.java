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

package stroom.util.logging;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.Priority;

import java.util.IllegalFormatException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Wrapper around log4j to do guarded logging with expression evaluation. E.g.
 * StroomLogger LOGGER.logInfo("copy() %s %s", file1, file2);
 *
 * Also if you have noisy logging (e.g. "Written 2/100") you can use
 * xxxInterval(...) method to only log out every second.
 */
public final class StroomLogger {
    private static final String FQCN = StroomLogger.class.getName();
    private final Logger logger;

    private long interval = 0;
    private long nextLogTime = 0;

    // Use a private constructor as this is only made via the static factory.
    private StroomLogger(final Logger logger) {
        this.logger = logger;
    }

    public static final StroomLogger getLogger(final Class<?> clazz) {
        final Logger logger = Logger.getLogger(clazz.getName());
        final StroomLogger stroomLogger = new StroomLogger(logger);
        stroomLogger.interval = 1000;
        return stroomLogger;
    }

    public boolean checkInterval() {
        if (interval == 0) {
            return true;
        }
        // We don't care about thread race conditions ...
        final long time = System.currentTimeMillis();
        if (time > nextLogTime) {
            nextLogTime = time + interval;
            return true;
        }
        return false;

    }

    public void setInterval(final long interval) {
        this.interval = interval;
    }

    public void debugInterval(final Object... args) {
        if (isDebugEnabled()) {
            if (checkInterval()) {
                logger.log(FQCN, Level.DEBUG, buildMessage(args), extractThrowable(args));
            }
        }
    }

    public void traceInterval(final Object... args) {
        if (isTraceEnabled()) {
            if (checkInterval()) {
                logger.log(FQCN, Level.TRACE, buildMessage(args), extractThrowable(args));
            }
        }
    }

    public void infoInterval(final Object... args) {
        if (isInfoEnabled()) {
            if (checkInterval()) {
                logger.log(FQCN, Level.INFO, buildMessage(args), extractThrowable(args));
            }
        }
    }

    public String buildMessage(final Object... args) {
        IllegalFormatException ilEx = null;
        try {
            if (args[0] instanceof String) {
                if (args.length > 1) {
                    final Object[] otherArgs = new Object[args.length - 1];
                    System.arraycopy(args, 1, otherArgs, 0, otherArgs.length);
                    return String.format((String) args[0], otherArgs);
                } else {
                    return (String) args[0];
                }
            }
        } catch (final IllegalFormatException il) {
            ilEx = il;
        }
        final StringBuilder builder = new StringBuilder();
        if (ilEx != null) {
            builder.append(ilEx.getMessage());
        }
        for (final Object arg : args) {
            if (builder.length() > 0) {
                builder.append(" - ");
            }
            builder.append(String.valueOf(arg));
        }
        return builder.toString();
    }

    public Throwable extractThrowable(final Object... args) {
        if (args.length > 0) {
            if (args[args.length - 1] instanceof Throwable) {
                return (Throwable) args[args.length - 1];
            }
        }
        return null;
    }

    /**
     * Logs a TRACE level message to the logger.
     *
     * @param args
     *            First argument is the log message. If the last argument is a
     *            {@link Throwable} then it will be attached to the log message.
     *            All other arguments are treated as the second argument to
     *            String.format(String format, Object... args)
     */
    public void trace(final Object... args) {
        if (isTraceEnabled()) {
            logger.log(FQCN, Level.TRACE, buildMessage(args), extractThrowable(args));
        }
    }

    /**
     * Logs a TRACE level message to the logger.
     *
     * @param msgSupplier
     *            A function that will supply the log message string
     * @param e
     *            The {@link Throwable} to attach to the message
     */
    public void trace(final Supplier<String> msgSupplier, final Throwable e) {
        log(Level.TRACE, this::isTraceEnabled, msgSupplier, e);
    }

    /**
     * Logs a TRACE level message to the logger.
     *
     * @param msgSupplier
     *            A function that will supply the log message string
     */
    public void trace(final Supplier<String> msgSupplier) {
        log(Level.TRACE, this::isTraceEnabled, msgSupplier, null);
    }

    /**
     * Logs a DEBUG level message to the logger.
     *
     * @param args
     *            First argument is the log message. If the last argument is a
     *            {@link Throwable} then it will be attached to the log message.
     *            All other arguments are treated as the second argument to
     *            String.format(String format, Object... args)
     */
    public void debug(final Object... args) {
        if (isDebugEnabled()) {
            logger.log(FQCN, Level.DEBUG, buildMessage(args), extractThrowable(args));
        }
    }

    /**
     * Logs a DEBUG level message to the logger.
     *
     * @param msgSupplier
     *            A function that will supply the log message string
     * @param e
     *            The {@link Throwable} to attach to the message
     */
    public void debug(final Supplier<String> msgSupplier, final Throwable e) {
        log(Level.DEBUG, this::isDebugEnabled, msgSupplier, e);
    }

    /**
     * Logs a DEBUG level message to the logger.
     *
     * @param msgSupplier
     *            A function that will supply the log message string
     */
    public void debug(final Supplier<String> msgSupplier) {
        log(Level.DEBUG, this::isDebugEnabled, msgSupplier, null);
    }

    /**
     * Logs a INFO level message to the logger.
     *
     * @param args
     *            First argument is the log message. If the last argument is a
     *            {@link Throwable} then it will be attached to the log message.
     *            All other arguments are treated as the second argument to
     *            String.format(String format, Object... args)
     */
    public void info(final Object... args) {
        if (isInfoEnabled()) {
            logger.log(FQCN, Level.INFO, buildMessage(args), extractThrowable(args));
        }
    }

    /**
     * Logs a INFO level message to the logger.
     *
     * @param msgSupplier
     *            A function that will supply the log message string
     * @param e
     *            The {@link Throwable} to attach to the message
     */
    public void info(final Supplier<String> msgSupplier, final Throwable e) {
        log(Level.INFO, this::isInfoEnabled, msgSupplier, e);
    }

    /**
     * Logs a INFO level message to the logger.
     *
     * @param msgSupplier
     *            A function that will supply the log message string
     */
    public void info(final Supplier<String> msgSupplier) {
        log(Level.INFO, this::isInfoEnabled, msgSupplier, null);
    }

    /**
     * Logs a WARN level message to the logger.
     *
     * @param args
     *            First argument is the log message. If the last argument is a
     *            {@link Throwable} then it will be attached to the log message.
     *            All other arguments are treated as the second argument to
     *            String.format(String format, Object... args)
     */
    public void warn(final Object... args) {
        logger.log(FQCN, Level.WARN, buildMessage(args), extractThrowable(args));
    }

    /**
     * Logs a WARN level message to the logger.
     *
     * @param msgSupplier
     *            A function that will supply the log message string
     * @param e
     *            The {@link Throwable} to attach to the message
     */
    public void warn(final Supplier<String> msgSupplier, final Throwable e) {
        log(Level.WARN, msgSupplier, e);
    }

    /**
     * Logs a WARN level message to the logger.
     *
     * @param msgSupplier
     *            A function that will supply the log message string
     */
    public void warn(final Supplier<String> msgSupplier) {
        log(Level.WARN, msgSupplier, null);
    }

    /**
     * Logs a ERROR level message to the logger.
     *
     * @param args
     *            First argument is the log message. If the last argument is a
     *            {@link Throwable} then it will be attached to the log message.
     *            All other arguments are treated as the second argument to
     *            String.format(String format, Object... args)
     */
    public void error(final Object... args) {
        logger.log(FQCN, Level.ERROR, buildMessage(args), extractThrowable(args));
    }

    /**
     * Logs a ERROR level message to the logger.
     *
     * @param msgSupplier
     *            A function that will supply the log message string
     * @param e
     *            The {@link Throwable} to attach to the message
     */
    public void error(final Supplier<String> msgSupplier, final Throwable e) {
        log(Level.ERROR, msgSupplier, e);
    }

    /**
     * Logs a ERROR level message to the logger.
     *
     * @param msgSupplier
     *            A function that will supply the log message string
     */
    public void error(final Supplier<String> msgSupplier) {
        log(Level.ERROR, msgSupplier, null);
    }

    /**
     * Logs a FATAL level message to the logger.
     *
     * @param args
     *            First argument is the log message. If the last argument is a
     *            {@link Throwable} then it will be attached to the log message.
     *            All other arguments are treated as the second argument to
     *            String.format(String format, Object... args)
     */
    public void fatal(final Object... args) {
        logger.log(FQCN, Level.FATAL, buildMessage(args), extractThrowable(args));
    }

    /**
     * Logs a FATAL level message to the logger.
     *
     * @param msgSupplier
     *            A function that will supply the log message string
     * @param e
     *            The {@link Throwable} to attach to the message
     */
    public void fatal(final Supplier<String> msgSupplier, final Throwable e) {
        log(Level.FATAL, msgSupplier, e);
    }

    /**
     * Logs a FATAL level message to the logger.
     *
     * @param msgSupplier
     *            A function that will supply the log message string
     */
    public void fatal(final Supplier<String> msgSupplier) {
        log(Level.FATAL, msgSupplier, null);
    }

    public boolean isTraceEnabled() {
        return logger != null && logger.isTraceEnabled();
    }

    public boolean isDebugEnabled() {
        return logger != null && logger.isDebugEnabled();
    }

    public boolean isInfoEnabled() {
        return logger != null && logger.isInfoEnabled();
    }

    @FunctionalInterface
    public interface Action {
        void doWork();
    }

    /**
     * Runs the passed functionalInterface if trace is enabled.
     *
     * @param action
     *            The functionalInterface to run
     */
    public void ifTraceIsEnabled(final Action action) {
        if (isTraceEnabled()) {
            action.doWork();
        }
    }

    /**
     * Runs the passed functionalInterface if debug is enabled.
     *
     * @param action
     *            The functionalInterface to run
     */
    public void ifDebugIsEnabled(final Action action) {
        if (isDebugEnabled()) {
            action.doWork();
        }
    }

    public void log(final String callerFQCN, final Priority level, final Object message, final Throwable t) {
        logger.log(callerFQCN, level, message, t);
    }

    /**
     * Logs a message to the logger.
     *
     * @param level
     *            The level to log at
     * @param msgSupplier
     *            A function that supplies the message string
     * @param e
     *            The {@link Throwable} to be attached to the log message
     */
    public void log(final Level level, final Supplier<String> msgSupplier, final Throwable e) {
        logger.log(FQCN, level, msgSupplier.get(), e);
    }

    /**
     * Logs a message to the logger.
     *
     * @param level
     *            The level to log at
     * @param enabledFunction
     *            The function to use to determine if it should log or not
     * @param msgSupplier
     *            A function that supplies the message string
     * @param e
     *            The {@link Throwable} to be attached to the log message
     */
    public void log(final Level level, final BooleanSupplier enabledFunction, final Supplier<String> msgSupplier,
            final Throwable e) {
        if (enabledFunction.getAsBoolean()) {
            this.log(level, msgSupplier, e);
        }
    }
}
