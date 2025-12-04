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

package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class WebSocketMessage {

    // We have to delegate to a map rather than just extend HashMap as RestyGWT can't cope
    // with it. Makes the json a bit meh, but not the end of the world.
    @JsonProperty
    private final Map<String, Object> items;

    @JsonCreator
    public WebSocketMessage(@JsonProperty("items") final Map<String, Object> items) {
        this.items = items;
    }

    // Needed for serialisation
    Map<String, Object> getItems() {
        return items;
    }

    /**
     * Create a {@link WebSocketMessage} containing a single key/value pair.
     */
    public static WebSocketMessage of(final String k1, final Object v1) {
        final Map<String, Object> map = new HashMap<>();
        map.put(k1, v1);
        return new WebSocketMessage(map);
    }

    /**
     * Create a {@link WebSocketMessage} containing two key/value pairs.
     */
    public static WebSocketMessage of(final String k1, final Object v1,
                                      final String k2, final Object v2) {
        final Map<String, Object> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return new WebSocketMessage(map);
    }

    /**
     * Create a {@link WebSocketMessage} containing three key/value pairs.
     */
    public static WebSocketMessage of(final String k1, final Object v1,
                                      final String k2, final Object v2,
                                      final String k3, final Object v3) {
        final Map<String, Object> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        return new WebSocketMessage(map);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Gets the value of the item with the specified key. The caller will need to cast
     * the value to its required type.
     *
     * @param key
     * @return The value or null if the key is not known.
     */
    public Object get(final String key) {
        return items.get(key);
    }

    public Set<String> keySet() {
        return items.keySet();
    }

    public Collection<Object> values() {
        return items.values();
    }

    public Set<Entry<String, Object>> entrySet() {
        return items.entrySet();
    }

    /**
     * Perform action on each item in the {@link WebSocketMessage}
     */
    public void forEach(final BiConsumer<? super String, ? super Object> action) {
        items.forEach(action);
    }

    @Override
    public String toString() {
        return "WebSocketMessage{" +
                "items=" + items +
                '}';
    }
}
