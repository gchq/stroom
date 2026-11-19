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

import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.Objects;

/// A value a controller provides to the controllers it owns (see
/// [ControllerNode#provide(ContextKey, ContextValue)]), e.g. whether its document is read-only.
/// Setting it tells every controller that watches it.
///
/// @param <T> The type of the value.
public final class ContextValue<T> {

    private final Listeners<T> listeners = new Listeners<>();
    private T value;

    /// @param value The first value.
    public ContextValue(final T value) {
        this.value = value;
    }

    /// @param value The first value.
    /// @param <T>   The type of the value.
    /// @return A value holding it.
    public static <T> ContextValue<T> of(final T value) {
        return new ContextValue<>(value);
    }

    /// @return The value.
    public T get() {
        return value;
    }

    /// Changes the value, telling the watchers if it is different.
    ///
    /// @param value The new value.
    public void set(final T value) {
        if (!Objects.equals(this.value, value)) {
            this.value = value;
            listeners.fire(value);
        }
    }

    /// Asks to be told when the value changes.
    ///
    /// @param onChange What to run.
    /// @return A registration that stops it.
    HandlerRegistration subscribe(final Runnable onChange) {
        return listeners.add(changed -> onChange.run());
    }

    /// @return The number of subscribers.
    int subscriberCount() {
        return listeners.size();
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
