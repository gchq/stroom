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
import stroom.query.api.v2.DocRef;
import stroom.refdata.lmdb.serde.AbstractKryoSerde;
import stroom.refdata.offheapstore.RefStreamDefinition;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.nio.ByteBuffer;

public class RefStreamDefinitionSerde extends AbstractKryoSerde<RefStreamDefinition> {


    private static final Logger LOGGER = LoggerFactory.getLogger(RefStreamDefinitionSerde.class);
    private static final LambdaLogger LAMBDA_LOGGER= LambdaLoggerFactory.getLogger(RefStreamDefinitionSerde.class);

    private static final KryoFactory kryoFactory = buildKryoFactory(
            RefStreamDefinition.class,
            RefStreamDefinitionKryoSerializer::new);

    private static final KryoPool pool = new KryoPool.Builder(kryoFactory)
            .softReferences()
            .build();

    @Override
    public RefStreamDefinition deserialize(final ByteBuffer byteBuffer) {
        return super.deserialize(pool, byteBuffer);
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final RefStreamDefinition object) {
        super.serialize(pool, byteBuffer, object);
    }

    static class RefStreamDefinitionKryoSerializer extends com.esotericsoftware.kryo.Serializer<RefStreamDefinition> {

        @Override
        public void write(final Kryo kryo,
                          final Output output,
                          final RefStreamDefinition refStreamDefinition) {
            output.writeString(refStreamDefinition.getPipelineDocRef().getUuid());
            output.writeString(refStreamDefinition.getPipelineDocRef().getType());
            output.writeByte(refStreamDefinition.getPipelineVersion());
            // write as variable length bytes as we don't require fixed width
            output.writeVarLong(refStreamDefinition.getStreamId(), true);
            output.writeVarLong(refStreamDefinition.getStreamNo(), true);
        }

        @Override
        public RefStreamDefinition read(final Kryo kryo,
                                        final Input input,
                                        final Class<RefStreamDefinition> type) {
            final String pipelineUuid = input.readString();
            final String pipelineType = input.readString();
            final byte pipelineVersion = input.readByte();
            final long streamId = input.readVarLong(true);
            final long streamNo = input.readVarLong(true);

            return new RefStreamDefinition(
                    new DocRef(pipelineType, pipelineUuid),
                    pipelineVersion,
                    streamId,
                    streamNo);
        }
    }
}
