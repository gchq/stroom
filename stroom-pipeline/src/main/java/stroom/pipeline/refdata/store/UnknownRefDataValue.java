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

import stroom.bytebuffer.ByteBufferUtils;

import java.nio.ByteBuffer;

public class UnknownRefDataValue implements RefDataValue {

    public static final byte TYPE_ID = -1;

    private final ByteBuffer value;

    public UnknownRefDataValue(final ByteBuffer value) {
        this.value = value;
    }

    public ByteBuffer getValue() {
        return value;
    }

    /**
     * @return The hashcode of just the underlying value that this object wraps
     * rather than hashcode of the whole object.
     */
    @Override
    public long getValueHashCode(final ValueStoreHashAlgorithm valueStoreHashAlgorithm) {
        return 0;
    }

    /**
     * @return A code to represent the class of the implementation,
     * unique within all sub-classes of {@link RefDataValue}
     */
    @Override
    public byte getTypeId() {
        return TYPE_ID;
    }

    @Override
    public String toString() {
        return ByteBufferUtils.byteBufferToHex(value);
    }

    @Override
    public boolean isNullValue() {
        return value == null || value.remaining() == 0;
    }
}
