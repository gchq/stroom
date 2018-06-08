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

import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.nio.ByteBuffer;

public class TestIntegerSerde extends AbstractSerdeTest {

    @Test
    public void testSerialisationDeSerialisation() {

        doSerialisationDeserialisationTest(123, IntegerSerde::new);
    }

    @Test
    public void testIncrement() {
        int input = 10;
        ByteBuffer inputBuf = ByteBuffer.allocateDirect(Integer.BYTES);
        IntegerSerde integerSerde = new IntegerSerde();

        integerSerde.serialize(inputBuf, input);

        integerSerde.increment(inputBuf);

        Integer output = integerSerde.deserialize(inputBuf);

        Assertions.assertThat(output).isEqualTo(input + 1);
    }

    @Test
    public void testDecrement() {
        int input = 10;
        ByteBuffer inputBuf = ByteBuffer.allocateDirect(Integer.BYTES);
        IntegerSerde integerSerde = new IntegerSerde();

        integerSerde.serialize(inputBuf, input);

        integerSerde.decrement(inputBuf);

        Integer output = integerSerde.deserialize(inputBuf);

        Assertions.assertThat(output).isEqualTo(input - 1);
    }
}