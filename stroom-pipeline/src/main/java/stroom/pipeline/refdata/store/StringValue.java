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

package stroom.pipeline.refdata.store;

import stroom.pipeline.refdata.store.offheapstore.serdes.StringValueSerde;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.nio.ByteBuffer;
import java.util.Objects;

public class StringValue implements RefDataValue {

    /**
     * MUST not change this else it is stored in the ref store. MUST be unique over all
     * {@link RefDataValue} impls.
     */
    public static final byte TYPE_ID = 0;

    private final String value;
    private volatile Long valueHash = null;

    public StringValue(final String value) {
        this.value = value;
    }

    public StringValue(final StagingValue stagingValue) {
        final int typeId = stagingValue.getTypeId();
        if (TYPE_ID != typeId) {
            throw new RuntimeException(LogUtil.message("Expecting type {}, got {}", FastInfosetValue.TYPE_ID, typeId));
        }
        final ByteBuffer valueBuffer = stagingValue.getValueBuffer();
        this.value = StringValueSerde.extractValue(valueBuffer);
        this.valueHash = stagingValue.getValueHashCode();
    }

    public static StringValue of(final String value) {
        return new StringValue(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final StringValue that = (StringValue) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public long getValueHashCode(final ValueStoreHashAlgorithm valueStoreHashAlgorithm) {
        // Lazily compute the hash and hold for future use.
        // This will mostly be used during a load which is single threaded so no need to
        // avoid a synch at the risk of getting the same hash value twice.
        if (valueHash == null) {
            valueHash = valueStoreHashAlgorithm.hash(value);
        }
        return valueHash;
    }

    @Override
    public byte getTypeId() {
        return TYPE_ID;
    }

    @Override
    public boolean isNullValue() {
        return NullSafe.isBlankString(value);
    }

    @Override
    public String toString() {
        return "StringValue{" +
               "value='" + value + '\'' +
               '}';
    }
}
