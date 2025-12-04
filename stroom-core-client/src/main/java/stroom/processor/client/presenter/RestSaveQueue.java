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

package stroom.processor.client.presenter;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public abstract class RestSaveQueue<K, V> implements HasHandlers {

    private final EventBus eventBus;
    private final Map<K, Boolean> setting = new HashMap<>();
    private final Map<K, V> nextValue = new HashMap<>();

    public RestSaveQueue(final EventBus eventBus) {
        this.eventBus = eventBus;
    }

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

    protected abstract void doAction(final K key, final V value, final Consumer<K> consumer);

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
