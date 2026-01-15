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


import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.RefDataValue;

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

class TestFastInfoSetValueSerde extends AbstractSerdeTest<RefDataValue, RefDataValueSerde> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestFastInfoSetValueSerde.class);

    @Test
    void testSerialisationDeserialisation() {

        final FastInfosetValue fastInfosetValue = new FastInfosetValue(ByteBuffer.wrap(new byte[]{0, 1, 2, 3, 4}));
//        FastInfosetValue fastInfosetValue = new FastInfosetValue(new byte[]{0, 1, 2, 3, 4});
        doSerialisationDeserialisationTest(fastInfosetValue);
    }

    @Override
    TypeLiteral<RefDataValueSerde> getSerdeType() {
        return new TypeLiteral<RefDataValueSerde>(){};
    }

    @Override
    Supplier<RefDataValueSerde> getSerdeSupplier() {
        return () -> new RefDataValueSerdeFactory().get(FastInfosetValue.TYPE_ID);
    }
}
