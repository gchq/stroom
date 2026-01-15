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

package stroom.util.exception;

import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import java.util.function.Supplier;

@FunctionalInterface
public interface InterruptibleSupplier<T> {

    LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(InterruptibleSupplier.class);

    T get() throws InterruptedException;

    /**
     * Wraps a supplier that throws an {@link InterruptedException} with a catch block that
     * will reset the interrupt flag and wrap the exception with a {@link UncheckedInterruptedException},
     * thus making it unchecked and usable in a lambda.
     */
    static <T> Supplier<T> unchecked(final InterruptibleSupplier<T> supplier) {
        return unchecked(supplier, null);
    }

    /**
     * Wraps a supplier that throws an {@link InterruptedException} with a catch block that
     * will reset the interrupt flag and wrap the exception with a {@link UncheckedInterruptedException},
     * thus making it unchecked and usable in a lambda.
     */
    static <T> Supplier<T> unchecked(final InterruptibleSupplier<T> supplier,
                                     final Supplier<String> debugMsgSupplier) {
        return () -> {
            try {
                return supplier.get();
            } catch (final InterruptedException e) {
                LOGGER.debug(() ->
                                NullSafe.getOrElse(
                                        debugMsgSupplier,
                                        Supplier::get,
                                        "Interrupted"),
                        e);
                Thread.currentThread().interrupt();
                throw new UncheckedInterruptedException("Interrupted: " + e.getMessage(), e);
            }
        };
    }
}
