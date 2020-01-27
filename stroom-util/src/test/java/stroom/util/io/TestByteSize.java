package stroom.util.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class TestByteSize {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestByteSize.class);

    private static final long KI = 1024L;

    @Test
    void parse() {
        doParseTest(123L, "123", "123b", "123B", "123byTes");
        doParseTest(123 * KI, "123K", "123KB", "123KiB");
        doParseTest(123 * KI * KI, "123M", "123MB", "123MiB");
        doParseTest(123 * KI * KI * KI, "123G", "123GB", "123GiB");
        doParseTest(123 * KI * KI * KI * KI, "123T", "123TB", "123TiB");
        doParseTest(123 * KI * KI * KI * KI * KI, "123P", "123PB", "123PiB");

        doParseTest((long) (KI + 0.1 * KI), "1.1K");
    }

    @Test
    void parse_bad() {
        Assertions.assertThatThrownBy(() -> {
            doParseTest(123L, "a load of rubbish");
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofBytes() {
        doOfTest(10L, ByteSize::ofBytes, "10B", 1);
        doOfTest(10L, ByteSize::ofKibibytes, "10K", KI);
        doOfTest(10L, ByteSize::ofMebibytes, "10M", KI * KI);
        doOfTest(10L, ByteSize::ofGibibytes, "10G", KI * KI * KI);
        doOfTest(10L, ByteSize::ofTebibytes, "10T", KI * KI * KI * KI);
        doOfTest(10L, ByteSize::ofPebibytes, "10P", KI * KI * KI * KI * KI);
    }

    private void doOfTest(final long input,
                          final Function<Long, ByteSize> func,
                          final String expected,
                          final long expectedMultiplier) {
        ByteSize byteSize = func.apply(input);

        assertThat(byteSize.getValueAsStr()).isEqualTo(expected);
        assertThat(byteSize.getBytes()).isEqualTo(input * expectedMultiplier);
    }

    @Test
    void ofBytes_bad() {
        Assertions.assertThatThrownBy(() -> {
            long input = -1;
            ByteSize.ofBytes(input);
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zero() {
        ByteSize byteSize = ByteSize.ZERO;
        assertThat(byteSize.getBytes()).isEqualTo(0);
        assertThat(byteSize.getValueAsStr()).isEqualTo("0B");
    }

    @Test
    void testSerde() throws IOException {
        final ByteSize byteSize = ByteSize.parse("1234K");

        final ObjectMapper objectMapper = new ObjectMapper();

        final StringWriter stringWriter = new StringWriter();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.writeValue(stringWriter, byteSize);

        final String json = stringWriter.toString();

        System.out.println(json);

        final ByteSize byteSize2 = objectMapper.readValue(json, ByteSize.class);

        assertThat(byteSize).isEqualTo(byteSize2);
    }

    private void doParseTest(final long expectedBytes, final String... values) {
        for (final String value : values) {
            LOGGER.info("Testing value {}, expected {}", value, expectedBytes);

            ByteSize byteSize = ByteSize.parse(value);
            assertThat(byteSize.getBytes()).isEqualTo(expectedBytes);
            assertThat(byteSize.getValueAsStr()).isEqualTo(value);
        }
    }
}