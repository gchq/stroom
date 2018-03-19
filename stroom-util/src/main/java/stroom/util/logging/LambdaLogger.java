/*
 * Copyright 2017 Crown Copyright
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

import org.slf4j.helpers.MessageFormatter;

import java.util.function.Supplier;

public interface LambdaLogger {
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
     * Performs timedWork and if TRACE is enabled, logs the time taken to do that work
     * @param timedWork Work to perform and to time if required
     * @param <T> The type of the result of the work
     * @param workDescription The name of the work to be added to the log message
     * @return The result of the work
     */
    default <T> T logDurationIfTraceEnabled(final Supplier<T> timedWork, final String workDescription) {
        return logDurationIfTraceEnabled(timedWork, () -> workDescription);
    }

    /**
     * Performs timedWork and if TRACE is enabled, logs the time taken to do that work. This cannot be used
     * where the work throws a checked exception.
     * @param timedWork Work to perform and to time if required
     * @param <T> The type of the result of the work
     * @param workDescriptionSupplier A supplier of the description of the work to be added to the log message
     * @return The result of the work
     */
    <T> T logDurationIfTraceEnabled(final Supplier<T> timedWork, final Supplier<String> workDescriptionSupplier);

    /**
     * Performs timedWork and if DEBUG is enabled, logs the time taken to do that work. This cannot be used
     * where the work throws a checked exception.
     * @param timedWork Work to perform and to time if required
     * @param workDescription The name of the work to be added to the log message
     * @param <T> The type of the result of the work
     * @return The result of the work
     */
    default <T> T logDurationIfDebugEnabled(final Supplier<T> timedWork, final String workDescription) {
        return logDurationIfDebugEnabled(timedWork, () -> workDescription);
    }

    /**
     * Performs timedWork and if DEBUG is enabled, logs the time taken to do that work. This cannot be used
     * where the work throws a checked exception.
     * @param timedWork Work to perform and to time if required
     * @param workDescriptionSupplier The name of the work to be added to the log message
     * @param <T> The type of the result of the work
     * @return The result of the work
     */
    <T> T logDurationIfDebugEnabled(final Supplier<T> timedWork, final Supplier<String> workDescriptionSupplier);

    /**
     * Performs timedWork and if INFO is enabled, logs the time taken to do that work. This cannot be used
     * where the work throws a checked exception.
     * @param timedWork Work to perform and to time if required
     * @param workDescription The name of the work to be added to the log message
     * @param <T> The type of the result of the work
     * @return The result of the work
     */
    default <T> T logDurationIfInfoEnabled(final Supplier<T> timedWork, final String workDescription) {
        return logDurationIfInfoEnabled(timedWork, () -> workDescription);
    }

    /**
     * Performs timedWork and if INFO is enabled, logs the time taken to do that work. This cannot be used
     * where the work throws a checked exception.
     * @param timedWork Work to perform and to time if required
     * @param workDescriptionSupplier The name of the work to be added to the log message
     * @param <T> The type of the result of the work
     * @return The result of the work
     */
    <T> T logDurationIfInfoEnabled(final Supplier<T> timedWork, final Supplier<String> workDescriptionSupplier);

    /**
     * Performs timedWork and if TRACE is enabled, logs the time taken to do that work. This cannot be used
     * where the work throws a checked exception.
     * @param timedWork Work to perform and to time if required
     * @param workDescription The name of the work to be added to the log message
     */
    default void logDurationIfTraceEnabled(final Runnable timedWork, final String workDescription) {
        logDurationIfTraceEnabled(timedWork, () -> workDescription);
    }

    /**
     * Performs timedWork and if TRACE is enabled, logs the time taken to do that work. This cannot be used
     * where the work throws a checked exception.
     * @param timedWork Work to perform and to time if required
     * @param workDescriptionSupplier The name of the work to be added to the log message
     */
    void logDurationIfTraceEnabled(final Runnable timedWork, final Supplier<String> workDescriptionSupplier);

    /**
     * Performs timedWork and if DEBUG is enabled, logs the time taken to do that work. This cannot be used
     * where the work throws a checked exception.
     * @param timedWork Work to perform and to time if required
     * @param workDescription The name of the work to be added to the log message
     */
    default void logDurationIfDebugEnabled(final Runnable timedWork, final String workDescription) {
        logDurationIfDebugEnabled(timedWork, () -> workDescription);
    }

    /**
     * Performs timedWork and if DEBUG is enabled, logs the time taken to do that work. This cannot be used
     * where the work throws a checked exception.
     * @param timedWork Work to perform and to time if required
     * @param workDescriptionSupplier The name of the work to be added to the log message
     */
    void logDurationIfDebugEnabled(final Runnable timedWork, final Supplier<String> workDescriptionSupplier);

    /**
     * Performs timedWork and if INFO is enabled, logs the time taken to do that work. This cannot be used
     * where the work throws a checked exception.
     * @param timedWork Work to perform and to time if required
     * @param workDescription The name of the work to be added to the log message
     */
    default void logDurationIfInfoEnabled(final Runnable timedWork, final String workDescription) {
        logDurationIfInfoEnabled(timedWork, () -> workDescription);
    }

    /**
     * Performs timedWork and if INFO is enabled, logs the time taken to do that work. This cannot be used
     * where the work throws a checked exception.
     * @param timedWork Work to perform and to time if required
     * @param workDescriptionSupplier The name of the work to be added to the log message
     */
    void logDurationIfInfoEnabled(final Runnable timedWork, final Supplier<String> workDescriptionSupplier);

    /**
     * Performs work only if TRACE is enabled.
     * @param work The work to perform.
     */
    void doIfTraceEnabled(final Runnable work);

    /**
     * Performs work only if DEBUG is enabled.
     * @param work The work to perform.
     */
    void doIfDebugEnabled(final Runnable work);

    /**
     * Performs work only if INFO is enabled.
     * @param work The work to perform.
     */
    void doIfInfoEnabled(final Runnable work);

    /**
     * Constructs a formatted message string using a format string that takes
     * the same placeholders as SLF4J, e.g.
     * "Function called with name {} and value {}"
     * @param format SLF4J style format string
     * @param args The values for any placeholders in the message format
     * @return A formatted message
     */
    static String buildMessage(String format, Object... args) {
        return MessageFormatter.arrayFormat(format, args).getMessage();
    }
}
