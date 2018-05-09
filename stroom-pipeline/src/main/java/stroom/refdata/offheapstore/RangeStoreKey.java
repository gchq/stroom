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

import stroom.entity.shared.Range;
import stroom.refdata.saxevents.uid.UID;
import stroom.util.logging.LambdaLogger;

import java.util.Objects;

/**
 * <mapUid><effectiveTimeEpochMs><rangeStartInc><rangeEndExc>
 */
public class RangeStoreKey {

    private final UID mapUid;
    private final long effectiveTimeEpochMs;
    private final Range<Long> keyRange;


    RangeStoreKey(final UID mapUid, final long effectiveTimeEpochMs, final Range<Long> keyRange) {
        this.mapUid = Objects.requireNonNull(mapUid);
        this.effectiveTimeEpochMs = effectiveTimeEpochMs;
        this.keyRange = Objects.requireNonNull(keyRange);
        if (!keyRange.isBounded()) {
            throw new RuntimeException(LambdaLogger.buildMessage("Only bounded ranges are supported, range: {}", keyRange));
        }
    }

    UID getMapUid() {
        return mapUid;
    }

    long getEffectiveTimeEpochMs() {
        return effectiveTimeEpochMs;
    }

    Range<Long> getKeyRange() {
        return keyRange;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final RangeStoreKey that = (RangeStoreKey) o;
        return effectiveTimeEpochMs == that.effectiveTimeEpochMs &&
                Objects.equals(mapUid, that.mapUid) &&
                Objects.equals(keyRange, that.keyRange);
    }

    @Override
    public int hashCode() {

        return Objects.hash(mapUid, effectiveTimeEpochMs, keyRange);
    }

    @Override
    public String toString() {
        return "RangeStoreKey{" +
                "mapUid=" + mapUid +
                ", effectiveTimeEpochMs=" + effectiveTimeEpochMs +
                ", keyRange=" + keyRange +
                '}';
    }
}
