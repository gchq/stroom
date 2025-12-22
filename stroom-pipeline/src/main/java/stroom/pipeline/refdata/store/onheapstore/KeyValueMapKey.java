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

package stroom.pipeline.refdata.store.onheapstore;

import stroom.pipeline.refdata.store.MapDefinition;

import java.util.Objects;

class KeyValueMapKey {

    private final MapDefinition mapDefinition;
    private final String key;
    private final int hashCode;

    KeyValueMapKey(final MapDefinition mapDefinition, final String key) {
        this.mapDefinition = mapDefinition;
        this.key = key;
        // pre-compute the hash
        this.hashCode = Objects.hash(mapDefinition, key);
    }

    MapDefinition getMapDefinition() {
        return mapDefinition;
    }

    String getKey() {
        return key;
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final KeyValueMapKey that = (KeyValueMapKey) o;
        return Objects.equals(mapDefinition, that.mapDefinition) &&
               Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return "KeyValueMapKey{" +
               "mapDefinition=" + mapDefinition +
               ", key='" + key + '\'' +
               '}';
    }
}
