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

package stroom.widget.component.client;

import java.util.Objects;
import java.util.function.BinaryOperator;

/// The name of a value that a controller provides to the controllers it owns, however deep (see
/// [ControllerNode#provide(ContextKey, ContextValue)]). Keys are compared by identity, so each is
/// made once, as a constant (see [Contexts]).
///
/// @param <T> The type of the value.
public final class ContextKey<T> {

    private final String name;
    private final T defaultValue;
    private final BinaryOperator<T> narrowing;

    private ContextKey(final String name, final T defaultValue, final BinaryOperator<T> narrowing) {
        this.name = Objects.requireNonNull(name, "name");
        this.defaultValue = defaultValue;
        this.narrowing = narrowing;
    }

    /// Makes a key whose value is the nearest provider's.
    ///
    /// @param name         The key's name, for diagnostics.
    /// @param defaultValue The value when no owner provides one, e.g. in a test with no owner.
    /// @param <T>          The type of the value.
    /// @return The key.
    public static <T> ContextKey<T> of(final String name, final T defaultValue) {
        return new ContextKey<>(name, defaultValue, null);
    }

    /// Makes a key whose providers can only narrow what the providers above them give, e.g.
    /// read-only, which a nested editor can make true for its subtree but never false within a
    /// read-only document.
    ///
    /// @param name         The key's name, for diagnostics.
    /// @param defaultValue The value when no owner provides one.
    /// @param narrowing    Combines the value from above (first) with a provider's own (second),
    ///                     e.g. `(outer, inner) -> outer || inner`.
    /// @param <T>          The type of the value.
    /// @return The key.
    public static <T> ContextKey<T> narrowing(final String name,
                                              final T defaultValue,
                                              final BinaryOperator<T> narrowing) {
        return new ContextKey<>(name, defaultValue, Objects.requireNonNull(narrowing, "narrowing"));
    }

    /// @return The key's name.
    public String getName() {
        return name;
    }

    /// @return The value when no owner provides one.
    public T getDefaultValue() {
        return defaultValue;
    }

    /// @return Whether every provider on the way up counts, rather than only the nearest.
    boolean isNarrowing() {
        return narrowing != null;
    }

    /// Combines a value from above with the next provider's.
    ///
    /// @param outer The value from the providers above.
    /// @param inner The next provider's own value.
    /// @return The combined value.
    T narrow(final T outer, final T inner) {
        return narrowing.apply(outer, inner);
    }

    @Override
    public String toString() {
        return name;
    }
}
