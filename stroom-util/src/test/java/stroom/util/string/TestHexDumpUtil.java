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

package stroom.util.string;

import stroom.test.common.TestUtil;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.Location;
import stroom.util.shared.Range;
import stroom.util.shared.TextRange;
import stroom.util.shared.string.HexDump;

import com.google.common.base.Strings;
import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

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
        final String hexDumpStr = hexDump.getHexDumpAsStr();
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

    @TestFactory
    Stream<DynamicTest> testCalculateLocation() throws IOException {

        final Charset charset = StandardCharsets.US_ASCII;
        final String input = """
                Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut \
                labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco \
                laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in \
                voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat \
                non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.""";

        final byte[] bytes = input.getBytes(charset);

        // Dump the full thing
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        final HexDump hexDump = HexDumpUtil.hexDump(inputStream, charset);
        LOGGER.info("hexDump: \n{}", hexDump.getHexDumpAsStr());

        return TestUtil.buildDynamicTestStream()
                .withInputType(Long.class)
                .withOutputType(Location.class)
                .withTestFunction(testCase ->
                        HexDumpUtil.calculateLocation(testCase.getInput()))
                .withSimpleEqualityAssertion()
                // First byte on first line
                .addCase(0L, DefaultLocation.of(1, 13))
                // Fifth byte on first line (i.e. 2nd block)
                .addCase(4L, DefaultLocation.of(1, 26))
                // Last byte on first line
                .addCase(31L, DefaultLocation.of(1, 113))
                // First byte on second line
                .addCase(32L, DefaultLocation.of(2, 13))
                // 11th byte on second line (third byte in 3rd block)
                .addCase(42L, DefaultLocation.of(2, 45))
                // 12th byte on second line (last byte in 3rd block)
                .addCase(43L, DefaultLocation.of(2, 48))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testCalculateHighlights() throws IOException {

        final Charset charset = StandardCharsets.US_ASCII;
        final String input = """
                Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut \
                labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco \
                laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in \
                voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat \
                non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.""";

        final byte[] bytes = input.getBytes(charset);

        // Dump the full thing
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        final HexDump hexDump = HexDumpUtil.hexDump(inputStream, charset);
        LOGGER.info("hexDump: \n{}", hexDump.getHexDumpAsStr());

        // Run the test then copy the hexdump into a text editor to check the line/col positions.
        // NOTE: the 'to' col is the 2nd char of the hex pair

        return TestUtil.buildDynamicTestStream()
                .withInputTypes(Long.class, Long.class)
                .withWrappedOutputType(new TypeLiteral<List<TextRange>>() {
                })
                .withTestFunction(testCase -> {
                    final Long byteOffsetFrom = testCase.getInput()._1;
                    final Long byteOffsetTo = testCase.getInput()._2;
                    return HexDumpUtil.calculateHighlights(byteOffsetFrom, byteOffsetTo);
                })
                .withAssertions(testOutcome -> {
                    // Order of highlights is not important
                    Assertions.assertThat(testOutcome.getActualOutput())
                            .containsExactlyInAnyOrderElementsOf(testOutcome.getExpectedOutput());
                })
                .addCase(
                        Tuple.of(0L, 4L),
                        List.of(TextRange.of(// hex pairs
                                        DefaultLocation.of(1, 13),
                                        DefaultLocation.of(1, 27)),
                                TextRange.of(// decoded chars
                                        DefaultLocation.of(1, 117),
                                        DefaultLocation.of(1, 121))))
                .addCase(
                        Tuple.of(2L, 31L),
                        List.of(TextRange.of(// hex pairs
                                        DefaultLocation.of(1, 19),
                                        DefaultLocation.of(1, 114)),
                                TextRange.of(// decoded chars
                                        DefaultLocation.of(1, 119),
                                        DefaultLocation.of(1, 148))))
                // One full line
                .addCase(
                        Tuple.of(32L, 63L),
                        List.of(TextRange.of(// hex pairs
                                        DefaultLocation.of(2, 13),
                                        DefaultLocation.of(2, 114)),
                                TextRange.of(// decoded chars
                                        DefaultLocation.of(2, 117),
                                        DefaultLocation.of(2, 148))))
                // Two partial lines
                .addCase(
                        Tuple.of(36L, 71L),
                        List.of(TextRange.of(// hex pairs
                                        DefaultLocation.of(2, 26),
                                        DefaultLocation.of(2, 114)),
                                TextRange.of(// hex pairs
                                        DefaultLocation.of(3, 13),
                                        DefaultLocation.of(3, 36)),
                                TextRange.of(// decoded chars
                                        DefaultLocation.of(2, 121),
                                        DefaultLocation.of(2, 148)),
                                TextRange.of(// decoded chars
                                        DefaultLocation.of(3, 117),
                                        DefaultLocation.of(3, 124)))
                )
                // Three lines, two partial, one full
                .addCase(
                        Tuple.of(3L, 68L),
                        List.of(
                                TextRange.of(// hex pairs
                                        DefaultLocation.of(1, 22),
                                        DefaultLocation.of(1, 114)), // |    <-------->|
                                TextRange.of(// hex pairs
                                        DefaultLocation.of(2, 13),
                                        DefaultLocation.of(2, 114)), // |<------------>|
                                TextRange.of(// hex pairs
                                        DefaultLocation.of(3, 13),
                                        DefaultLocation.of(3, 27)),  // |<--->         |
                                TextRange.of(// decoded chars
                                        DefaultLocation.of(1, 120),
                                        DefaultLocation.of(1, 148)), //                  |   <--->|
                                TextRange.of(// decoded chars
                                        DefaultLocation.of(2, 117),
                                        DefaultLocation.of(2, 148)), //                  |<------>|
                                TextRange.of(// decoded chars
                                        DefaultLocation.of(3, 117),
                                        DefaultLocation.of(3, 121)))  //                 |<-->    |
                )
                .build();
    }

    @Test
    void testCalculateLocation_invalid() {
        Assertions.assertThatThrownBy(() ->
                        HexDumpUtil.calculateLocation(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
