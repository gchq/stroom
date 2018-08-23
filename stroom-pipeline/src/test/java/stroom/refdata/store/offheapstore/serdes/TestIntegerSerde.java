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

import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.nio.ByteBuffer;

public class TestIntegerSerde extends AbstractSerdeTest<Integer, IntegerSerde> {

    @Test
    public void testSerialisationDeSerialisation() {

        doSerialisationDeserialisationTest(123);
    }

    @Test
    public void testIncrement() {
        int input = 10;

        ByteBuffer inputBuf = serialize(input);

        getSerde().increment(inputBuf);

        Integer output = deserialize(inputBuf);

        Assertions.assertThat(output).isEqualTo(input + 1);
    }

    @Test
    public void testDecrement() {
        int input = 10;
        ByteBuffer inputBuf = serialize(input);

        getSerde().decrement(inputBuf);

        Integer output = deserialize(inputBuf);

        Assertions.assertThat(output).isEqualTo(input - 1);
    }

    @Override
    Class<IntegerSerde> getSerdeType() {
        return IntegerSerde.class;
    }
}