/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.util;

import stroom.util.io.ByteSize;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Util methods for safely working with things that might be null.
 * <p>
 * MUST contain only methods that cannot go in {@link NullSafe} due
 * to GWT compilation constraints.
 * </p>
 *
 */
public class NullSafeExtra {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(NullSafeExtra.class);

    private NullSafeExtra() {
    }

    /**
     * Returns the passed stroomDuration if it is non-null else returns a ZERO {@link StroomDuration}
     */
    public static StroomDuration duration(final StroomDuration stroomDuration) {
        return stroomDuration != null
                ? stroomDuration
                : StroomDuration.ZERO;
    }

    /**
     * Returns the passed duration if it is non-null else returns a ZERO {@link Duration}
     */
    public static Duration duration(final Duration duration) {
        return duration != null
                ? duration
                : Duration.ZERO;
    }

    /**
     * Returns the passed durationTimer if it is non-null else returns a ZERO {@link DurationTimer}
     */
    public static DurationTimer durationTimer(final DurationTimer durationTimer) {
        return durationTimer != null
                ? durationTimer
                : DurationTimer.ZERO;
    }

    /**
     * Returns the passed byteSize if it is non-null else returns a ZERO {@link ByteSize}
     */
    public static ByteSize byteSize(final ByteSize byteSize) {
        return byteSize != null
                ? byteSize
                : ByteSize.ZERO;
    }

    /**
     * Will close closeable if it is non-null.
     *
     * @param closeable        The closeable to close. A no-op if null.
     * @param swallowException If true any exceptions will be swallowed. If false, they will be re-throw
     *                         either wrapped in a {@link RuntimeException} or an {@link UncheckedIOException}
     *                         as appropriate.
     * @param messageSupplier  If non-null, an error will be logged. messageSupplier should provide details of the
     *                         thing being closed. The error log message will be of the form
     *                         {@code 'Error closing <messageSupplier result>'}.
     */
    public static void close(final AutoCloseable closeable,
                             final boolean swallowException,
                             final Supplier<String> messageSupplier) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (final Exception e) {
                if (messageSupplier != null) {
                    LOGGER.error("Error closing {}", messageSupplier.get(), e);
                }
                if (!swallowException) {
                    if (e instanceof final IOException ioe) {
                        throw new UncheckedIOException(ioe);
                    } else {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    /**
     * Will close closeable if it is non-null.
     *
     * @param closeable        The closeable to close. A no-op if null.
     * @param swallowException If true any exceptions will be swallowed. If false, they will be re-throw
     *                         either wrapped in a {@link RuntimeException} or an {@link UncheckedIOException}
     *                         as appropriate.
     * @param message          If non-null, an error will be logged. message should provide details of the
     *                         thing being closed. The error log message will be of the form
     *                         {@code 'Error closing <message>'}.
     * @return Always returns null so you assign the result to the variable passed in to prevent it from
     * being closed again.
     */
    public static <T extends Closeable> T close(final AutoCloseable closeable,
                                                final boolean swallowException,
                                                final String message) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (final Exception e) {
                if (message != null) {
                    LOGGER.error("Error closing {}", message, e);
                }
                if (!swallowException) {
                    if (e instanceof final IOException ioe) {
                        throw new UncheckedIOException(ioe);
                    } else {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        // Always return null, so you can assign the result back to the passed variable
        // to stop it being closed again.
        return null;
    }
}
