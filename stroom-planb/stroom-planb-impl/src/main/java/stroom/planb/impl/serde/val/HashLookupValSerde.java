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
import stroom.planb.impl.db.HashLookupDb;
import stroom.planb.impl.db.HashLookupRecorder;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.query.language.functions.Val;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class HashLookupValSerde implements ValSerde {

    private final HashLookupDb hashLookupDb;
    private final ByteBuffers byteBuffers;

    public HashLookupValSerde(final HashLookupDb hashLookupDb, final ByteBuffers byteBuffers) {
        this.hashLookupDb = hashLookupDb;
        this.byteBuffers = byteBuffers;
    }

    @Override
    public Val read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final ByteBuffer valueByteBuffer = hashLookupDb.getValue(txn, byteBuffer);
        return ValSerdeUtil.read(valueByteBuffer);
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final Val value, final Consumer<ByteBuffer> consumer) {
        ValSerdeUtil.write(value, byteBuffers, valueByteBuffer -> {
            hashLookupDb.put(txn, valueByteBuffer, idByteBuffer -> {
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
        return new HashLookupRecorder(env, hashLookupDb);
    }
}
