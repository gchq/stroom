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

import stroom.util.logging.LogUtil;
import stroom.util.shared.Range;

import java.util.Objects;

/**
 * <mapUid><rangeStartInc><rangeEndExc>
 * <4 bytes><8 bytes><8 bytes>
 */
public class RangeStoreKey {

    private final UID mapUid;
    private final Range<Long> keyRange;

    public RangeStoreKey(final UID mapUid, final long from, final long to) {
        this(mapUid, new Range<>(from, to));
    }

    public static RangeStoreKey of(final UID mapUid, final long from, final long to) {
        return new RangeStoreKey(mapUid, from, to);
    }

    public RangeStoreKey(final UID mapUid, final Range<Long> keyRange) {
        this.mapUid = Objects.requireNonNull(mapUid);
        this.keyRange = Objects.requireNonNull(keyRange);
        if (!keyRange.isBounded()) {
            throw new RuntimeException(LogUtil.message("Only bounded ranges are supported, range: {}", keyRange));
        }
    }

    public UID getMapUid() {
        return mapUid;
    }

    public Range<Long> getKeyRange() {
        return keyRange;
    }

    public RangeStoreKey withMapUid(final UID newMapUid) {
        return new RangeStoreKey(newMapUid, keyRange);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RangeStoreKey that = (RangeStoreKey) o;
        return Objects.equals(mapUid, that.mapUid) &&
               Objects.equals(keyRange, that.keyRange);
    }

    @Override
    public int hashCode() {

        return Objects.hash(mapUid, keyRange);
    }

    @Override
    public String toString() {
        return "RangeStoreKey{" +
               "mapUid=" + mapUid +
               ", keyRange=" + keyRange +
               '}';
    }
}
