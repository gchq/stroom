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
import stroom.planb.impl.db.KeyLength;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UidLookupDb;
import stroom.planb.impl.db.UidLookupRecorder;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.planb.impl.db.UsedLookupsRecorderProxy;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.function.Consumer;

public class UidLookupValTimeSerde implements ValTimeSerde {

    private final UidLookupDb uidLookupDb;
    private final ByteBuffers byteBuffers;
    private final TimeSerde timeSerde;

    public UidLookupValTimeSerde(final UidLookupDb uidLookupDb,
                                 final ByteBuffers byteBuffers,
                                 final TimeSerde timeSerde) {
        this.uidLookupDb = uidLookupDb;
        this.byteBuffers = byteBuffers;
        this.timeSerde = timeSerde;
    }

    @Override
    public ValTime read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final ByteBuffer valSlice = getPrefix(byteBuffer);
        final ByteBuffer timeSlice = byteBuffer.slice(byteBuffer.remaining() - timeSerde.getSize(),
                timeSerde.getSize());
        final ByteBuffer valueByteBuffer = uidLookupDb.getValue(txn, valSlice);
        final Val val = ValSerdeUtil.read(valueByteBuffer);
        final Instant insertTime = timeSerde.read(timeSlice);
        return new ValTime(val, insertTime);
    }

    private ByteBuffer getPrefix(final ByteBuffer byteBuffer) {
        return byteBuffer.slice(0, byteBuffer.remaining() - timeSerde.getSize());
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final ValTime value, final Consumer<ByteBuffer> consumer) {
        ValSerdeUtil.write(value.val(), byteBuffers, valueByteBuffer -> {
            KeyLength.check(valueByteBuffer, Db.MAX_KEY_LENGTH);

            uidLookupDb.put(txn, valueByteBuffer, idByteBuffer -> {
                byteBuffers.use(idByteBuffer.remaining() + timeSerde.getSize(), keyBuffer -> {
                    keyBuffer.put(idByteBuffer);
                    timeSerde.write(keyBuffer, value.insertTime());
                    keyBuffer.flip();
                    consumer.accept(keyBuffer);
                });

                return null;
            });
            return null;
        });
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
