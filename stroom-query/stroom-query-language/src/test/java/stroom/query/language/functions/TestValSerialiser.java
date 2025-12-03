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

package stroom.query.language.functions;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.query.language.functions.ValSerialiser.Serialiser;
import stroom.query.language.functions.ref.KryoDataReader;
import stroom.query.language.functions.ref.KryoDataWriter;
import stroom.test.common.TestUtil;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferOutput;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestValSerialiser {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestValSerialiser.class);

    @TestFactory
    Stream<DynamicTest> testSerDeSer() {
        final long nowMs = System.currentTimeMillis();
        final String nowDateStr = DateUtil.createNormalDateTimeString(nowMs + 10);

        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(Val.class)
                .withTestFunction(testCase -> {
                    final Val inputVal = testCase.getInput();
                    // Serialise then de-serialise so we can compare input to output

                    final ByteBuffer byteBuffer;
                    try (final UnsafeByteBufferOutput output = new UnsafeByteBufferOutput(200)) {
                        try (final KryoDataWriter writer = new KryoDataWriter(output)) {
                            ValSerialiser.write(writer, inputVal);
                        }
                        byteBuffer = output.getByteBuffer();
                    }

                    byteBuffer.flip();
                    LOGGER.debug("byteBuffer: {}", ByteBufferUtils.byteBufferToHex(byteBuffer));

                    try (final KryoDataReader input = new KryoDataReader(new ByteBufferInput(byteBuffer))) {
                        return ValSerialiser.read(input);
                    }

                })
                .withSimpleEqualityAssertion()
                .addCase(ValNull.INSTANCE, ValNull.INSTANCE)
                .addCase(ValBoolean.TRUE, ValBoolean.TRUE)
                .addCase(ValBoolean.FALSE, ValBoolean.FALSE)
                .addCase(ValFloat.create(-1.23F), ValFloat.create(-1.23F))
                .addCase(ValFloat.create(0), ValFloat.create(0))
                .addCase(ValFloat.create(1.23F), ValFloat.create(1.23F))
                .addCase(ValDouble.create(-1.23D), ValDouble.create(-1.23D))
                .addCase(ValDouble.create(1.23D), ValDouble.create(1.23D))
                .addCase(ValInteger.create(-123), ValInteger.create(-123))
                .addCase(ValInteger.create(123), ValInteger.create(123))
                .addCase(ValLong.create(-123L), ValLong.create(-123L))
                .addCase(ValLong.create(123L), ValLong.create(123L))
                .addCase(ValDate.create(nowMs), ValDate.create(nowMs))
                .addCase(ValDate.create(nowDateStr), ValDate.create(nowDateStr))
                .addCase(ValString.create("hello world!"), ValString.create("hello world!"))
                .addCase(ValString.create(""), ValString.EMPTY)
                .addCase(ValErr.create("Bad things happened"), ValErr.create("Bad things happened"))
                .addCase(ValDuration.create(123), ValDuration.create(123))
                .withNameFunction(testCase ->
                        testCase.getInput().getClass().getSimpleName()
                                + "(" + testCase.getInput().toString() + ")")
                .build();
    }

    @Test
    void testWriteArrayReadArray() {
        final long nowMs = System.currentTimeMillis();
        final String nowDateStr = DateUtil.createNormalDateTimeString(nowMs + 10);

        final List<Val> vals = List.of(
                ValNull.INSTANCE,
                ValBoolean.TRUE,
                ValBoolean.FALSE,
                ValFloat.create(-1.23F),
                ValFloat.create(0),
                ValFloat.create(1.23F),
                ValDouble.create(-1.23D),
                ValDouble.create(1.23D),
                ValInteger.create(-123),
                ValInteger.create(123),
                ValLong.create(-123L), ValLong.create(123L), ValDate.create(nowMs),
                ValDate.create(nowDateStr),
                ValString.create("hello world!"),
                ValString.create(""),
                ValErr.create("Bad things happened"),
                ValDuration.create(123));

        final Val[] valArr = vals.toArray(Val.EMPTY_VALUES);

        // Serialise then de-serialise so we can compare input to output
        final ByteBuffer byteBuffer;
        try (final UnsafeByteBufferOutput output = new UnsafeByteBufferOutput(200)) {
            try (final KryoDataWriter writer = new KryoDataWriter(output)) {
                ValSerialiser.writeArray(writer, valArr);
                byteBuffer = output.getByteBuffer();
            }
        }

        byteBuffer.flip();
        LOGGER.info("byteBuffer: {}", ByteBufferUtils.byteBufferToHex(byteBuffer));

        try (final KryoDataReader reader = new KryoDataReader(new ByteBufferInput(byteBuffer))) {
            final Val[] outputVals = ValSerialiser.readArray(reader);

            assertThat(outputVals)
                    .containsExactly(valArr);
        }
    }

    @Test
    void ensureAllSerialisers() {
        for (final Type type : Type.values()) {
            final Serialiser serialiser = ValSerialiser.getSerialiser(type.getId());
            assertThat(serialiser)
                    .withFailMessage(() -> "No serialiser for type " + type)
                    .isNotNull();
        }

    }
}
