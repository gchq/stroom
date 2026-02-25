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
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.HashLookupDb;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UidLookupDb;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.planb.impl.db.UsedLookupsRecorderProxy;
import stroom.planb.impl.db.VariableUsedLookupsRecorder;
import stroom.planb.impl.serde.keyprefix.KeyPrefix;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.planb.impl.serde.val.ValSerdeUtil.Addition;
import stroom.planb.impl.serde.val.VariableValType;
import stroom.query.language.functions.Val;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class VariableSessionSerde implements SessionSerde {

    private static final int USE_HASH_LOOKUP_THRESHOLD = Db.MAX_KEY_LENGTH;

    private final int uidLookupThreshold;
    private final UidLookupDb uidLookupDb;
    private final HashLookupDb hashLookupDb;
    private final ByteBuffers byteBuffers;
    private final TimeSerde timeSerde;
    private final int timeLength;

    public VariableSessionSerde(final UidLookupDb uidLookupDb,
                                final HashLookupDb hashLookupDb,
                                final ByteBuffers byteBuffers,
                                final TimeSerde timeSerde) {
        this.uidLookupDb = uidLookupDb;
        this.hashLookupDb = hashLookupDb;
        this.byteBuffers = byteBuffers;
        this.timeSerde = timeSerde;
        this.timeLength = timeSerde.getSize() + timeSerde.getSize();
        uidLookupThreshold = 32 + timeLength;
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

        // Read the variable type.
        final VariableValType valType = VariableValType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(keySlice.get());
        final Val val = switch (valType) {
            case DIRECT -> {
                // Read direct.
                yield ValSerdeUtil.read(keySlice);
            }
            case UID_LOOKUP -> {
                // Read via UI lookup.
                final ByteBuffer valueByteBuffer = uidLookupDb.getValue(txn, keySlice);
                yield ValSerdeUtil.read(valueByteBuffer);
            }
            case HASH_LOOKUP -> {
                // Read via hash lookup.
                final ByteBuffer valueByteBuffer = hashLookupDb.getValue(txn, keySlice);
                yield ValSerdeUtil.read(valueByteBuffer);
            }
        };

        return new Session(KeyPrefix.create(val), start, end);
    }

    private ByteBuffer getPrefix(final ByteBuffer byteBuffer) {
        // Slice off the key.
        return byteBuffer.slice(0, byteBuffer.remaining() - timeLength);
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final Session session, final Consumer<ByteBuffer> consumer) {
        final Addition prefix = new Addition(1, bb -> bb.put(VariableValType.DIRECT.getPrimitiveValue()));
        final Addition suffix = new Addition(timeLength, bb -> {
            timeSerde.write(bb, session.getStart());
            timeSerde.write(bb, session.getEnd());
        });

        ValSerdeUtil.write(session.getPrefix().getVal(), byteBuffers, valueByteBuffer -> {
            if (valueByteBuffer.remaining() > USE_HASH_LOOKUP_THRESHOLD) {
                // We are going to store as a lookup so take off the variable type prefix.
                final ByteBuffer slice = getKey(valueByteBuffer);
                hashLookupDb.put(txn, slice, idByteBuffer -> {
                    byteBuffers.use(idByteBuffer.remaining() + 1 + timeLength, prefixedBuffer -> {
                        // Add the variable type prefix to the lookup id.
                        prefixedBuffer.put(VariableValType.HASH_LOOKUP.getPrimitiveValue());
                        prefixedBuffer.put(idByteBuffer);
                        timeSerde.write(prefixedBuffer, session.getStart());
                        timeSerde.write(prefixedBuffer, session.getEnd());
                        prefixedBuffer.flip();
                        consumer.accept(prefixedBuffer);
                    });
                    return null;
                });
            } else if (valueByteBuffer.remaining() > uidLookupThreshold) {
                // We are going to store as a lookup so take off the variable type prefix.
                final ByteBuffer slice = getKey(valueByteBuffer);
                uidLookupDb.put(txn, slice, idByteBuffer -> {
                    byteBuffers.use(idByteBuffer.remaining() + 1 + timeLength, prefixedBuffer -> {
                        // Add the variable type prefix to the lookup id.
                        prefixedBuffer.put(VariableValType.UID_LOOKUP.getPrimitiveValue());
                        prefixedBuffer.put(idByteBuffer);
                        timeSerde.write(prefixedBuffer, session.getStart());
                        timeSerde.write(prefixedBuffer, session.getEnd());
                        prefixedBuffer.flip();
                        consumer.accept(prefixedBuffer);
                    });
                    return null;
                });
            } else {
                // We have already added the direct variable prefix so just use the byte buffer.
                consumer.accept(valueByteBuffer);
            }
            return null;
        }, prefix, suffix);
    }

    @Override
    public <R> R toBufferForGet(final Txn<ByteBuffer> txn,
                                final Session session,
                                final Function<Optional<ByteBuffer>, R> function) {
        final Addition prefix = new Addition(1, bb -> bb.put(VariableValType.DIRECT.getPrimitiveValue()));
        final Addition suffix = new Addition(timeLength, bb -> {
            timeSerde.write(bb, session.getStart());
            timeSerde.write(bb, session.getEnd());
        });

        return ValSerdeUtil.write(session.getPrefix().getVal(), byteBuffers, valueByteBuffer -> {
            if (valueByteBuffer.remaining() > USE_HASH_LOOKUP_THRESHOLD) {
                // We are going to store as a lookup so take off the variable type prefix.
                final ByteBuffer slice = getKey(valueByteBuffer);
                return hashLookupDb.get(txn, slice, optionalIdByteBuffer ->
                        optionalIdByteBuffer
                                .map(idByteBuffer ->
                                        byteBuffers.use(idByteBuffer.remaining() + 1 + timeLength, prefixedBuffer -> {
                                            // Add the variable type prefix to the lookup id.
                                            prefixedBuffer.put(VariableValType.HASH_LOOKUP.getPrimitiveValue());
                                            prefixedBuffer.put(idByteBuffer);
                                            timeSerde.write(prefixedBuffer, session.getStart());
                                            timeSerde.write(prefixedBuffer, session.getEnd());
                                            prefixedBuffer.flip();
                                            return function.apply(Optional.of(prefixedBuffer));
                                        }))
                                .orElse(null));
            } else if (valueByteBuffer.remaining() > uidLookupThreshold) {
                // We are going to store as a lookup so take off the variable type prefix.
                final ByteBuffer slice = getKey(valueByteBuffer);
                return uidLookupDb.get(txn, slice, optionalIdByteBuffer ->
                        optionalIdByteBuffer
                                .map(idByteBuffer ->
                                        byteBuffers.use(idByteBuffer.remaining() + 1 + timeLength, prefixedBuffer -> {
                                            // Add the variable type prefix to the lookup id.
                                            prefixedBuffer.put(VariableValType.UID_LOOKUP.getPrimitiveValue());
                                            prefixedBuffer.put(idByteBuffer);
                                            timeSerde.write(prefixedBuffer, session.getStart());
                                            timeSerde.write(prefixedBuffer, session.getEnd());
                                            prefixedBuffer.flip();
                                            return function.apply(Optional.of(prefixedBuffer));
                                        }))
                                .orElse(null));
            } else {
                // We have already added the direct variable prefix so just use the byte buffer.
                return function.apply(Optional.of(valueByteBuffer));
            }
        }, prefix, suffix);
    }

    private ByteBuffer getKey(final ByteBuffer byteBuffer) {
        return byteBuffer.slice(1, byteBuffer.remaining() - 1 - timeLength);
    }

    @Override
    public boolean usesLookup(final ByteBuffer byteBuffer) {
        // Read the variable type.
        final VariableValType valType = VariableValType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(byteBuffer.get(0));
        return !VariableValType.DIRECT.equals(valType);
    }

    @Override
    public UsedLookupsRecorder getUsedLookupsRecorder(final PlanBEnv env) {
        return new UsedLookupsRecorderProxy(
                new VariableUsedLookupsRecorder(env, uidLookupDb, hashLookupDb),
                this::getPrefix);
    }
}
