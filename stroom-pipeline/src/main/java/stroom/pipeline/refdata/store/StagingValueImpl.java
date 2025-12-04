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
import stroom.pipeline.refdata.store.offheapstore.serdes.StagingValueSerde;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A staging value held in byte form
 * <pre>{@code
 * <type ID int><hash code long><value bytes................>
 * }</pre>
 */
public class StagingValueImpl implements StagingValue {

    private final ByteBuffer fullByteBuffer;
    private final ByteBuffer valueByteBufferView;

    public StagingValueImpl(final ByteBuffer fullByteBuffer) {
        this.fullByteBuffer = Objects.requireNonNull(fullByteBuffer);
        this.valueByteBufferView = fullByteBuffer.slice(
                VALUE_OFFSET,
                fullByteBuffer.remaining() - StagingValue.META_LENGTH);
    }

    @Override
    public long getValueHashCode() {
        return StagingValueSerde.extractValueHash(fullByteBuffer);
    }

    @Override
    public long getValueHashCode(final ValueStoreHashAlgorithm valueStoreHashAlgorithm) {
        return valueStoreHashAlgorithm.hash(valueByteBufferView);
    }

    public byte getTypeId() {
        return StagingValueSerde.extractTypeId(fullByteBuffer);
    }

    @Override
    public boolean isNullValue() {
        return NullValue.TYPE_ID == getTypeId()
                || valueByteBufferView.remaining() == 0;
    }

    @Override
    public ByteBuffer getValueBuffer() {
        return valueByteBufferView;
    }

    @Override
    public ByteBuffer getFullByteBuffer() {
        return fullByteBuffer;
    }

    @Override
    public int size() {
        return fullByteBuffer.remaining();
    }

    /**
     * Copies the contents of this into a new {@link StagingValue} backed by the supplied buffer.
     */
    @Override
    public StagingValue copy(final Supplier<ByteBuffer> byteBufferSupplier) {
        final ByteBuffer newByteBuffer = byteBufferSupplier.get();
        ByteBufferUtils.copy(getFullByteBuffer(), newByteBuffer);
        return new StagingValueImpl(newByteBuffer);
    }

    @Override
    public String toString() {
        return "StagingValueImpl{" +
                "typeId=" + getTypeId() +
                "valueHash=" + getValueHashCode() +
                "byteBuffer=" + ByteBufferUtils.byteBufferInfo(fullByteBuffer) +
                ", valueByteBufferView=" + ByteBufferUtils.byteBufferInfo(valueByteBufferView) +
                '}';
    }
}
