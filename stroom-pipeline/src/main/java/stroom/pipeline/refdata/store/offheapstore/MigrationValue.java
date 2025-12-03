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

import stroom.bytebuffer.ByteBufferUtils;
import stroom.pipeline.refdata.store.StagingValue;
import stroom.pipeline.refdata.store.ValueStoreHashAlgorithm;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Supplier;

class MigrationValue implements StagingValue {

    private final byte typeId;
    private final ByteBuffer valueBuffer;
    private final ValueStoreHashAlgorithm valueStoreHashAlgorithm;
    private volatile Long valueHash = null;

    MigrationValue(final byte typeId,
                   final ByteBuffer valueBuffer,
                   final ValueStoreHashAlgorithm valueStoreHashAlgorithm) {
        this(typeId, valueBuffer, valueStoreHashAlgorithm, null);
    }

    private MigrationValue(final byte typeId,
                           final ByteBuffer valueBuffer,
                           final ValueStoreHashAlgorithm valueStoreHashAlgorithm,
                           final Long valueHash) {
        this.typeId = typeId;
        this.valueBuffer = valueBuffer;
        this.valueStoreHashAlgorithm = valueStoreHashAlgorithm;
        this.valueHash = valueHash;
    }

    @Override
    public long getValueHashCode(final ValueStoreHashAlgorithm valueStoreHashAlgorithm) {
        Objects.requireNonNull(valueStoreHashAlgorithm);
        if (valueStoreHashAlgorithm.getClass().equals(this.valueStoreHashAlgorithm.getClass())) {
            return getValueHashCode();
        } else {
            return valueStoreHashAlgorithm.hash(valueBuffer);
        }
    }

    @Override
    public boolean isNullValue() {
        return false;
    }

    @Override
    public long getValueHashCode() {
        if (valueHash == null) {
            valueHash = valueStoreHashAlgorithm.hash(valueBuffer);
        }
        return valueHash;
    }

    @Override
    public byte getTypeId() {
        return typeId;
    }

    @Override
    public int size() {
        return valueBuffer.remaining();
    }

    @Override
    public ByteBuffer getValueBuffer() {
        return valueBuffer;
    }

    @Override
    public ByteBuffer getFullByteBuffer() {
        return valueBuffer;
    }

    @Override
    public StagingValue copy(final Supplier<ByteBuffer> byteBufferSupplier) {
        final ByteBuffer newByteBuffer = byteBufferSupplier.get();
        ByteBufferUtils.copy(getFullByteBuffer(), newByteBuffer);
        return new MigrationValue(typeId, newByteBuffer, valueStoreHashAlgorithm, valueHash);
    }
}
