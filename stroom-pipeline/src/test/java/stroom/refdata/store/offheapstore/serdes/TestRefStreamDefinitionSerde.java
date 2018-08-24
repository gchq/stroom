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
import stroom.refdata.store.RefStreamDefinition;

import java.util.UUID;

public class TestRefStreamDefinitionSerde extends AbstractSerdeTest<RefStreamDefinition, RefStreamDefinitionSerde> {

    @Test
    public void testSerialisationDeserialisation() {
        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                123456L,
                123L);

        doSerialisationDeserialisationTest(refStreamDefinition);
    }

    @Override
    Class<RefStreamDefinitionSerde> getSerdeType() {
        return RefStreamDefinitionSerde.class;
    }
}