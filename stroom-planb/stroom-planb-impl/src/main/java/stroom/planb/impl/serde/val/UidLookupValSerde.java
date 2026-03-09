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

package stroom.planb.impl.serde.val;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.KeyLength;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UidLookupDb;
import stroom.planb.impl.db.UidLookupRecorder;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.query.language.functions.Val;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class UidLookupValSerde implements ValSerde {

    private final UidLookupDb uidLookupDb;
    private final ByteBuffers byteBuffers;

    public UidLookupValSerde(final UidLookupDb uidLookupDb, final ByteBuffers byteBuffers) {
        this.uidLookupDb = uidLookupDb;
        this.byteBuffers = byteBuffers;
    }

    @Override
    public Val read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final ByteBuffer valueByteBuffer = uidLookupDb.getValue(txn, byteBuffer);
        return ValSerdeUtil.read(valueByteBuffer);
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final Val value, final Consumer<ByteBuffer> consumer) {
        ValSerdeUtil.write(value, byteBuffers, valueByteBuffer -> {
            KeyLength.check(valueByteBuffer, Db.MAX_KEY_LENGTH);

            uidLookupDb.put(txn, valueByteBuffer, idByteBuffer -> {
                consumer.accept(idByteBuffer);
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
        return new UidLookupRecorder(env, uidLookupDb);
    }
}
