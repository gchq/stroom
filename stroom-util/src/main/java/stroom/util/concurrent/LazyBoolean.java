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

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * A boolean value that is lazily evaluated using a provided {@link BooleanSupplier}.
 */
public class LazyBoolean {

    private final BooleanSupplier valueSupplier;
    private transient volatile boolean isInitialised = false;
    // Volatile piggybacking ensures that a thread sees the right value
    // as long as we read/write value after reading/writing initialised,
    // hence no volatile on this one.
    private transient boolean value;

    private LazyBoolean(final BooleanSupplier valueSupplier) {
        this.valueSupplier = Objects.requireNonNull(valueSupplier);
    }

    public static LazyBoolean initialisedBy(final BooleanSupplier valueSupplier) {
        return new LazyBoolean(valueSupplier);
    }

    /**
     * @return The value, initialising it if required. No locks are used so
     * the valueSupplier may be called multiple times. This method should only
     * be used if valueSupplier is idempotent, side effect free and not too
     * costly to run.
     */
    public boolean getValueWithoutLocks() {
        if (!isInitialised) {
            // May be called >1 times by different threads
            final boolean newVal = valueSupplier.getAsBoolean();
            // Write order is important here due to volatile piggybacking
            value = newVal;
            isInitialised = true;
            return newVal;
        }
        return value;
    }

    /**
     * @return The value, initialising it if required. If initialisation
     * is required synchronisation will be used to ensure the supplier is only
     * called once.
     * Use this method if supplier is not idempotent, has side effects or is
     * costly to run.
     */
    public boolean getValueWithLocks() {
        if (!isInitialised) {
            synchronized (this) {
                if (!isInitialised) {
                    final boolean newVal = valueSupplier.getAsBoolean();
                    // Write order is important here due to volatile piggybacking
                    value = newVal;
                    isInitialised = true;
                    return newVal;
                }
            }
        }
        return value;
    }

    public boolean isInitialised() {
        return isInitialised;
    }
}
