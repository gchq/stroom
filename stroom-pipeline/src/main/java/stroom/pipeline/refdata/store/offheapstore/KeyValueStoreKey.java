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

package stroom.pipeline.refdata.store.offheapstore;

import java.util.Objects;

/**
 * < mapUid >< key >
 * < 4 bytes >< ? bytes >
 */
public class KeyValueStoreKey {

    private final UID mapUid;
    private final String key;

    public KeyValueStoreKey(final UID mapUid, final String key) {
        this.mapUid = Objects.requireNonNull(mapUid);
        this.key = Objects.requireNonNull(key);
    }

    public UID getMapUid() {
        return mapUid;
    }

    public KeyValueStoreKey withMapUid(final UID newMapUid) {
        return new KeyValueStoreKey(newMapUid, key);
    }

    public String getKey() {
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
        final KeyValueStoreKey that = (KeyValueStoreKey) o;
        return Objects.equals(mapUid, that.mapUid) &&
                Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mapUid, key);
    }

    @Override
    public String toString() {
        return "KeyValueStoreKey{" +
                "mapUid=" + mapUid +
                ", key='" + key + '\'' +
                '}';
    }
}
