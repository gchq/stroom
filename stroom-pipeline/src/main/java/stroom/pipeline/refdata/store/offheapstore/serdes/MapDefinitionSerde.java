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

package stroom.pipeline.refdata.store.offheapstore.serdes;

import stroom.lmdb.serde.AbstractKryoSerde;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapDefinitionSerde extends AbstractKryoSerde<MapDefinition> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MapDefinitionSerde.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(MapDefinitionSerde.class);

    private final RefStreamDefinitionSerde refStreamDefinitionSerde;

    public MapDefinitionSerde() {
        this.refStreamDefinitionSerde = new RefStreamDefinitionSerde();
    }

    @Override
    public void write(final Output output, final MapDefinition mapDefinition) {
        // first serialise the refStreamDefinition part
        refStreamDefinitionSerde.write(output, mapDefinition.getRefStreamDefinition());
        if (mapDefinition.getMapName() != null) {
            output.writeString(mapDefinition.getMapName());
        }
    }

    @Override
    public MapDefinition read(final Input input) {
        // first de-serialise the refStreamDefinition part
        final RefStreamDefinition refStreamDefinition = refStreamDefinitionSerde.read(input);
        final String mapName;
        if (input.position() < input.limit()) {
            mapName = input.readString();
        } else {
            mapName = null;
        }
        return new MapDefinition(refStreamDefinition, mapName);
    }

//    public void serializeWithoutKeyPart(final ByteBuffer byteBuffer, final MapDefinition mapDefinition) {
//
//        serialize(byteBuffer, mapDefinition);
//
//        // set the limit to just after the UID part
//        byteBuffer.limit(UID.UID_ARRAY_LENGTH);
//    }
}
