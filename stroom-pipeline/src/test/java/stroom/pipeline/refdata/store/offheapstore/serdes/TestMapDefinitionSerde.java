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


import stroom.bytebuffer.ByteBufferUtils;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.RefStreamDefinition;

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestMapDefinitionSerde extends AbstractSerdeTest<MapDefinition, MapDefinitionSerde> {

    @Test
    void serialize() {
        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                123456L);
        final MapDefinition mapDefinition1 = new MapDefinition(refStreamDefinition, "MyMapName");

        doSerialisationDeserialisationTest(mapDefinition1);
    }

    @Test
    void serialize_nullMapName() {
        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                123456L);
        final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, null);

        doSerialisationDeserialisationTest(mapDefinition);

    }

    @Test
    void serialize_nullMapName_verifySerialisedForm() {
        final RefStreamDefinitionSerde refStreamDefinitionSerde = new RefStreamDefinitionSerde();
        final MapDefinitionSerde mapDefinitionSerde = new MapDefinitionSerde();

        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                123456L);
        final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, null);

        final ByteBuffer refStreamDefBuffer = ByteBuffer.allocate(60);
        refStreamDefinitionSerde.serialize(refStreamDefBuffer, refStreamDefinition);

        final ByteBuffer mapDefBuffer = ByteBuffer.allocate(60);
        mapDefinitionSerde.serialize(mapDefBuffer, mapDefinition);

        assertThat(refStreamDefBuffer).isEqualTo(mapDefBuffer);

        final MapDefinition mapDefinition2 = new MapDefinition(refStreamDefinition, "myMapName");
        final ByteBuffer mapDefBuffer2 = ByteBuffer.allocate(60);
        mapDefinitionSerde.serialize(mapDefBuffer2, mapDefinition2);

        assertThat(mapDefBuffer2.remaining()).isGreaterThan(mapDefBuffer.remaining());
    }

    @Override
    TypeLiteral<MapDefinitionSerde> getSerdeType() {
        return new TypeLiteral<MapDefinitionSerde>(){};
    }
}
