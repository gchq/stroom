/*
 * Copyright 2026 Crown Copyright
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


package stroom.gwt.workbench.framework.client.play;

import java.util.Objects;
import java.util.function.Supplier;

/// A value read when a step runs, with a label to show in the Interactions addon if it can't be
/// read, e.g. `getByRole("button").textContent`. [Query] gives values derived from elements, e.g.
/// `play.expect(play.getByRole("button").textContent()).toBe("Save")`.
///
/// @param <T> The type of the value.
public final class Value<T> implements Supplier<T> {

    private final String label;
    private final Supplier<T> supplier;

    private Value(final String label, final Supplier<T> supplier) {
        this.label = Objects.requireNonNull(label, "label");
        this.supplier = Objects.requireNonNull(supplier, "supplier");
    }

    /// @param label    How the value is shown if it can't be read, e.g. `saved.size()`.
    /// @param supplier Reads the value when a step runs.
    /// @param <T>      The type of the value.
    /// @return The value.
    public static <T> Value<T> of(final String label, final Supplier<T> supplier) {
        return new Value<>(label, supplier);
    }

    /// @return How the value is shown if it can't be read.
    public String getLabel() {
        return label;
    }

    /// @return The value, read now.
    @Override
    public T get() {
        return supplier.get();
    }

    @Override
    public String toString() {
        return label;
    }
}
