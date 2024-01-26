package stroom.query.language.functions;

import stroom.query.language.functions.ValSerialiser.Serialiser;
import stroom.test.common.TestUtil;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.esotericsoftware.kryo.io.ByteBufferInputStream;
import com.esotericsoftware.kryo.io.ByteBufferOutputStream;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import jakarta.xml.bind.DatatypeConverter;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
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
                    try (final ByteBufferOutputStream byteBufferOutputStream = new ByteBufferOutputStream(20)) {
                        try (Output output = new Output(byteBufferOutputStream)) {
                            ValSerialiser.write(output, inputVal);
                            final ByteBuffer byteBuffer = byteBufferOutputStream.getByteBuffer();
                            output.flush();
                            byteBuffer.flip();
                            LOGGER.debug("byteBuffer: {}", byteArrayToHex(byteBuffer.array()));

                            try (Input input = new Input(new ByteBufferInputStream(byteBuffer))) {
                                final Val outputVal = ValSerialiser.read(input);
                                return outputVal;
                            }
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
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
                .addCase(ValString.create(null), ValString.create(null)) // TODO is this right?
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
                ValString.create(null),
                ValString.create(""),
                ValErr.create("Bad things happened"),
                ValDuration.create(123));

        final Val[] valArr = vals.toArray(Val.EMPTY_VALUES);

        // Serialise then de-serialise so we can compare input to output
        try (final ByteBufferOutputStream byteBufferOutputStream = new ByteBufferOutputStream(200)) {
            try (Output output = new Output(byteBufferOutputStream)) {
                ValSerialiser.writeArray(output, valArr);
                final ByteBuffer byteBuffer = byteBufferOutputStream.getByteBuffer();
                output.flush();
                byteBuffer.flip();
                LOGGER.info("byteBuffer: {}", byteArrayToHex(byteBuffer.array()));

                try (Input input = new Input(new ByteBufferInputStream(byteBuffer))) {
                    final Val[] outputVals = ValSerialiser.readArray(input);

                    assertThat(outputVals)
                            .containsExactly(valArr);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
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

    public static String byteArrayToHex(final byte[] arr) {
        final StringBuilder sb = new StringBuilder();
        if (arr != null) {
            for (final byte b : arr) {
                final byte[] oneByteArr = new byte[1];
                oneByteArr[0] = b;
                sb.append(DatatypeConverter.printHexBinary(oneByteArr));
                sb.append(" ");
            }
        }
        return sb.toString().replaceAll(" $", "");
    }
}
