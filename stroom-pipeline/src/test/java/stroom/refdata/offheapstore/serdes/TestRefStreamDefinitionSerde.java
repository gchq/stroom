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

import org.junit.Test;
import stroom.query.api.v2.DocRef;
import stroom.refdata.offheapstore.RefStreamDefinition;

import java.util.UUID;

public class TestRefStreamDefinitionSerde extends AbstractSerdeTest {

    @Test
    public void testSerialisationDeserialisation() {
        byte version = 0;
        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                new DocRef("MyType", UUID.randomUUID().toString()),
                version,
                123456L,
                1);

        doSerialisationDeserialisationTest(refStreamDefinition, RefStreamDefinitionSerde::new);
    }
}