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

package stroom.planb.impl.serde.valtime;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.HashLookupDb;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UidLookupDb;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.planb.impl.db.UsedLookupsRecorderProxy;
import stroom.planb.impl.db.VariableUsedLookupsRecorder;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.planb.impl.serde.val.ValSerdeUtil.Addition;
import stroom.planb.impl.serde.val.VariableValType;
import stroom.query.language.functions.Val;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.function.Consumer;

public class VariableValTimeSerde implements ValTimeSerde {

    private static final int USE_UID_LOOKUP_THRESHOLD = 32;
    private static final int USE_HASH_LOOKUP_THRESHOLD = Db.MAX_KEY_LENGTH;

    private final UidLookupDb uidLookupDb;
    private final HashLookupDb hashLookupDb;
    private final ByteBuffers byteBuffers;
    private final TimeSerde timeSerde;

    public VariableValTimeSerde(final UidLookupDb uidLookupDb,
                                final HashLookupDb hashLookupDb,
                                final ByteBuffers byteBuffers,
                                final TimeSerde timeSerde) {
        this.uidLookupDb = uidLookupDb;
        this.hashLookupDb = hashLookupDb;
        this.byteBuffers = byteBuffers;
        this.timeSerde = timeSerde;
    }

    @Override
    public ValTime read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final ByteBuffer valSlice = getPrefix(byteBuffer);
        final ByteBuffer timeSlice = byteBuffer.slice(byteBuffer.remaining() - timeSerde.getSize(),
                timeSerde.getSize());

        // Read the variable type.
        final VariableValType valType = VariableValType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(valSlice.get());
        final Val val = switch (valType) {
            case DIRECT -> {
                // Read direct.
                yield ValSerdeUtil.read(valSlice);
            }
            case UID_LOOKUP -> {
                // Read via UI lookup.
                final ByteBuffer valueByteBuffer = uidLookupDb.getValue(txn, valSlice);
                yield ValSerdeUtil.read(valueByteBuffer);
            }
            case HASH_LOOKUP -> {
                // Read via hash lookup.
                final ByteBuffer valueByteBuffer = hashLookupDb.getValue(txn, valSlice);
                yield ValSerdeUtil.read(valueByteBuffer);
            }
        };

        final Instant insertTime = timeSerde.read(timeSlice);
        return new ValTime(val, insertTime);
    }

    private ByteBuffer getPrefix(final ByteBuffer byteBuffer) {
        return byteBuffer.slice(0, byteBuffer.remaining() - timeSerde.getSize());
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final ValTime val, final Consumer<ByteBuffer> consumer) {
        final Addition prefix = new Addition(1, bb -> bb.put(VariableValType.DIRECT.getPrimitiveValue()));
        final Addition suffix = Addition.NONE;

        ValSerdeUtil.write(val.val(), byteBuffers, valueByteBuffer -> {
            if (valueByteBuffer.remaining() > USE_HASH_LOOKUP_THRESHOLD) {
                // We are going to store as a lookup so take off the variable type prefix.
                final ByteBuffer valueSlice = getValueSlice(valueByteBuffer);
                hashLookupDb.put(txn, valueSlice, idByteBuffer -> {
                    byteBuffers.use(1 + idByteBuffer.remaining() + timeSerde.getSize(), prefixedBuffer -> {
                        // Add the variable type prefix to the lookup id.
                        prefixedBuffer.put(VariableValType.HASH_LOOKUP.getPrimitiveValue());
                        prefixedBuffer.put(idByteBuffer);
                        timeSerde.write(prefixedBuffer, val.insertTime());
                        prefixedBuffer.flip();
                        consumer.accept(prefixedBuffer);
                    });
                    return null;
                });
            } else if (valueByteBuffer.remaining() > USE_UID_LOOKUP_THRESHOLD) {
                // We are going to store as a lookup so take off the variable type prefix.
                final ByteBuffer valueSlice = getValueSlice(valueByteBuffer);
                uidLookupDb.put(txn, valueSlice, idByteBuffer -> {
                    byteBuffers.use(1 + idByteBuffer.remaining() + timeSerde.getSize(), prefixedBuffer -> {
                        // Add the variable type prefix to the lookup id.
                        prefixedBuffer.put(VariableValType.UID_LOOKUP.getPrimitiveValue());
                        prefixedBuffer.put(idByteBuffer);
                        timeSerde.write(prefixedBuffer, val.insertTime());
                        prefixedBuffer.flip();
                        consumer.accept(prefixedBuffer);
                    });
                    return null;
                });
            } else {
                // We have already added the direct variable prefix so just use the byte buffer.
                byteBuffers.use(valueByteBuffer.remaining() + timeSerde.getSize(), prefixedBuffer -> {
                    prefixedBuffer.put(valueByteBuffer);
                    timeSerde.write(prefixedBuffer, val.insertTime());
                    prefixedBuffer.flip();
                    consumer.accept(prefixedBuffer);
                });
            }
            return null;
        }, prefix, suffix);
    }

    private ByteBuffer getValueSlice(final ByteBuffer byteBuffer) {
        return byteBuffer.slice(1, byteBuffer.remaining() - 1);
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
