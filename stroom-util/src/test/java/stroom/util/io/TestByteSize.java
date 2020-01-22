package stroom.util.io;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        long input = 1024L;
        ByteSize byteSize = ByteSize.ofBytes(input);
        assertThat(byteSize.getBytes()).isEqualTo(input);
        assertThat(byteSize.getValueAsStr()).isEqualTo("1K");
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

    private void doParseTest(final long expectedBytes, final String... values) {
        for (final String value : values) {
            LOGGER.info("Testing value {}, expected {}", value, expectedBytes);

            ByteSize byteSize = ByteSize.parse(value);
            assertThat(byteSize.getBytes()).isEqualTo(expectedBytes);
            assertThat(byteSize.getValueAsStr()).isEqualTo(value);
        }
    }
}