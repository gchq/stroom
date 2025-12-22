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
import stroom.util.logging.LogUtil;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Supplier;

public class FastInfosetValue implements RefDataValue {

    /**
     * MUST not change this else it is stored in the ref store. MUST be unique over all
     * {@link RefDataValue} impls.
     */
    public static final byte TYPE_ID = 1;

    private final ByteBuffer fastInfosetByteBuffer;
    private volatile Long fastInfosetValueHash = null;
    // Hold this so we know what was used to compute the hash
    private volatile ValueStoreHashAlgorithm valueStoreHashAlgorithm = null;

    public FastInfosetValue(final ByteBuffer fastInfosetByteBuffer) {
        this.fastInfosetByteBuffer = fastInfosetByteBuffer;
    }

    public FastInfosetValue(final ByteBuffer fastInfosetByteBuffer,
                            final long valueHash,
                            final ValueStoreHashAlgorithm valueStoreHashAlgorithm) {
        this.fastInfosetByteBuffer = Objects.requireNonNull(fastInfosetByteBuffer);
        this.fastInfosetValueHash = valueHash;
        this.valueStoreHashAlgorithm = Objects.requireNonNull(valueStoreHashAlgorithm);
    }

    public FastInfosetValue(final StagingValue stagingValue) {
        final int typeId = stagingValue.getTypeId();
        if (TYPE_ID != typeId) {
            throw new RuntimeException(LogUtil.message("Expecting type {}, got {}", FastInfosetValue.TYPE_ID, typeId));
        }
        this.fastInfosetByteBuffer = stagingValue.getValueBuffer();
        this.fastInfosetValueHash = stagingValue.getValueHashCode();
    }

    public static FastInfosetValue wrap(final ByteBuffer fastInfosetByteBuffer) {
        return new FastInfosetValue(fastInfosetByteBuffer);
    }

    @Override
    public byte getTypeId() {
        return TYPE_ID;
    }

    @Override
    public boolean isNullValue() {
        return fastInfosetByteBuffer.remaining() == 0;
    }

    @Override
    public long getValueHashCode(final ValueStoreHashAlgorithm valueStoreHashAlgorithm) {
        // Lazily compute the hash and hold for future use these values can be quite big.
        // This will mostly be used during a load which is single threaded so no need to
        // avoid a synchronize block at the risk of getting the same hash value twice.

        if (fastInfosetValueHash == null) {
            fastInfosetValueHash = valueStoreHashAlgorithm.hash(fastInfosetByteBuffer);
            this.valueStoreHashAlgorithm = valueStoreHashAlgorithm;
            return fastInfosetValueHash;
        } else if (valueStoreHashAlgorithm != null
                   && !Objects.equals(this.valueStoreHashAlgorithm, valueStoreHashAlgorithm)) {
            // If hash algo doesn't match then compute with the provided one
            return valueStoreHashAlgorithm.hash(fastInfosetByteBuffer);
        } else {
            return fastInfosetValueHash;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FastInfosetValue that = (FastInfosetValue) o;
        return Objects.equals(fastInfosetByteBuffer, that.fastInfosetByteBuffer);
    }

    @Override
    public int hashCode() {
        return ByteBufferUtils.basicHashCode(fastInfosetByteBuffer);
    }

    public ByteBuffer getByteBuffer() {
        return fastInfosetByteBuffer;
    }

    public RefDataValue copy(final Supplier<ByteBuffer> byteBufferSupplier) {
        final ByteBuffer newByteBuffer = byteBufferSupplier.get();
        ByteBufferUtils.copy(this.fastInfosetByteBuffer, newByteBuffer);
        return new FastInfosetValue(newByteBuffer, fastInfosetValueHash, valueStoreHashAlgorithm);
    }

    public int size() {
        return fastInfosetByteBuffer.limit() - fastInfosetByteBuffer.position();
    }

    public boolean isDirect() {
        return fastInfosetByteBuffer.isDirect();
    }

    @Override
    public String toString() {
        return "FastInfosetValue{" +
               "fastInfosetByteBuffer=" + fastInfosetByteBuffer +
               '}';
    }
}
