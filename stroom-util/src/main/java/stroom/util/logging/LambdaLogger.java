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

import stroom.util.shared.NullSafe;

import org.slf4j.Logger;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface LambdaLogger extends Logger {

    void trace(Supplier<String> message);

    void trace(Supplier<String> message, Throwable t);

    void debug(Supplier<String> message);

    void debug(Supplier<String> message, Throwable t);

    void info(Supplier<String> message);

    void info(Supplier<String> message, Throwable t);

    void warn(Supplier<String> message);

    void warn(Supplier<String> message, Throwable t);

    void error(Supplier<String> message);

    void error(Supplier<String> message, Throwable t);

    /**
     * Logs to ERROR using args but NOT including throwable.
     * <p>
     * {@code " (Enable debug for stack trace)"} is appended to the end of message.
     * </p>
     * <p>
     * Also logs to DEBUG using args AND throwable so DEBUG gets the full stack trace.
     * </p>
     *
     * @param message   The message to log. " (Enable debug for stack trace)" will be appended to the
     *                  end of message for the ERROR log.
     * @param throwable The exception to log at DEBUG level only.
     * @param args      The message arguments, not including the throwable.
     */
    default void errorAndDebug(final Throwable throwable, final String message, final Object... args) {
        try {
            error(() -> LogUtil.message(message + " (Enable debug for stack trace)", args));
            if (isDebugEnabled()) {
                if (NullSafe.isEmptyArray(args)) {
                    debug(message, throwable);
                } else {
                    // Add the throwable as another arg on the end
                    final int newLength = args.length + 1;
                    final Object[] newArgs = Arrays.copyOf(args, newLength);
                    newArgs[newLength - 1] = throwable;
                    debug(message, newArgs);
                }
            }
        } catch (final Exception e) {
            error("Error calling errorAndDebug: {}", LogUtil.exceptionMessage(e), e);
        }
    }

    /**
     * Logs to ERROR without including throwable. " (Enable debug for stack trace)" is
     * appended to the end of message. Also logs to DEBUG with throwable so DEBUG gets
     * the full stack trace.
     *
     * @param messageSupplier The message to log. " (Enable debug for stack trace)" will be appended to the
     *                        end of message for the ERROR log.
     * @param throwable       The exception to log at DEBUG level only.
     */
    default void errorAndDebug(final Throwable throwable,
                               final Supplier<String> messageSupplier) {
        try {
            error(() -> messageSupplier.get() + " (Enable debug for stack trace)");
            if (isDebugEnabled()) {
                debug(messageSupplier.get(), throwable);
            }
        } catch (final Exception e) {
            error("Error calling errorAndDebug: {}", LogUtil.exceptionMessage(e), e);
        }
    }

    void log(LogLevel logLevel, String message);

    void log(LogLevel logLevel, String format, Object arg);

    void log(LogLevel logLevel, String format, Object arg1, Object arg2);

    void log(LogLevel logLevel, String format, Object... args);

    void log(LogLevel logLevel, Supplier<String> messageSupplier);

    void log(LogLevel logLevel, Supplier<String> messageSupplier, Throwable t);

    boolean isTraceEnabled();

    boolean isDebugEnabled();

    boolean isInfoEnabled();

    boolean isWarnEnabled();

    boolean isErrorEnabled();

    boolean isEnabled(LogLevel logLevel);

    /**
     * Performs timedWork and if TRACE is enabled, logs the time taken to do that work
     *
     * @param timedWork       Work to perform and to time if required
     * @param <T>             The type of the result of the work
     * @param workDescription The name of the work to be added to the log message. The log message looks
     *                        like '{@code Completed [<work description>] in <duration>}'.
     * @return The result of the work
     */
    default <T> T logDurationIfTraceEnabled(final Supplier<T> timedWork, final String workDescription) {
        return logDurationIfTraceEnabled(timedWork, () -> workDescription);
    }

    /**
     * Performs timedWork and if TRACE is enabled, logs the time taken to do that work. This cannot be used
     * where the work throws a checked exception.
     *
     * @param timedWork               Work to perform and to time if required
     * @param <T>                     The type of the result of the work
     * @param workDescriptionSupplier A supplier of the description of the work to be added to the log message.
     *                                The log message looks like
     *                                '{@code Completed [<work description>] in <duration>}'.
     * @return The result of the work
     */
    <T> T logDurationIfTraceEnabled(final Supplier<T> timedWork, final Supplier<String> workDescriptionSupplier);

    /**
     * Performs timedWork and if TRACE is enabled, logs the time taken to do that work. This cannot be used
     * where the work throws a checked exception.
     *
     * @param timedWork               Work to perform and to time if required
     * @param <T>                     The type of the result of the work
     * @param workDescriptionFunction A supplier of the description of the work to be added to the log message.
     *                                The log message looks like
     *                                '{@code Completed [<work description>] in <duration>}'.
     * @return The result of the work
     */
    <T> T logDurationIfTraceEnabled(final Supplier<T> timedWork, final Function<T, String> workDescriptionFunction);

    /**
     * Performs timedWork and if DEBUG is enabled, logs the time taken to do that work. This cannot be used
     * where the work throws a checked exception.
     *
     * @param timedWork       Work to perform and to time if required
     * @param workDescription The name of the work to be added to the log message. The log message looks
     *                        like '{@code Completed [<work description>] in <duration>}'.
     * @param <T>             The type of the result of the work
     * @return The result of the work
     */
    default <T> T logDurationIfDebugEnabled(final Supplier<T> timedWork, final String workDescription) {
        return logDurationIfDebugEnabled(timedWork, () -> workDescription);
    }

    /**
     * Performs timedWork and if DEBUG is enabled, logs the time taken to do that work. This cannot be used
     * where the work throws a checked exception.
     *
     * @param timedWork               Work to perform and to time if required
     * @param workDescriptionSupplier The name of the work to be added to the log message. The log message looks
     *                                like '{@code Completed [<work description>] in <duration>}'.
     * @param <T>                     The type of the result of the work
     * @return The result of the work
     */
    <T> T logDurationIfDebugEnabled(final Supplier<T> timedWork, final Supplier<String> workDescriptionSupplier);

    /**
     * Performs timedWork and if DEBUG is enabled, logs the time taken to do that work. This cannot be used
     * where the work throws a checked exception.
     *
     * @param timedWork               Work to perform and to time if required
     * @param workDescriptionFunction The name of the work to be added to the log message. The log message looks
     *                                like '{@code Completed [<work description>] in <duration>}'.
     * @param <T>                     The type of the result of the work
     * @return The result of the work
     */
    <T> T logDurationIfDebugEnabled(final Supplier<T> timedWork, final Function<T, String> workDescriptionFunction);

    /**
     * Performs timedWork and if INFO is enabled, logs the time taken to do that work. This cannot be used
     * where the work throws a checked exception.
     *
     * @param timedWork       Work to perform and to time if required
     * @param workDescription The name of the work to be added to the log message. The log message looks
     *                        like '{@code Completed [<work description>] in <duration>}'.
     * @param <T>             The type of the result of the work
     * @return The result of the work
     */
    default <T> T logDurationIfInfoEnabled(final Supplier<T> timedWork, final String workDescription) {
        return logDurationIfInfoEnabled(timedWork, () -> workDescription);
    }

    /**
     * Performs timedWork and if INFO is enabled, logs the time taken to do that work. This cannot be used
     * where the work throws a checked exception.
     *
     * @param timedWork               Work to perform and to time if required
     * @param workDescriptionSupplier The name of the work to be added to the log message. The log message looks
     *                                like '{@code Completed [<work description>] in <duration>}'.
     * @param <T>                     The type of the result of the work
     * @return The result of the work
     */
    <T> T logDurationIfInfoEnabled(final Supplier<T> timedWork, final Supplier<String> workDescriptionSupplier);

    /**
     * Performs timedWork and if INFO is enabled, logs the time taken to do that work. This cannot be used
     * where the work throws a checked exception.
     *
     * @param timedWork               Work to perform and to time if required
     * @param workDescriptionFunction The name of the work to be added to the log message. The log message looks
     *                                like '{@code Completed [<work description>] in <duration>}'.
     * @param <T>                     The type of the result of the work
     * @return The result of the work
     */
    <T> T logDurationIfInfoEnabled(final Supplier<T> timedWork, final Function<T, String> workDescriptionFunction);

    /**
     * Performs timedWork and if TRACE is enabled, logs the time taken to do that work. This cannot be used
     * where the work throws a checked exception.
     *
     * @param timedWork       Work to perform and to time if required
     * @param workDescription The name of the work to be added to the log message. The log message looks
     *                        like '{@code Completed [<work description>] in <duration>}'.
     */
    default void logDurationIfTraceEnabled(final Runnable timedWork, final String workDescription) {
        logDurationIfTraceEnabled(timedWork, () -> workDescription);
    }

    /**
     * Performs timedWork and if TRACE is enabled, logs the time taken to do that work. This cannot be used
     * where the work throws a checked exception.
     *
     * @param timedWork               Work to perform and to time if required
     * @param workDescriptionSupplier The name of the work to be added to the log message. The log message looks
     *                                like '{@code Completed [<work description>] in <duration>}'.
     */
    void logDurationIfTraceEnabled(final Runnable timedWork, final Supplier<String> workDescriptionSupplier);

    /**
     * Performs timedWork and if DEBUG is enabled, logs the time taken to do that work. This cannot be used
     * where the work throws a checked exception.
     *
     * @param timedWork       Work to perform and to time if required
     * @param workDescription The name of the work to be added to the log message. The log message looks
     *                        like '{@code Completed [<work description>] in <duration>}'.
     */
    default void logDurationIfDebugEnabled(final Runnable timedWork, final String workDescription) {
        logDurationIfDebugEnabled(timedWork, () -> workDescription);
    }

    /**
     * Performs timedWork and if DEBUG is enabled, logs the time taken to do that work. This cannot be used
     * where the work throws a checked exception.
     *
     * @param timedWork               Work to perform and to time if required
     * @param workDescriptionSupplier The name of the work to be added to the log message. The log message looks
     *                                like '{@code Completed [<work description>] in <duration>}'.
     */
    void logDurationIfDebugEnabled(final Runnable timedWork, final Supplier<String> workDescriptionSupplier);

    /**
     * Performs timedWork and if INFO is enabled, logs the time taken to do that work. This cannot be used
     * where the work throws a checked exception.
     *
     * @param timedWork       Work to perform and to time if required
     * @param workDescription The name of the work to be added to the log message. The log message looks
     *                        like '{@code Completed [<work description>] in <duration>}'.
     */
    default void logDurationIfInfoEnabled(final Runnable timedWork, final String workDescription) {
        logDurationIfInfoEnabled(timedWork, () -> workDescription);
    }

    /**
     * Performs timedWork and if INFO is enabled, logs the time taken to do that work. This cannot be used
     * where the work throws a checked exception.
     *
     * @param timedWork               Work to perform and to time if required
     * @param workDescriptionSupplier The name of the work to be added to the log message. The log message looks
     *                                like '{@code Completed [<work description>] in <duration>}'.
     */
    void logDurationIfInfoEnabled(final Runnable timedWork, final Supplier<String> workDescriptionSupplier);

    /**
     * Performs work only if TRACE is enabled.
     *
     * @param work The work to perform.
     */
    void doIfTraceEnabled(final Runnable work);

    /**
     * Performs work only if DEBUG is enabled.
     *
     * @param work The work to perform.
     */
    void doIfDebugEnabled(final Runnable work);

    /**
     * Performs work only if INFO is enabled.
     *
     * @param work The work to perform.
     */
    void doIfInfoEnabled(final Runnable work);

    /**
     * If debug is enabled returns the supplied consumer else, returns a no-op consumer.
     * Useful in the {@link java.util.stream.Stream#peek(Consumer)} method for logging items
     * in the stream.
     */
    default <T> Consumer<T> createIfDebugConsumer(final Consumer<T> debugConsumer) {
        if (isDebugEnabled()) {
            return debugConsumer;
        } else {
            return val -> {
            };
        }
    }

    /**
     * If debug is enabled returns the supplied consumer else, returns a no-op consumer.
     * Useful in the {@link java.util.stream.Stream#peek(Consumer)} method for logging items
     * in the stream.
     */
    default <T> Consumer<T> createIfTraceConsumer(final Consumer<T> debugConsumer) {
        if (isTraceEnabled()) {
            return debugConsumer;
        } else {
            return val -> {
            };
        }
    }

}
