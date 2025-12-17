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

package stroom.pipeline.refdata.store.offheapstore.serdes;

import stroom.bytebuffer.PooledByteBufferOutputStream;
import stroom.lmdb.serde.Serde;
import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.NullValue;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.StagingValue;
import stroom.pipeline.refdata.store.StagingValueImpl;
import stroom.pipeline.refdata.store.StringValue;
import stroom.pipeline.refdata.store.ValueStoreHashAlgorithm;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import static stroom.pipeline.refdata.store.StagingValue.VALUE_HASH_OFFSET;

/**
 * <pre>{@code
 * <type ID int><hash code long><value bytes................>
 * }</pre>
 */
public class StagingValueSerde implements Serde<StagingValue> {

    private static final int TYPE_ID_OFFSET = 0;
//    private static final int VALUE_HASH_OFFSET = TYPE_ID_OFFSET + Integer.BYTES;
//    private static final int VALUE_OFFSET = VALUE_HASH_OFFSET + Long.BYTES;
    private static final int META_LENGTH = Integer.BYTES + Long.BYTES;

    @Override
    public StagingValue deserialize(final ByteBuffer byteBuffer) {
        // Just wraps the buffer with no copy, simples
        return new StagingValueImpl(byteBuffer);
    }

    @Override
    public ByteBuffer serialize(final Supplier<ByteBuffer> byteBufferSupplier, final StagingValue stagingValue) {
        Objects.requireNonNull(stagingValue);
        // Don't want to do a buffer copy into the supplied buffer, just use the one we already have
        return stagingValue.getFullByteBuffer();
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final StagingValue stagingValue) {
        // No need for supplier as it just wraps a buffer.
        final ByteBuffer valueByteBuffer = serialize((Supplier<ByteBuffer>) null, stagingValue);
        // Need to copy into supplied buffer
        byteBuffer.put(valueByteBuffer);
        byteBuffer.flip();
        valueByteBuffer.rewind();
    }

    @Override
    public ByteBuffer serialize(final PooledByteBufferOutputStream pooledByteBufferOutputStream,
                                final StagingValue stagingValue) {
        throw new UnsupportedOperationException();
    }

    public static byte extractTypeId(final ByteBuffer byteBuffer) {
        return byteBuffer.get(TYPE_ID_OFFSET);
    }

    public static long extractValueHash(final ByteBuffer byteBuffer) {
        return byteBuffer.getLong(VALUE_HASH_OFFSET);
    }

    /**
     * For testing really
     */
    public static StagingValue convert(final Function<Integer, ByteBuffer> byteBufferSupplier,
                                       final ValueStoreHashAlgorithm valueStoreHashAlgorithm,
                                       final StringValue stringValue) {
        final byte[] valueBytes = StringValueSerde.toBytes(stringValue);
        final int capacity = META_LENGTH + valueBytes.length;
        final ByteBuffer byteBuffer = byteBufferSupplier.apply(capacity);
        putMetaData(byteBuffer, stringValue, valueStoreHashAlgorithm);
        byteBuffer.put(valueBytes);
        byteBuffer.flip();
        return new StagingValueImpl(byteBuffer);
    }

    /**
     * For testing really
     */
    public static StagingValue convert(final Function<Integer, ByteBuffer> byteBufferSupplier,
                                       final ValueStoreHashAlgorithm valueStoreHashAlgorithm,
                                       final FastInfosetValue fastInfosetValue) {
        final ByteBuffer valueBuffer = fastInfosetValue.getByteBuffer();

        final int capacity = META_LENGTH + valueBuffer.remaining();
        final ByteBuffer byteBuffer = byteBufferSupplier.apply(capacity);
        putMetaData(byteBuffer, fastInfosetValue, valueStoreHashAlgorithm);
        byteBuffer.put(valueBuffer);
        byteBuffer.flip();
        return new StagingValueImpl(byteBuffer);
    }

    /**
     * For testing really
     */
    public static StagingValue convert(final Function<Integer, ByteBuffer> byteBufferSupplier,
                                       final ValueStoreHashAlgorithm valueStoreHashAlgorithm,
                                       final NullValue nullValue) {
        final int capacity = META_LENGTH;
        final ByteBuffer byteBuffer = byteBufferSupplier.apply(capacity);
        putMetaData(byteBuffer, nullValue, valueStoreHashAlgorithm);
        byteBuffer.flip();
        return new StagingValueImpl(byteBuffer);
    }

    /**
     * For testing really
     */
    public static StagingValue convert(final Function<Integer, ByteBuffer> byteBufferSupplier,
                                       final ValueStoreHashAlgorithm valueStoreHashAlgorithm,
                                       final RefDataValue refDataValue) {
        if (refDataValue instanceof StringValue) {
            return StagingValueSerde.convert(
                    byteBufferSupplier,
                    valueStoreHashAlgorithm,
                    (StringValue) refDataValue);
        } else if (refDataValue instanceof FastInfosetValue) {
            return StagingValueSerde.convert(
                    byteBufferSupplier,
                    valueStoreHashAlgorithm,
                    (FastInfosetValue) refDataValue);
        } else if (refDataValue instanceof NullValue) {
            return StagingValueSerde.convert(
                    byteBufferSupplier,
                    valueStoreHashAlgorithm,
                    (NullValue) refDataValue);
        } else {
            throw new IllegalArgumentException("Unexpected type " + refDataValue.getClass().getSimpleName());
        }
    }

    private static void putMetaData(final ByteBuffer byteBuffer,
                                    final RefDataValue refDataValue,
                                    final ValueStoreHashAlgorithm valueStoreHashAlgorithm) {
        byteBuffer.put(refDataValue.getTypeId());
        byteBuffer.putLong(refDataValue.getValueHashCode(valueStoreHashAlgorithm));
    }
}
