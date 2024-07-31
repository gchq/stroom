/*
 * Copyright 2024 Crown Copyright
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

package stroom.util.shared.string;

import java.util.HashMap;

/**
 * A {@link HashMap} keyed with case-insensitive {@link CIKey} keys.
 */
public class CIHashMap<V> extends HashMap<CIKey, V> {

    public V put(final String key, final V value) {
        return super.put(CIKey.of(key), value);
    }

    public V get(final String key) {
        return super.get(CIKey.of(key));
    }

    public boolean containsStringKey(final String key) {
        return super.containsKey(CIKey.of(key));
    }
}
