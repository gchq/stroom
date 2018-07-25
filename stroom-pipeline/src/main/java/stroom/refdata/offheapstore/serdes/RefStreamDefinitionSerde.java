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
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.pipeline.shared.PipelineDoc;
import stroom.refdata.lmdb.serde.AbstractKryoSerde;
import stroom.refdata.offheapstore.RefStreamDefinition;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.nio.ByteBuffer;

public class RefStreamDefinitionSerde extends AbstractKryoSerde<RefStreamDefinition> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefStreamDefinitionSerde.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(RefStreamDefinitionSerde.class);

    private static final int BUFFER_CAPACITY = (VariableLengthUUIDKryoSerializer.BUFFER_CAPACITY * 2) +
                    (AbstractKryoSerde.VARIABLE_LENGTH_LONG_BYTES * 2) +
                    AbstractKryoSerde.BOOLEAN_BYTES;

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

    @Override
    public int getBufferCapacity() {
        return BUFFER_CAPACITY;
    }

    static class RefStreamDefinitionKryoSerializer extends com.esotericsoftware.kryo.Serializer<RefStreamDefinition> {

        private final VariableLengthUUIDKryoSerializer pipelineDocRefUuidVariableLengthSerializer;
        private final VariableLengthUUIDKryoSerializer pieplineVersionUuidVariableLengthSerializer;

        RefStreamDefinitionKryoSerializer() {
            this.pipelineDocRefUuidVariableLengthSerializer = new VariableLengthUUIDKryoSerializer();
            this.pieplineVersionUuidVariableLengthSerializer = new VariableLengthUUIDKryoSerializer();
        }

        @Override
        public void write(final Kryo kryo,
                          final Output output,
                          final RefStreamDefinition refStreamDefinition) {

            Preconditions.checkArgument(refStreamDefinition.getPipelineDocRef().getType().equals(PipelineDoc.DOCUMENT_TYPE));
            pipelineDocRefUuidVariableLengthSerializer.write(kryo, output, refStreamDefinition.getPipelineDocRef().getUuid());

            // We are only ever dealing with pipeline DocRefs so we don't need
            // the type as the uuid will be unique over all pipelines. The Type is only needed
            // if we have more than one type in there
//            output.writeString(refStreamDefinition.getPipelineDocRef().getType());
            pieplineVersionUuidVariableLengthSerializer.write(kryo, output, refStreamDefinition.getPipelineVersion());

            // write as fixed length bytes so we can scan down the mapUidForwardDb looking for keys
            // with the same RefStreamDefinition TODO why do we need to do this scan?

            output.writeLong(refStreamDefinition.getStreamId(), true);
            output.writeBoolean(refStreamDefinition.isContextData());
            output.writeLong(refStreamDefinition.getStreamNo(), true);
        }

        @Override
        public RefStreamDefinition read(final Kryo kryo,
                                        final Input input,
                                        final Class<RefStreamDefinition> type) {

            final String pipelineUuid = pipelineDocRefUuidVariableLengthSerializer.read(kryo, input, String.class);
            final String pipelineVersion = pieplineVersionUuidVariableLengthSerializer.read(kryo, input, String.class);
            final long streamId = input.readLong(true);
            final boolean isContextData = input.readBoolean();
            final long streamNo = input.readLong(true);

            return new RefStreamDefinition(
                    pipelineUuid,
                    pipelineVersion,
                    streamId,
                    isContextData,
                    streamNo);
        }
    }
}
