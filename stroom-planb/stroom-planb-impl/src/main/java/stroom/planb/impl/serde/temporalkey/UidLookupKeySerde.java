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

package stroom.planb.impl.serde.temporalkey;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.KeyLength;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UidLookupDb;
import stroom.planb.impl.db.UidLookupRecorder;
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

public class UidLookupKeySerde implements TemporalKeySerde {

    private final UidLookupDb uidLookupDb;
    private final ByteBuffers byteBuffers;
    private final TimeSerde timeSerde;

    public UidLookupKeySerde(final UidLookupDb uidLookupDb, final ByteBuffers byteBuffers, final TimeSerde timeSerde) {
        this.uidLookupDb = uidLookupDb;
        this.byteBuffers = byteBuffers;
        this.timeSerde = timeSerde;
    }

    @Override
    public TemporalKey read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        // Slice off the end to get the effective time.
        final ByteBuffer timeSlice = byteBuffer.slice(byteBuffer.remaining() - timeSerde.getSize(),
                timeSerde.getSize());
        final Instant time = timeSerde.read(timeSlice);

        // Slice off the name.
        final ByteBuffer nameSlice = getPrefix(byteBuffer);

        // Read via lookup.
        final ByteBuffer valueByteBuffer = uidLookupDb.getValue(txn, nameSlice);
        final Val val = ValSerdeUtil.read(valueByteBuffer);
        return new TemporalKey(KeyPrefix.create(val), time);
    }

    private ByteBuffer getPrefix(final ByteBuffer byteBuffer) {
        // Slice off the name.
        return byteBuffer.slice(0, byteBuffer.remaining() - timeSerde.getSize());
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final TemporalKey key, final Consumer<ByteBuffer> consumer) {
        final Addition prefix = Addition.NONE;
        final Addition suffix = new Addition(timeSerde.getSize(), bb -> timeSerde.write(bb, key.getTime()));

        ValSerdeUtil.write(key.getPrefix().getVal(), byteBuffers, valueByteBuffer -> {
            final ByteBuffer slice = valueByteBuffer.slice(0, valueByteBuffer.remaining() - timeSerde.getSize());
            KeyLength.check(slice, Db.MAX_KEY_LENGTH);

            uidLookupDb.put(txn, slice, idByteBuffer -> {
                byteBuffers.use(idByteBuffer.remaining() + timeSerde.getSize(), prefixedBuffer -> {
                    prefixedBuffer.put(idByteBuffer);
                    timeSerde.write(prefixedBuffer, key.getTime());
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
                                final TemporalKey key,
                                final Function<Optional<ByteBuffer>, R> function) {
        final Addition prefix = Addition.NONE;
        final Addition suffix = new Addition(timeSerde.getSize(), bb -> timeSerde.write(bb, key.getTime()));

        return ValSerdeUtil.write(key.getPrefix().getVal(), byteBuffers, valueByteBuffer -> {
            // We are going to store as a lookup so take off the variable type prefix.
            final ByteBuffer slice = valueByteBuffer.slice(0, valueByteBuffer.remaining() - timeSerde.getSize());
            KeyLength.check(slice, Db.MAX_KEY_LENGTH);

            return uidLookupDb.get(txn, slice, optionalIdByteBuffer ->
                    optionalIdByteBuffer
                            .map(idByteBuffer ->
                                    byteBuffers.use(idByteBuffer.remaining() + timeSerde.getSize(), prefixedBuffer -> {
                                        prefixedBuffer.put(idByteBuffer);
                                        timeSerde.write(prefixedBuffer, key.getTime());
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
                new UidLookupRecorder(env, uidLookupDb),
                this::getPrefix);
    }
}
