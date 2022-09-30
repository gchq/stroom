package stroom.util.string;

import stroom.util.shared.Range;
import stroom.util.shared.string.HexDump;

import com.google.common.base.Strings;
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
        final HexDump hexDump = HexDumpUtil.hexDump(new ByteArrayInputStream(input.getBytes(charset)), charset);
        final String hexDumpStr = hexDump.getHexDumpAsStr();
        LOGGER.info("hexDump: \n{}", hexDumpStr);

        Assertions.assertThat(hexDumpStr)
                .contains("This is a longish piece of text ")
                .contains("to test a hex dump with");

        Assertions.assertThat(hexDumpStr)
                .containsOnlyOnce("\n");
    }


    @Test
    void testHexDump_smallInput() throws IOException {
        final Charset charset = StandardCharsets.UTF_8;
        final String input = "Small input 😀.";
        final HexDump hexDump = HexDumpUtil.hexDump(new ByteArrayInputStream(input.getBytes(charset)), charset);
        final String hexDumpStr = hexDump.getHexDumpAsStr();
        LOGGER.info("hexDump: \n{}", hexDumpStr);

        final String replacedChars = Strings.repeat(HexDumpUtil.DEFAULT_REPLACEMENT_STRING, 4);
        Assertions.assertThat(hexDumpStr)
                .contains("Small input " + replacedChars + ".");

        Assertions.assertThat(hexDumpStr)
                .doesNotContain("\n")
                .doesNotContain("😀");
    }

    @Test
    void testHexDump_fromByteOffset() throws IOException {
        final Charset charset = StandardCharsets.US_ASCII;
        final String input = """
                Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut \
                labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco \
                laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in \
                voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat \
                non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.""";

        final byte[] bytes = input.getBytes(charset);

        // Dump the full thing
        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        HexDump hexDump = HexDumpUtil.hexDump(inputStream, charset);
        String hexDumpStr = hexDump.getHexDumpAsStr();
        LOGGER.info("hexDump: \n{}", hexDump.getHexDumpAsStr());

        final String fourthLine1 = hexDump.getLine(4).get().getLine();
        final String fifthLine1 = hexDump.getLine(5).get().getLine();

        // Dump from our offset
        inputStream = new ByteArrayInputStream(bytes);
        final int lineCount = 2;
        final int offset = 100; // 100 is in the middle of a hex dump line 4
        final int expectedFirstOffset = HexDump.MAX_BYTES_PER_LINE * 3;

        hexDump = HexDumpUtil.hexDump(inputStream, charset, offset, lineCount);
        LOGGER.info("hexDump: \n{}", hexDump.getHexDumpAsStr());

        Assertions.assertThat(hexDump.getLineCount())
                .isEqualTo(lineCount);
        Assertions.assertThat(hexDump.getByteOffsetRange().getFrom())
                .isEqualTo(expectedFirstOffset);

        final String fourthLine2 = hexDump.getLine(4).get().getLine();
        final String fifthLine2 = hexDump.getLine(5).get().getLine();

        Assertions.assertThat(fourthLine1)
                .isEqualTo(fourthLine2);
        Assertions.assertThat(fifthLine1)
                .isEqualTo(fifthLine2);

        hexDump.getLines().forEach(hexDumpLine -> {
            final Range<Long> offsetRange = hexDumpLine.getByteOffsetRange();
            // 32 bytes per line
            Assertions.assertThat(offsetRange.getTo() - offsetRange.getFrom())
                    .isEqualTo(HexDump.MAX_BYTES_PER_LINE);
        });
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
        final String input = String.valueOf(new char[]{
                0x00, // null
                0x09, // tab
                0x0a, // line feed
                0x0d}); // carriage return
        final byte[] bytes = input.getBytes(charset);

        final String printableChars = HexDumpUtil.decodeAsPrintableChars(bytes, charset);

        LOGGER.info("printableChars: {}", printableChars);

        final String expected = String.valueOf(HexDumpUtil.NULL_REPLACEMENT_CHAR) +
                HexDumpUtil.TAB_REPLACEMENT_CHAR +
                HexDumpUtil.LINE_FEED_REPLACEMENT_CHAR +
                HexDumpUtil.CARRIAGE_RETURN_REPLACEMENT_CHAR;

        Assertions.assertThat(printableChars)
                .isEqualTo(expected);
    }
}
