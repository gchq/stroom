/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.offheapstore;

import stroom.refdata.saxevents.uid.UID;

import java.util.Objects;

public class KeyValueStoreKey {

    private final UID mapUid;
    private final String key;
    private final long effectiveTimeEpochMs;

    KeyValueStoreKey(final UID mapUid, final String key, final long effectiveTimeEpochMs) {
        this.mapUid = mapUid;
        this.key = key;
        this.effectiveTimeEpochMs = effectiveTimeEpochMs;
    }

    UID getMapUid() {
        return mapUid;
    }

    String getKey() {
        return key;
    }

    long getEffectiveTimeEpochMs() {
        return effectiveTimeEpochMs;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final KeyValueStoreKey that = (KeyValueStoreKey) o;
        return effectiveTimeEpochMs == that.effectiveTimeEpochMs &&
                Objects.equals(mapUid, that.mapUid) &&
                Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {

        return Objects.hash(mapUid, key, effectiveTimeEpochMs);
    }

    @Override
    public String toString() {
        return "KeyValueStoreKey{" +
                "mapUid=" + mapUid +
                ", key='" + key + '\'' +
                ", effectiveTimeEpochMs=" + effectiveTimeEpochMs +
                '}';
    }
}
