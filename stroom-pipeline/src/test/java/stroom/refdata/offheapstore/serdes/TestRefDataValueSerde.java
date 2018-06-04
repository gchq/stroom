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

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import stroom.refdata.lmdb.serde.Serde;
import stroom.refdata.offheapstore.FastInfosetValue;
import stroom.refdata.offheapstore.RefDataValue;
import stroom.refdata.offheapstore.StringValue;

import java.util.Map;

public class TestRefDataValueSerde extends AbstractSerdeTest {

    @Test
    public void testSerialisationDeserialisation_FastInfosetValue() {

        byte[] bytes = new byte[] {0, 1, 2, 3, 4, 5};
        final RefDataValue refDataValue = new FastInfosetValue(bytes);

        final RefDataValueSerde refDataValueSerde = RefDataValueSerdeFactory.create();

        doSerialisationDeserialisationTest(refDataValue, () -> refDataValueSerde);
    }

    @Test
    public void testSerialisationDeserialisation_StringValue() {

        final RefDataValue refDataValue = new StringValue("this is my value");

        final RefDataValueSerde refDataValueSerde = RefDataValueSerdeFactory.create();

        doSerialisationDeserialisationTest(refDataValue, () -> refDataValueSerde);
    }
}