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

package stroom.util.concurrent;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import java.util.Objects;

public class UncheckedInterruptedException extends RuntimeException {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UncheckedInterruptedException.class);

    public UncheckedInterruptedException(final String message, final InterruptedException interruptedException) {
        super(message, interruptedException);
    }

    public UncheckedInterruptedException(final InterruptedException interruptedException) {
        super(interruptedException.getMessage(), interruptedException);
    }

    /**
     * Logs the error with using the msgSupplier to provide the log message, resets the interrupt flag
     * and returns a new {@link UncheckedInterruptedException} that can be rethrown.
     */
    public static UncheckedInterruptedException create(final InterruptedException e) {
        return create(e.getMessage(), e);
    }

    /**
     * Logs the error to DEBUG using the msgSupplier to provide the log message,
     * resets the interrupt flag
     * and returns a new {@link UncheckedInterruptedException} that can be rethrown.
     */
    public static UncheckedInterruptedException create(final String message,
                                                       final InterruptedException e) {
        LOGGER.debug(() ->
                Objects.requireNonNullElseGet(
                        message,
                        () -> NullSafe.getOrElse(e, Throwable::getMessage, "Interrupted")
                ));
        // Continue to interrupt the thread.
        Thread.currentThread().interrupt();
        return new UncheckedInterruptedException(message, e);
    }
}
