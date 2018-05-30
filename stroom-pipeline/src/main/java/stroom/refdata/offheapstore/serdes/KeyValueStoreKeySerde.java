/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.offheapstore.serdes;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoFactory;
import com.esotericsoftware.kryo.pool.KryoPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.lmdb.serde.AbstractKryoSerde;
import stroom.refdata.offheapstore.KeyValueStoreKey;
import stroom.refdata.offheapstore.UID;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.nio.ByteBuffer;

public class KeyValueStoreKeySerde extends AbstractKryoSerde<KeyValueStoreKey> {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyValueStoreKeySerde.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(KeyValueStoreKeySerde.class);

    private static final KryoFactory kryoFactory = buildKryoFactory(KeyValueStoreKey.class, KeyValueStoreKeyKryoSerializer::new);

    private static final KryoPool pool = new KryoPool.Builder(kryoFactory)
            .softReferences()
            .build();

    @Override
    public KeyValueStoreKey deserialize(final ByteBuffer byteBuffer) {
        return super.deserialize(pool, byteBuffer);
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final KeyValueStoreKey object) {
        super.serialize(pool, byteBuffer, object);
    }

    private static class KeyValueStoreKeyKryoSerializer extends com.esotericsoftware.kryo.Serializer<KeyValueStoreKey> {

        @Override
        public void write(final Kryo kryo, final Output output, final KeyValueStoreKey key) {
            RefDataSerdeUtils.writeUid(output, key.getMapUid());
            output.writeString(key.getKey());
            RefDataSerdeUtils.writeTimeMs(output, key.getEffectiveTimeEpochMs());
        }

        @Override
        public KeyValueStoreKey read(final Kryo kryo, final Input input, final Class<KeyValueStoreKey> type) {
            final UID mapUid = RefDataSerdeUtils.readUid(input);
            final String key = input.readString();
            final long effectiveTimeEpochMs = RefDataSerdeUtils.readTimeMs(input);
            return new KeyValueStoreKey(mapUid, key, effectiveTimeEpochMs);
        }
    }
}
