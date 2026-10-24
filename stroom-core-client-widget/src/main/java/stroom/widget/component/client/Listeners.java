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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/// The listeners of something a controller tells more than one listener about. Most controllers
/// have one listener, their owner, and take a single callback instead (e.g. `setOnChange`).
///
/// Each [#add(Consumer)] returns a registration for the listener's own controller to own (see
/// [ControllerNode#add(HandlerRegistration)]), so that neither side keeps the other once either
/// is disposed.
///
/// @param <T> What the listeners are told.
public final class Listeners<T> {

    private final List<Listener<T>> listeners = new ArrayList<>();

    /// Adds a listener.
    ///
    /// @param listener What to tell.
    /// @return A registration that removes it.
    public HandlerRegistration add(final Consumer<T> listener) {
        final Listener<T> entry = new Listener<>(listener);
        listeners.add(entry);
        return () -> {
            entry.removed = true;
            listeners.remove(entry);
        };
    }

    /// Tells every listener, in the order they were added. A listener added meanwhile isn't told
    /// this time; one removed meanwhile isn't told if it hasn't been already.
    ///
    /// @param value What to tell them.
    /// @throws RuntimeException The first failure, once every listener has been told (one
    ///                          failing mustn't stop the rest).
    public void fire(final T value) {
        RuntimeException failure = null;
        for (final Listener<T> entry : new ArrayList<>(listeners)) {
            if (!entry.removed) {
                try {
                    entry.consumer.accept(value);
                } catch (final RuntimeException e) {
                    failure = failure == null
                            ? e
                            : failure;
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /// @return The number of listeners.
    public int size() {
        return listeners.size();
    }

    /// @return Whether there are no listeners.
    public boolean isEmpty() {
        return listeners.isEmpty();
    }

    private static final class Listener<T> {

        private final Consumer<T> consumer;
        private boolean removed;

        private Listener(final Consumer<T> consumer) {
            this.consumer = consumer;
        }
    }
}
