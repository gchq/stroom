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

import com.google.common.base.Preconditions;

import java.util.Objects;

/**
 * A surrogate key for a value that implements hashCode(). It is recommended that the
 * hashcode of the value is held on the instance rather than computing it on the fly
 * each time to save processing.
 */
public class ValueStoreKey {

    private static final short DEFAULT_UNIQUE_ID = 0;
    public static final short MIN_UNIQUE_ID = DEFAULT_UNIQUE_ID;
    public static final short MAX_UNIQUE_ID = Short.MAX_VALUE;

    // The hashcode of the value that this key points to
    private final long valueHashCode;
    // An ID to provide uniqueness in the event of a hash-clash
    private final short uniqueId;

    /**
     * @return A key with the lowest possible uniqueId for valueHashCode
     */
    public static ValueStoreKey lowestKey(final long valueHashCode) {
        return new ValueStoreKey(valueHashCode, MIN_UNIQUE_ID);
    }

    /**
     * @return A key with the highest possible uniqueId for valueHashCode
     */
    public static ValueStoreKey highestKey(final long valueHashCode) {
        return new ValueStoreKey(valueHashCode, MAX_UNIQUE_ID);
    }


    public ValueStoreKey(final long valueHashCode, final short uniqueId) {
        // Due to the way keys are sorted, negative unique ids are not supported
        Preconditions.checkArgument(uniqueId >= 0);
        this.valueHashCode = valueHashCode;
        this.uniqueId = uniqueId;
    }

    /**
     * @return A new Key instance with the next unique ID. Should be used with some form of
     * concurrency protection to avoid multiple keys with the same ID.
     */
    public ValueStoreKey nextKey() {
        if (uniqueId == MAX_UNIQUE_ID) {
            throw new RuntimeException(LogUtil.message(
                    "Unable to create the next key as the max ID {} has been reached",
                    MAX_UNIQUE_ID));
        }
        return new ValueStoreKey(valueHashCode, (short) (uniqueId + 1));
    }

    public long getValueHashCode() {
        return valueHashCode;
    }

    public short getUniqueId() {
        return uniqueId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ValueStoreKey key = (ValueStoreKey) o;
        return valueHashCode == key.valueHashCode &&
                uniqueId == key.uniqueId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(valueHashCode, uniqueId);
    }

    @Override
    public String toString() {
        return "Key{" +
                "valueHashCode=" + valueHashCode +
                ", uniqueId=" + uniqueId +
                '}';
    }
}
