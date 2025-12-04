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

import stroom.lmdb.serde.IntegerSerde;

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class TestIntegerSerde extends AbstractSerdeTest<Integer, IntegerSerde> {

    @Test
    void testSerialisationDeSerialisation() {

        doSerialisationDeserialisationTest(123);
    }

    @Test
    void testIncrement() {
        final int input = 10;

        final ByteBuffer inputBuf = serialize(input);

        getSerde().increment(inputBuf);

        final Integer output = deserialize(inputBuf);

        assertThat(output).isEqualTo(input + 1);
    }

    @Test
    void testDecrement() {
        final int input = 10;
        final ByteBuffer inputBuf = serialize(input);

        getSerde().decrement(inputBuf);

        final Integer output = deserialize(inputBuf);

        assertThat(output).isEqualTo(input - 1);
    }

    @Override
    TypeLiteral<IntegerSerde> getSerdeType() {
        return new TypeLiteral<IntegerSerde>(){};
    }
}
