/*
 * Copyright 2016 Crown Copyright
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

package stroom.refdata;

import java.io.Serializable;

public class MapStoreKey implements Serializable {
    private static final long serialVersionUID = -7153262432069953060L;

    private final String mapName;
    private final String keyName;
    private final int hashCode;

    public MapStoreKey(final String mapName, final String keyName) {
        this.mapName = mapName;
        this.keyName = keyName;

        int code = 31;
        if (mapName == null) {
            code = code * 31;
        } else {
            code = code * 31 + mapName.hashCode();
        }
        if (keyName == null) {
            code = code * 31;
        } else {
            code = code * 31 + keyName.hashCode();
        }
        hashCode = code;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (o == null || !(o instanceof MapStoreKey)) {
            return false;
        }

        final MapStoreKey mapStoreKey = (MapStoreKey) o;

        if (mapName != null && !mapName.equals(mapStoreKey.mapName)) {
            return false;
        }

        if (mapStoreKey.mapName != null && !mapStoreKey.mapName.equals(mapName)) {
            return false;
        }

        if (keyName != null && !keyName.equals(mapStoreKey.keyName)) {
            return false;
        }

        return !(mapStoreKey.keyName != null && !mapStoreKey.keyName.equals(keyName));

    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return mapName + ":" + keyName;
    }
}
