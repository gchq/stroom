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

package stroom.refdata.offheapstore;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoFactory;
import com.esotericsoftware.kryo.pool.KryoPool;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.lmdb.serde.AbstractKryoSerde;
import stroom.refdata.saxevents.uid.UID;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.nio.ByteBuffer;

public class KeyValueStoreKeySerde extends AbstractKryoSerde<KeyValueStoreKey> {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyValueStoreKeySerde.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(KeyValueStoreKeySerde.class);

    private static final KryoFactory kryoFactory = () -> {
        Kryo kryo = new Kryo();
        try {
            LAMBDA_LOGGER.debug(() -> String.format("Initialising Kryo on thread %s",
                    Thread.currentThread().getName()));

            kryo.register(KeyValueStoreKey.class, new KeyValueStoreKeyKryoSerializer());
            ((Kryo.DefaultInstantiatorStrategy) kryo.getInstantiatorStrategy()).setFallbackInstantiatorStrategy(
                    new StdInstantiatorStrategy());
            kryo.setRegistrationRequired(true);
        } catch (Exception e) {
            LOGGER.error("Exception occurred configuring kryo instance", e);
        }
        return kryo;
    };

    private static final KryoPool pool = new KryoPool.Builder(kryoFactory)
            .softReferences()
            .build();

    @Override
    public KeyValueStoreKey deserialize(final ByteBuffer byteBuffer) {
        return super.deserialize(pool, byteBuffer);
    }

    @Override
    public ByteBuffer serialize(final KeyValueStoreKey object) {
        // TODO how do we know how big the serialized form will be
        return super.serialize(pool, 1_000, object);
    }

    private static class KeyValueStoreKeyKryoSerializer extends com.esotericsoftware.kryo.Serializer<KeyValueStoreKey> {

        @Override
        public void write(final Kryo kryo, final Output output, final KeyValueStoreKey key) {
            final UID mapUid = key.getMapUid();
            output.write(mapUid.getBackingArray(), mapUid.getOffset(), UID.length());
            output.writeString(key.getKey());

            //TODO need to be sure this is written in correct endian-ness so lexicographical scanning works
            output.writeLong(key.getEffectiveTimeEpochMs());
        }

        @Override
        public KeyValueStoreKey read(final Kryo kryo, final Input input, final Class<KeyValueStoreKey> type) {
            final UID mapUid = UID.from(input.readBytes(UID.length()));
            final String key = input.readString();

            //TODO need to be sure this is written in correct endian-ness so lexicographical scanning works
            final long effectiveTimeEpochMs = input.readLong();
            return new KeyValueStoreKey(mapUid, key, effectiveTimeEpochMs);
        }
    }
}
