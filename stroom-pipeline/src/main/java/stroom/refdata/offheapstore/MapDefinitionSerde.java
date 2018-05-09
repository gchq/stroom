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
import stroom.query.api.v2.DocRef;
import stroom.refdata.lmdb.serde.AbstractKryoSerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.nio.ByteBuffer;

public class MapDefinitionSerde extends AbstractKryoSerde<MapDefinition> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MapDefinitionSerde.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(MapDefinitionSerde.class);

    private static final KryoFactory kryoFactory = () -> {
        Kryo kryo = new Kryo();
        try {
            LAMBDA_LOGGER.debug(() -> String.format("Initialising Kryo on thread %s",
                    Thread.currentThread().getName()));

            kryo.register(MapDefinition.class, new MapDefinitionKryoSerializer());
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
    public MapDefinition deserialize(final ByteBuffer byteBuffer) {
        return super.deserialize(pool, byteBuffer);
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final MapDefinition object) {
        super.serialize(pool, byteBuffer, object);
    }

    private static class MapDefinitionKryoSerializer extends com.esotericsoftware.kryo.Serializer<MapDefinition> {

        @Override
        public void write(final Kryo kryo, final Output output, final MapDefinition mapDefinition) {
            output.writeString(mapDefinition.getPipelineDocRef().getUuid());
            output.writeString(mapDefinition.getPipelineDocRef().getType());

            // write as variable length bytes as we don't require fixed width
            output.writeLong(mapDefinition.getStreamId(), true);
            output.writeString(mapDefinition.getMapName());
        }

        @Override
        public MapDefinition read(final Kryo kryo, final Input input, final Class<MapDefinition> type) {
            final String pipelineUuid = input.readString();
            final String pipelineType = input.readString();
            final long streamId = input.readLong();
            final String mapName = input.readString();
            return new MapDefinition(new DocRef(pipelineUuid, pipelineType), streamId, mapName);
        }
    }
}
