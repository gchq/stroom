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

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.data.Session;
import stroom.planb.impl.db.HashLookupDb;
import stroom.planb.impl.db.HashLookupRecorder;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.planb.impl.db.UsedLookupsRecorderProxy;
import stroom.planb.impl.serde.keyprefix.KeyPrefix;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.planb.impl.serde.val.ValSerdeUtil.Addition;
import stroom.query.language.functions.Val;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class HashLookupSessionSerde implements SessionSerde {

    private final HashLookupDb hashLookupDb;
    private final ByteBuffers byteBuffers;
    private final TimeSerde timeSerde;
    private final int timeLength;

    public HashLookupSessionSerde(final HashLookupDb hashLookupDb,
                                  final ByteBuffers byteBuffers,
                                  final TimeSerde timeSerde) {
        this.hashLookupDb = hashLookupDb;
        this.byteBuffers = byteBuffers;
        this.timeSerde = timeSerde;
        this.timeLength = timeSerde.getSize() + timeSerde.getSize();
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
        final ByteBuffer keySlice = getPrefix(byteBuffer);

        // Read via lookup.
        final ByteBuffer valueByteBuffer = hashLookupDb.getValue(txn, keySlice);
        final Val val = ValSerdeUtil.read(valueByteBuffer);
        return new Session(KeyPrefix.create(val), start, end);
    }

    private ByteBuffer getPrefix(final ByteBuffer byteBuffer) {
        return byteBuffer.slice(0, byteBuffer.remaining() - timeLength);
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final Session session, final Consumer<ByteBuffer> consumer) {
        final Addition prefix = Addition.NONE;
        final Addition suffix = new Addition(timeLength, bb -> {
            timeSerde.write(bb, session.getStart());
            timeSerde.write(bb, session.getEnd());
        });

        ValSerdeUtil.write(session.getPrefix().getVal(), byteBuffers, valueByteBuffer -> {
            final ByteBuffer slice = valueByteBuffer.slice(0,
                    valueByteBuffer.remaining() - timeLength);
            hashLookupDb.put(txn, slice, idByteBuffer -> {
                byteBuffers.use(idByteBuffer.remaining() + timeLength,
                        prefixedBuffer -> {
                            prefixedBuffer.put(idByteBuffer);
                            timeSerde.write(prefixedBuffer, session.getStart());
                            timeSerde.write(prefixedBuffer, session.getEnd());
                            prefixedBuffer.flip();
                            consumer.accept(prefixedBuffer);
                        });
                return null;
            });
            return null;
        }, prefix, suffix);
    }

    @Override
    public <R> R toBufferForGet(final Txn<ByteBuffer> txn,
                                final Session session,
                                final Function<Optional<ByteBuffer>, R> function) {
        final Addition prefix = Addition.NONE;
        final Addition suffix = new Addition(timeLength, bb -> {
            timeSerde.write(bb, session.getStart());
            timeSerde.write(bb, session.getEnd());
        });

        return ValSerdeUtil.write(session.getPrefix().getVal(), byteBuffers, valueByteBuffer -> {
            // We are going to store as a lookup so take off the variable type prefix.
            final ByteBuffer slice = valueByteBuffer.slice(0,
                    valueByteBuffer.remaining() - timeLength);
            return hashLookupDb.get(txn, slice, optionalIdByteBuffer ->
                    optionalIdByteBuffer
                            .map(idByteBuffer ->
                                    byteBuffers.use(idByteBuffer.remaining() + timeLength,
                                            prefixedBuffer -> {
                                                prefixedBuffer.put(idByteBuffer);
                                                timeSerde.write(prefixedBuffer, session.getStart());
                                                timeSerde.write(prefixedBuffer, session.getEnd());
                                                prefixedBuffer.flip();
                                                return function.apply(Optional.of(prefixedBuffer));
                                            }))
                            .orElse(null));
        }, prefix, suffix);
    }

    @Override
    public boolean usesLookup(final ByteBuffer byteBuffer) {
        return true;
    }

    @Override
    public UsedLookupsRecorder getUsedLookupsRecorder(final PlanBEnv env) {
        return new UsedLookupsRecorderProxy(
                new HashLookupRecorder(env, hashLookupDb),
                this::getPrefix);
    }
}
