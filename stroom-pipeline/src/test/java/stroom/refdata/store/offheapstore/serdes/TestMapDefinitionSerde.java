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

package stroom.refdata.store.offheapstore.serdes;

import org.junit.Test;
import stroom.refdata.util.ByteBufferUtils;
import stroom.refdata.store.MapDefinition;
import stroom.refdata.store.RefStreamDefinition;

import java.nio.ByteBuffer;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class TestMapDefinitionSerde extends AbstractSerdeTest<MapDefinition, MapDefinitionSerde> {

    @Test
    public void serialize() {
        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                123456L);
        final MapDefinition mapDefinition1 = new MapDefinition(refStreamDefinition, "MyMapName");

        doSerialisationDeserialisationTest(mapDefinition1);
    }

    @Test
    public void serialize_nullMapName() {
        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                123456L);
        final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, null);

        doSerialisationDeserialisationTest(mapDefinition);

    }

    @Test
    public void serialize_nullMapName_verifySerialisedForm() {
        RefStreamDefinitionSerde refStreamDefinitionSerde = new RefStreamDefinitionSerde();
        MapDefinitionSerde mapDefinitionSerde = new MapDefinitionSerde();

        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                123456L);
        final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, null);

        ByteBuffer refStreamDefBuffer = ByteBuffer.allocate(60);
        refStreamDefinitionSerde.serialize(refStreamDefBuffer, refStreamDefinition);

        ByteBuffer mapDefBuffer = ByteBuffer.allocate(60);
        mapDefinitionSerde.serialize(mapDefBuffer, mapDefinition);

        int compareResult = ByteBufferUtils.compare(refStreamDefBuffer, mapDefBuffer);

        assertThat(compareResult).isEqualTo(0);

        final MapDefinition mapDefinition2 = new MapDefinition(refStreamDefinition, "myMapName");
        ByteBuffer mapDefBuffer2 = ByteBuffer.allocate(60);
        mapDefinitionSerde.serialize(mapDefBuffer2, mapDefinition2);

        assertThat(mapDefBuffer2.remaining()).isGreaterThan(mapDefBuffer.remaining());
    }

    @Override
    Class<MapDefinitionSerde> getSerdeType() {
        return MapDefinitionSerde.class;
    }
}