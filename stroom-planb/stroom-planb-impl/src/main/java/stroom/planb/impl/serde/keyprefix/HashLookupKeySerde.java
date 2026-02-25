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

package stroom.planb.impl.serde.keyprefix;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.HashLookupDb;
import stroom.planb.impl.db.HashLookupRecorder;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class HashLookupKeySerde implements KeyPrefixSerde {

    private final HashLookupDb hashLookupDb;
    private final ByteBuffers byteBuffers;

    public HashLookupKeySerde(final HashLookupDb hashLookupDb,
                              final ByteBuffers byteBuffers) {
        this.hashLookupDb = hashLookupDb;
        this.byteBuffers = byteBuffers;
    }

    @Override
    public KeyPrefix read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        // Read via lookup.
        final ByteBuffer valueByteBuffer = hashLookupDb.getValue(txn, byteBuffer);
        final Val val = ValSerdeUtil.read(valueByteBuffer);
        return KeyPrefix.create(val);
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final KeyPrefix key, final Consumer<ByteBuffer> consumer) {
        ValSerdeUtil.write(key.getVal(), byteBuffers, valueByteBuffer -> {
            hashLookupDb.put(txn, valueByteBuffer, idByteBuffer -> {
                consumer.accept(idByteBuffer);
                return null;
            });
            return null;
        });
    }

    @Override
    public <R> R toBufferForGet(final Txn<ByteBuffer> txn,
                                final KeyPrefix key,
                                final Function<Optional<ByteBuffer>, R> function) {
        return ValSerdeUtil.write(key.getVal(), byteBuffers, valueByteBuffer ->
                hashLookupDb.get(txn, valueByteBuffer, function));
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
