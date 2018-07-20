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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.offheapstore.ByteBufferUtils;
import stroom.refdata.offheapstore.FastInfosetValue;
import stroom.refdata.offheapstore.RefDataValue;
import stroom.refdata.offheapstore.StringValue;

import java.nio.ByteBuffer;

public class TestRefDataValueSerde extends AbstractSerdeTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestRefDataValueSerde.class);

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

    @Test
    public void testAreValuesEqual_notEqual_FastInfoSetValue() {
        doEqualityTest(
                new FastInfosetValue(new byte[] {0, 1, 2, 3, 4, 5}),
                new FastInfosetValue(new byte[] {5, 4, 3, 2, 1, 0}),
                false);
    }

    @Test
    public void testAreValuesEqual_notEqual2_FastInfoSetValue() {
        doEqualityTest(
                new FastInfosetValue(new byte[] {0, 1, 2, 3, 4, 5}),
                new FastInfosetValue(new byte[] {0, 1, 2}),
                false);
    }

    @Test
    public void testAreValuesEqual_equal_FastInfoSetValue() {
        doEqualityTest(
                new FastInfosetValue(new byte[] {0, 1, 2, 3, 4, 5}),
                new FastInfosetValue(new byte[] {0, 1, 2, 3, 4, 5}),
                true);
    }

    @Test
    public void testAreValuesEqual_equalDiffRefCount_FastInfoSetValue() {
        doEqualityTest(
                new FastInfosetValue(1, new byte[] {0, 1, 2, 3, 4, 5}),
                new FastInfosetValue(2, new byte[] {0, 1, 2, 3, 4, 5}),
                true);
    }

    @Test(expected = RuntimeException.class)
    public void testAreValuesEqual_differentTypes() {
        doEqualityTest(
                new FastInfosetValue(1, new byte[] {0, 1, 2, 3, 4, 5}),
                new StringValue("my value"),
                false);
    }

    @Test
    public void testAreValuesEqual_notEqual_StringValue() {
        doEqualityTest(
                new StringValue("This"),
                new StringValue("That"),
                false);
    }

    @Test
    public void testAreValuesEqual_notEqual2_StringValue() {
        doEqualityTest(
                new StringValue("Thisxxx"),
                new StringValue("This"),
                false);
    }

    @Test
    public void testAreValuesEqual_equal_StringValue() {
        doEqualityTest(
                new StringValue("myValue"),
                new StringValue("myValue"),
                true);
    }

    @Test
    public void testAreValuesEqual_equalDiffRefCount_StringValue() {
        doEqualityTest(
                new StringValue(1, "my value"),
                new StringValue(2, "my value"),
                true);
    }

    private void doEqualityTest(final RefDataValue thisRefDataValue,
                           final RefDataValue thatRefDataValue,
                           final boolean expectedResult) {
        final RefDataValueSerde refDataValueSerde = RefDataValueSerdeFactory.create();
        ByteBuffer thisBuf = refDataValueSerde.serialize(thisRefDataValue);
        ByteBuffer thatBuf = refDataValueSerde.serialize(thatRefDataValue);

        LOGGER.debug("thisBuf: {}", ByteBufferUtils.byteBufferInfo(thisBuf));
        LOGGER.debug("thatBuf: {}", ByteBufferUtils.byteBufferInfo(thatBuf));

        boolean result = refDataValueSerde.areValuesEqual(thisBuf, thatBuf);
        Assertions.assertThat(result).isEqualTo(expectedResult);
    }
}