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

package stroom.planb.impl.serde.session;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.data.Session;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.KeyLength;
import stroom.planb.impl.serde.keyprefix.KeyPrefix;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class LimitedStringSessionSerde implements SessionSerde {

    private final ByteBuffer reusableWriteBuffer;
    private final ByteBuffers byteBuffers;
    private final TimeSerde timeSerde;
    private final int timeLength;
    private final int limit;

    public LimitedStringSessionSerde(final ByteBuffers byteBuffers,
                                     final int limit,
                                     final TimeSerde timeSerde) {
        this.byteBuffers = byteBuffers;
        this.timeSerde = timeSerde;
        this.timeLength = timeSerde.getSize() + timeSerde.getSize();
        this.limit = Db.MAX_KEY_LENGTH - timeLength;
        reusableWriteBuffer = ByteBuffer.allocateDirect(limit);
    }

    @Override
    public Session read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final ByteBuffer startSlice = byteBuffer.slice(byteBuffer.remaining() - timeLength,
                timeSerde.getSize());
        final ByteBuffer endSlice = byteBuffer.slice(byteBuffer.remaining() - timeSerde.getSize(),
                timeSerde.getSize());
        final Instant start = timeSerde.read(startSlice);
        final Instant end = timeSerde.read(endSlice);

        // Slice off the key.
        final ByteBuffer keySlice = byteBuffer.slice(0,
                byteBuffer.remaining() - timeLength);

        // Read via lookup.
        final Val key = ValString.create(ByteBufferUtils.toString(keySlice));
        return new Session(KeyPrefix.create(key), start, end);
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final Session session, final Consumer<ByteBuffer> consumer) {
        final byte[] bytes = session.getPrefix().toString().getBytes(StandardCharsets.UTF_8);
        KeyLength.check(bytes.length, limit);

        // We are in a single write transaction so should be able to reuse the same buffer again and again.
        reusableWriteBuffer.clear();
        reusableWriteBuffer.put(bytes);
        timeSerde.write(reusableWriteBuffer, session.getStart());
        timeSerde.write(reusableWriteBuffer, session.getEnd());
        reusableWriteBuffer.flip();
        consumer.accept(reusableWriteBuffer);
    }

    @Override
    public <R> R toBufferForGet(final Txn<ByteBuffer> txn,
                                final Session session,
                                final Function<Optional<ByteBuffer>, R> function) {
        final byte[] bytes = session.getPrefix().toString().getBytes(StandardCharsets.UTF_8);
        KeyLength.check(bytes.length, limit);
        return byteBuffers.use(bytes.length + timeLength, byteBuffer -> {
            byteBuffer.put(bytes);
            timeSerde.write(byteBuffer, session.getStart());
            timeSerde.write(byteBuffer, session.getEnd());
            byteBuffer.flip();
            return function.apply(Optional.of(byteBuffer));
        });
    }
}
