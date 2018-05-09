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
import stroom.entity.shared.Range;
import stroom.refdata.lmdb.serde.AbstractKryoSerde;
import stroom.refdata.saxevents.uid.UID;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.nio.ByteBuffer;

public class RangeStoreKeySerde extends AbstractKryoSerde<RangeStoreKey> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RangeStoreKeySerde.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(RangeStoreKeySerde.class);

    private static final KryoFactory kryoFactory = () -> {
        Kryo kryo = new Kryo();
        try {
            LAMBDA_LOGGER.debug(() -> String.format("Initialising Kryo on thread %s",
                    Thread.currentThread().getName()));

            kryo.register(RangeStoreKey.class, new RangeStoreKeyKryoSerializer());
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
    public RangeStoreKey deserialize(final ByteBuffer byteBuffer) {
        return super.deserialize(pool, byteBuffer);
    }

    @Override
    public ByteBuffer serialize(final RangeStoreKey object) {
        // TODO how do we know how big the serialized form will be
        return super.serialize(pool, 1_000, object);
    }

    private static class RangeStoreKeyKryoSerializer extends com.esotericsoftware.kryo.Serializer<RangeStoreKey> {

        @Override
        public void write(final Kryo kryo, final Output output, final RangeStoreKey key) {
            RefDataSerdeUtils.writeUid(output, key.getMapUid());
            RefDataSerdeUtils.writeTimeMs(output, key.getEffectiveTimeEpochMs());
            Range<Long> range = key.getKeyRange();
            RefDataSerdeUtils.writeTimeMs(output, range.getFrom());
            RefDataSerdeUtils.writeTimeMs(output, range.getTo());
        }

        @Override
        public RangeStoreKey read(final Kryo kryo, final Input input, final Class<RangeStoreKey> type) {
            final UID mapUid = RefDataSerdeUtils.readUid(input);
            final long effectiveTimeEpochMs = RefDataSerdeUtils.readTimeMs(input);
            long startMs = RefDataSerdeUtils.readTimeMs(input);
            long endMs = RefDataSerdeUtils.readTimeMs(input);
            return new RangeStoreKey(mapUid, effectiveTimeEpochMs, new Range<>(startMs, endMs));
        }
    }
}
