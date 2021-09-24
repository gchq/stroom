package stroom.util.string;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

class TestHexDumpUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestHexDumpUtil.class);

    @Test
    void testHexDump1() throws IOException {
        final Charset charset = StandardCharsets.UTF_8;
        final String input = "This is a longish piece of text to test a hex dump with";
        final String hexDump = HexDumpUtil.hexDump(new ByteArrayInputStream(input.getBytes(charset)), charset);
        LOGGER.info("hexDump: \n{}", hexDump);

        Assertions.assertThat(hexDump)
                .contains("This is a longish piece of text ")
                .contains("to test a hex dump with");

        Assertions.assertThat(hexDump)
                .containsOnlyOnce("\n");
    }

    @Test
    void testHexDump2() throws IOException {
        final Charset charset = StandardCharsets.UTF_8;
        final String input = "Small input 😀.";
        final String hexDump = HexDumpUtil.hexDump(new ByteArrayInputStream(input.getBytes(charset)), charset);
        LOGGER.info("hexDump: \n{}", hexDump);

        Assertions.assertThat(hexDump)
                .contains("Small input ����.");

        Assertions.assertThat(hexDump)
                .doesNotContain("\n")
                .doesNotContain("😀");
    }

    @Test
    void testDecodeAsPrintableChars1() {

        final Charset charset = StandardCharsets.UTF_8;
        final String input = "This is a test";
        final byte[] bytes = input.getBytes(charset);

        final String printableChars = HexDumpUtil.decodeAsPrintableChars(bytes, charset);

        LOGGER.info("printableChars: {}", printableChars);

        Assertions.assertThat(printableChars)
                .isEqualTo(input);
    }

    @Test
    void testDecodeAsPrintableChars2() {

        final Charset charset = StandardCharsets.UTF_8;
        final String input = String.valueOf(new char[]{0x00, 0x09, 0x0a, 0x0d});
        final byte[] bytes = input.getBytes(charset);

        final String printableChars = HexDumpUtil.decodeAsPrintableChars(bytes, charset);

        LOGGER.info("printableChars: {}", printableChars);

        Assertions.assertThat(printableChars)
                .isEqualTo("␀↹↲↩");
    }
}
