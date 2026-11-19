/*
 * Copyright 2020 Crown Copyright
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

package stroom.processor.client.component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/// Saves values one at a time for each key: a value set while the key's last is being saved is
/// saved after it, and only the latest of several such is.
///
/// @param <K> The type of a key, e.g. a processor's id.
/// @param <V> The type of a value, e.g. whether it is enabled.
public abstract class RestSaveQueue<K, V> {

    private final Map<K, Boolean> setting = new HashMap<>();
    private final Map<K, V> nextValue = new HashMap<>();

    /// Saves a value, now or once the key's value being saved is saved.
    ///
    /// @param key   The key.
    /// @param value The value.
    public void setValue(final K key, final V value) {
        nextValue.put(key, value);
        if (!setting.containsKey(key)) {
            setting.put(key, true);
            tryAndSetValue(key);
        }
    }

    private void tryAndSetValue(final K key) {
        final V value = nextValue.remove(key);
        if (value != null) {
            doAction(key, value, this::tryAndSetValue);

        } else {
            setting.remove(key);
        }
    }

    /// Saves a value.
    ///
    /// @param key      The key.
    /// @param value    The value.
    /// @param consumer Given the key once the value is saved, or saving it fails.
    protected abstract void doAction(final K key, final V value, final Consumer<K> consumer);
}
