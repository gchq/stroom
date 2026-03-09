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

package stroom.planb.impl.serde.temporalrangestate;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.planb.impl.data.TemporalRangeState.Key;
import stroom.planb.impl.serde.time.TimeSerde;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class IntegerRangeKeySerde implements TemporalRangeKeySerde {

    private static final UnsignedBytes UNSIGNED_BYTES = UnsignedBytesInstances.ofLength(4);

    private final ByteBuffer reusableWriteBuffer;
    private final ByteBuffers byteBuffers;
    private final TimeSerde timeSerde;
    private final int length;

    public IntegerRangeKeySerde(final ByteBuffers byteBuffers, final TimeSerde timeSerde) {
        this.byteBuffers = byteBuffers;
        this.timeSerde = timeSerde;
        length = Integer.BYTES + Integer.BYTES + timeSerde.getSize();
        reusableWriteBuffer = ByteBuffer.allocateDirect(length);
    }

    @Override
    public Key read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final long start = UNSIGNED_BYTES.get(byteBuffer);
        final long end = UNSIGNED_BYTES.get(byteBuffer);
        final Instant effectiveTime = timeSerde.read(byteBuffer);
        return new Key(start, end, effectiveTime);
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final Key key, final Consumer<ByteBuffer> consumer) {
//        byteBuffers.use(Byte.BYTES, byteBuffer -> {
//            byteBuffer.put(getByte(value));
//            byteBuffer.flip();
//            consumer.accept(byteBuffer);
//        });

        // We are in a single write transaction so should be able to reuse the same buffer again and again.
        reusableWriteBuffer.clear();
        writeInteger(key.getKeyStart(), reusableWriteBuffer);
        writeInteger(key.getKeyEnd(), reusableWriteBuffer);
        timeSerde.write(reusableWriteBuffer, key.getTime());
        reusableWriteBuffer.flip();
        consumer.accept(reusableWriteBuffer);
    }

    @Override
    public <R> R toBufferForGet(final Txn<ByteBuffer> txn,
                                final Key key,
                                final Function<Optional<ByteBuffer>, R> function) {
        return byteBuffers.use(length, byteBuffer -> {
            writeInteger(key.getKeyStart(), byteBuffer);
            writeInteger(key.getKeyEnd(), byteBuffer);
            timeSerde.write(byteBuffer, key.getTime());
            byteBuffer.flip();
            return function.apply(Optional.of(byteBuffer));
        });
    }

    @Override
    public <R> R toKeyStart(final long key, final Function<ByteBuffer, R> function) {
        return byteBuffers.use(length, byteBuffer -> {
            writeInteger(key, byteBuffer);
            ByteBufferUtils.padMax(byteBuffer, Integer.BYTES);
            byteBuffer.flip();
            return function.apply(byteBuffer);
        });
    }

    private void writeInteger(final long l, final ByteBuffer byteBuffer) {
        UNSIGNED_BYTES.put(byteBuffer, l);
    }
}
