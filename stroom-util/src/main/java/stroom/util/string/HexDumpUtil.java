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

import stroom.util.logging.LogUtil;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.Location;
import stroom.util.shared.Range;
import stroom.util.shared.TextRange;
import stroom.util.shared.string.HexDump;
import stroom.util.shared.string.HexDumpLine;

import com.google.common.base.Strings;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class HexDumpUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(HexDumpUtil.class);

    static final char DEFAULT_REPLACEMENT_CHAR = '.';
    static final char NULL_REPLACEMENT_CHAR = '.';
    static final char LINE_FEED_REPLACEMENT_CHAR = '↲';
    static final char CARRIAGE_RETURN_REPLACEMENT_CHAR = '↩';
    static final char TAB_REPLACEMENT_CHAR = '↹';
    static final String DEFAULT_REPLACEMENT_STRING = String.valueOf(DEFAULT_REPLACEMENT_CHAR);
    static final int DECODED_CHARS_START_COL = 117;

    private static final int BYTES_PER_BLOCK = 4;
    // 10 chars wide is good for displaying about 1Tb so should be enough
    private static final int PADDED_LINE_NO_WIDTH = 10;
    private static final String SPACER_AFTER_PADDED_LINE_NO = "  ";

    // Dump line looks like (assuming MAX_BYTES_PER_LINE == 32)
    @SuppressWarnings("checkstyle:linelength")
    // 0000000580  69 64 69 74  61 74 65 20  6f 64 69 6f  20 64 6f 6c  6f 72 20 61  6e 69 6d 69  20 64 65 73  65 72 75 6e  iditate odio dolor animi deserun

    private HexDumpUtil() {
    }

    /**
     * {@link HexDumpUtil#hexDump(InputStream, Charset, int)}
     */
    public static HexDump hexDump(final InputStream inputStream,
                                  final Charset charset) throws IOException {
        return hexDump(inputStream, charset, Integer.MAX_VALUE);
    }

    /**
     * Produces a hex dump similar to the linux xxd binary.
     *
     * @param inputStream     The stream to dump as hex
     * @param charset         The charset to decode single bytes with. Even if a multibyte charset
     *                        is supplied bytes will only be decoded individually.
     * @param maxHexDumpLines The max number of line of hex dump output. A full line contains 32 bytes.
     * @return
     * @throws IOException
     */
    public static HexDump hexDump(final InputStream inputStream,
                                  final Charset charset,
                                  final int maxHexDumpLines) throws IOException {
        return hexDump(inputStream, charset, 0, maxHexDumpLines);
    }

    public static HexDump hexDump(final InputStream inputStream,
                                  final Charset charset,
                                  final long byteOffset,
                                  final int maxHexDumpLines) throws IOException {

        Objects.requireNonNull(inputStream);
        Objects.requireNonNull(charset);
        // We want to start at an offset that is at the start of a line
        final long effectiveByteOffset = byteOffset == 0
                ? 0
                : ((long) ((double) byteOffset / HexDump.MAX_BYTES_PER_LINE)) * HexDump.MAX_BYTES_PER_LINE;

        final CharsetDecoder charsetDecoder = getCharsetDecoder(charset);
        if (effectiveByteOffset > 0) {
            final long lenSkipped = inputStream.skip(effectiveByteOffset);
            if (lenSkipped < effectiveByteOffset) {
                throw new RuntimeException(LogUtil.message("Failed to skip to byte offset {}. Not enough data",
                        effectiveByteOffset));
            }
        }

        // zero based for simpler maths
        int lineOffset = (int) (effectiveByteOffset / HexDump.MAX_BYTES_PER_LINE);
        int lineCount = 0;
        final HexDumpBuilder hexDumpBuilder = new HexDumpBuilder();
        while (lineCount < maxHexDumpLines) {
            final int len = decodeHexDumpLine(inputStream, charsetDecoder, hexDumpBuilder, lineOffset);
            if (len == -1) {
                break;
            }
            lineOffset++;
            lineCount++;
        }

        return hexDumpBuilder.build(charset);
    }

    private static int decodeHexDumpLine(final InputStream inputStream,
                                         final CharsetDecoder charsetDecoder,
                                         final HexDumpBuilder hexDumpBuilder,
                                         final int lineOffset) throws IOException {

        Objects.requireNonNull(inputStream);
        Objects.requireNonNull(charsetDecoder);
        final byte[] lineBytes = new byte[HexDump.MAX_BYTES_PER_LINE];

//        if (!stringBuilder.isEmpty()) {
//            stringBuilder.append("\n");
//        }
        int remaining = HexDump.MAX_BYTES_PER_LINE;
        int len = 0;
        int lineByteCount = 0;
        // Keep reading from the stream till we have a full line of our hex or the
        // stream has ended
        while (remaining > 0 && len >= 0) {
            len = inputStream.read(lineBytes, HexDump.MAX_BYTES_PER_LINE - remaining, remaining);
            if (len > 0) {
                lineByteCount += len;
                remaining -= len;
            }
        }

        // build the hex dump line for these bytes
        appendHexDumpLine(
                hexDumpBuilder,
                lineOffset,
                lineBytes,
                lineByteCount,
                HexDump.MAX_BYTES_PER_LINE,
                charsetDecoder);

        return len;
    }

    private static CharsetDecoder getCharsetDecoder(final Charset charset) {
        final CharsetDecoder charsetDecoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .replaceWith(DEFAULT_REPLACEMENT_STRING);
        return charsetDecoder;
    }

    private static void appendHexDumpLine(final HexDumpBuilder hexDumpBuilder,
                                          final int lineOffset,
                                          final byte[] lineBytes,
                                          final int len,
                                          final int bytesPerLine,
                                          final CharsetDecoder charsetDecoder) {
        final long firstByteNo = ((long) lineOffset) * bytesPerLine;


        // Builder for the hex values for each byte
        final StringBuilder hexStringBuilder = new StringBuilder();
        // Builder for the decoded forms of each individual byte
        final StringBuilder decodedStringBuilder = new StringBuilder();

        final byte[] singleByteArr = new byte[1];
        final ByteBuffer singleByteBuffer = ByteBuffer.wrap(singleByteArr);

        for (int i = 0; i < lineBytes.length; i++) {
            if (i < len) {
                singleByteBuffer.clear();
                singleByteArr[0] = lineBytes[i];

                final String hex = Hex.encodeHexString(singleByteArr, false);
                hexStringBuilder
                        .append(hex)
                        .append(" ");

                // Try to decode the byte as ascii for the right-hand column
                char chr;
                try {
                    final CharBuffer charBuffer = charsetDecoder.decode(singleByteBuffer);
                    chr = charBuffer.charAt(0);
                } catch (final CharacterCodingException e) {
                    chr = DEFAULT_REPLACEMENT_CHAR;
                }
                final char printableChar = asPrintableChar(chr);
                LOGGER.trace("hex: {}, printableChar {}", hex, printableChar);
                decodedStringBuilder.append(printableChar);
            } else {
                // We have gone past the last byte but still need to pad it out with spaces
                // so the decoded text is in the right place. Three spaces because two where
                // the hex pair would be then one for the gap between hex pairs
                hexStringBuilder
                        .append("   ");
                decodedStringBuilder.append(" ");
            }

            // Add an extra space after each 4 byte block
            if (i != 0
                    && (i + 1) % BYTES_PER_BLOCK == 0) {
                hexStringBuilder.append(" ");
            }
        }
        // Add the number of the first byte on the line in hex form, zero padded
        final String paddedLineNo = Strings.padStart(
                Long.toHexString(firstByteNo).toUpperCase(),
                PADDED_LINE_NO_WIDTH,
                '0');
        final String lineStr = paddedLineNo
                + SPACER_AFTER_PADDED_LINE_NO
                + hexStringBuilder
                + decodedStringBuilder;

        final HexDumpLine hexDumpLine = new HexDumpLine(
                lineStr,
                lineOffset + 1,
                Range.of(firstByteNo, firstByteNo + len));
        hexDumpBuilder.addLine(hexDumpLine);
    }

    /**
     * Replaces chr if it is a control character
     */
    public static char asPrintableChar(final char chr) {

        final char charToAppend;
        if ((int) chr < 32) {
            // replace all non-printable chars, ideally with a representative char
            if (chr == 0) {
                charToAppend = NULL_REPLACEMENT_CHAR;
            } else if (chr == '\n') {
                charToAppend = LINE_FEED_REPLACEMENT_CHAR;
            } else if (chr == '\r') {
                charToAppend = CARRIAGE_RETURN_REPLACEMENT_CHAR;
            } else if (chr == '\t') {
                charToAppend = TAB_REPLACEMENT_CHAR;
            } else {
                charToAppend = DEFAULT_REPLACEMENT_CHAR;
            }
//        } else if (chr == ' ') {
//            charToAppend = '␣';
        } else {
            charToAppend = chr;
        }
        return charToAppend;
    }

    public static String decodeAsPrintableChars(final byte[] bytes,
                                                final Charset charset) {
        return decodeAsPrintableChars(bytes, charset, DEFAULT_REPLACEMENT_CHAR);
    }

    public static String decodeAsPrintableChars(final byte[] bytes,
                                                final Charset charset,
                                                final char unknownCharReplacement) {

        final CharsetDecoder charsetDecoder = getCharsetDecoder(charset);
        final StringBuilder stringBuilder = new StringBuilder();
        decodeAsPrintableChars(charsetDecoder, bytes, stringBuilder, unknownCharReplacement);
        return stringBuilder.toString();
    }

    private static void decodeAsPrintableChars(final CharsetDecoder charsetDecoder,
                                               final byte[] bytes,
                                               final StringBuilder stringBuilder,
                                               final char unknownCharReplacement) {

        final ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

        for (int i = 0; i < bytes.length; i++) {
            byteBuffer.position(i);
            byteBuffer.limit(i + 1);
            char chr;
            try {
                final CharBuffer charBuffer = charsetDecoder.decode(byteBuffer);
                chr = charBuffer.charAt(0);
            } catch (final CharacterCodingException e) {
                chr = unknownCharReplacement;
            }
            final char printableChar = asPrintableChar(chr);
            stringBuilder.append(printableChar);
        }
    }

    /**
     * The size in chars of a rendered hex dump for byteCount input bytes.
     */
    public static long calculateHexDumpTotalChars(final long byteCount) {
        final long completeLines = (long) (((double) byteCount) / HexDump.MAX_BYTES_PER_LINE);
        final long remainingBytes = byteCount % HexDump.MAX_BYTES_PER_LINE;
        return (completeLines * HexDump.MAX_CHARS_PER_DUMP_LINE) + remainingBytes;
    }

    /**
     * Calculates the line/col position (in the rendered hex dump) of a given byte offset.
     * The line/col position is of the first of the 2 characters in the hex pair that corresponds to the
     * given byte offset.
     * of the first or second char in this pair.
     * This method knows nothing about the hex dump in question so if byteOffset is greater
     * than the number of bytes in the dump then the returned {@link Location} will also
     * be invalid. It also assumes that whatever is displaying the hex dump is showing all of it
     * up to and including this byte or is only showing some but has had its line numbers adjusted
     * accordingly.
     */
    public static Location calculateLocation(final long byteOffset) {
        return calculateLocation(byteOffset, true);
    }

    /**
     * Calculates the line/col position (in the rendered hex dump) of a given byte offset.
     * The line/col position is of one of the 2 characters in the hex pair that corresponds to the
     * given byte offset. Use {@code isOnFistCharInHexPair} to set whether it returns the location
     * of the first or second char in this pair.
     * This method knows nothing about the hex dump in question so if byteOffset is greater
     * than the number of bytes in the dump then the returned {@link Location} will also
     * be invalid. It also assumes that whatever is displaying the hex dump is showing all of it
     * up to and including this byte or is only showing some but has had its line numbers adjusted
     * accordingly.
     *
     * @param isOnFistCharInHexPair If true returns first char of the hex pair, else the second.
     */
    public static Location calculateLocation(final long byteOffset, final boolean isOnFistCharInHexPair) {

        if (byteOffset < 0) {
            throw new IllegalArgumentException(LogUtil.message("Invalid byteOffset {}. Must be >= 0", byteOffset));
        }

        final int lineNo = (int) (byteOffset / HexDump.MAX_BYTES_PER_LINE + 1); // one based
        final int byteOffsetOnLine = (int) (byteOffset % HexDump.MAX_BYTES_PER_LINE);
        final int blockOffsetOnLine = byteOffsetOnLine / BYTES_PER_BLOCK;
        final int charsPerByte = 3; // Two for the hex pair + one space
        final int colNo = PADDED_LINE_NO_WIDTH // left hand line no col
                + SPACER_AFTER_PADDED_LINE_NO.length()
                + (byteOffsetOnLine * charsPerByte) //
                + blockOffsetOnLine // One extra space after each 4 byte block
                + 1; // one based

        LOGGER.debug("lineNo: {}, colNo: {}, byteOffsetOnLine: {}, blockOffsetOnLine: {}",
                lineNo, colNo, byteOffsetOnLine, blockOffsetOnLine);

        return isOnFistCharInHexPair
                ? DefaultLocation.of(lineNo, colNo)
                : DefaultLocation.of(lineNo, colNo + 1);
    }

    /**
     * Calculates the line/col position (in the rendered hex dump) of a given byte offset.
     * The line/col position is of the decoded char (in the right-hand column) corresponding to the
     * given byte offset.
     * This method knows nothing about the hex dump in question so if byteOffset is greater
     * than the number of bytes in the dump then the returned {@link Location} will also
     * be invalid. It also assumes that whatever is displaying the hex dump is showing all of it
     * up to and including this byte or is only showing some but has had its line numbers adjusted
     * accordingly.
     */
    public static Location calculateDecodedCharLocation(final long byteOffset) {

        if (byteOffset < 0) {
            throw new IllegalArgumentException(LogUtil.message("Invalid byteOffset {}. Must be >= 0", byteOffset));
        }

        final int lineNo = (int) (byteOffset / HexDump.MAX_BYTES_PER_LINE + 1); // one based
        final int byteOffsetOnLine = (int) (byteOffset % HexDump.MAX_BYTES_PER_LINE);
        final int blockOffsetOnLine = byteOffsetOnLine / BYTES_PER_BLOCK;
        final int colNo = DECODED_CHARS_START_COL + byteOffsetOnLine;

        LOGGER.debug("lineNo: {}, colNo: {}, byteOffsetOnLine: {}, blockOffsetOnLine: {}",
                lineNo, colNo, byteOffsetOnLine, blockOffsetOnLine);

        return DefaultLocation.of(lineNo, colNo);
    }

    /**
     * Translates a contiguous range of bytes into a set of highlight blocks defined in terms
     * of line/col positions in the rendered hex dump. It highlights both the middle hex pairs
     * block and the right hand decoded chars column. For example a range of bytes that spans
     * two lines of the hex dump will result in four highlight {@link TextRange}s, two for the
     * two hex pair lines and two for the two decoded chars lines.
     */
    public static List<TextRange> calculateHighlights(final long byteOffsetFromInc,
                                                      final long byteOffsetToInc) {
        validateOffsetArgs(byteOffsetFromInc, byteOffsetToInc);

        long currByteOffsetFromInc = byteOffsetFromInc;
        long currByteOffsetToInc;
        int iteration = 1;
        final List<TextRange> highlights = new ArrayList<>();
        while (true) {
            final long lineNoFrom = currByteOffsetFromInc / HexDump.MAX_BYTES_PER_LINE;
            final long lineNoTo = byteOffsetToInc / HexDump.MAX_BYTES_PER_LINE;
            final boolean isLastLineOfHighlight = lineNoFrom == lineNoTo;

            final long firstByteOnLineOffsetInc = ((currByteOffsetFromInc / HexDump.MAX_BYTES_PER_LINE)
                    * HexDump.MAX_BYTES_PER_LINE);

            if (isLastLineOfHighlight) {
                currByteOffsetToInc = byteOffsetToInc;
            } else {
                final long lastByteOnLineOffsetInc =
                        firstByteOnLineOffsetInc
                                + HexDump.MAX_BYTES_PER_LINE
                                - 1;
                currByteOffsetToInc = lastByteOnLineOffsetInc;
            }

            final TextRange highlight = new TextRange(
                    calculateLocation(currByteOffsetFromInc),
                    calculateLocation(currByteOffsetToInc, false)); // +1 to get 2nd of hex pair
            highlights.add(highlight);

            final TextRange decodeCharHighlight = new TextRange(
                    calculateDecodedCharLocation(currByteOffsetFromInc),
                    calculateDecodedCharLocation(currByteOffsetToInc));
            highlights.add(decodeCharHighlight);

            LOGGER.debug("iteration: {}, lineNoFrom: {}, lineNoTo: {}, firstByteOnLineOffsetInc: {}, " +
                            "isLastLineOfHighlight: {}, " +
                            "currByteOffsetFromInc: {}, currByteOffsetToInc: {}, highlight: {}",
                    iteration,
                    lineNoFrom,
                    lineNoTo,
                    firstByteOnLineOffsetInc,
                    isLastLineOfHighlight,
                    currByteOffsetFromInc,
                    currByteOffsetToInc,
                    highlight);

            if (isLastLineOfHighlight) {
                break;
            }
            // Start next iter at the beginning of the next line.
            currByteOffsetFromInc = firstByteOnLineOffsetInc + HexDump.MAX_BYTES_PER_LINE;
            iteration++;
        }
        return highlights;
    }

    private static void validateOffsetArgs(final long byteOffsetFromInc,
                                           final long byteOffsetToInc) {
        if (byteOffsetFromInc < 0) {
            throw new IllegalArgumentException(LogUtil.message("Invalid byteOffsetFromInc {}. Must be >= 0",
                    byteOffsetFromInc));
        }
        if (byteOffsetToInc < 0) {
            throw new IllegalArgumentException(LogUtil.message("Invalid byteOffsetToExc {}. Must be >= 0",
                    byteOffsetToInc));
        }
        if (byteOffsetToInc <= byteOffsetFromInc) {
            throw new IllegalArgumentException(LogUtil.message(
                    "byteOffsetFromInc {} must be less than byteOffsetToExc {}.",
                    byteOffsetFromInc,
                    byteOffsetToInc));
        }
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    private static class HexDumpBuilder {

        private final List<HexDumpLine> lines = new ArrayList<>();
        private long startByteOffsetInc = -1;
        private long endByteOffsetExc = -1;

        private void addLine(final HexDumpLine line) {
            if (startByteOffsetInc == -1) {
                startByteOffsetInc = line.getByteOffsetRange().getFrom();
            }
            endByteOffsetExc = line.getByteOffsetRange().getTo();
            lines.add(line);
        }

        private HexDump build(final Charset charset) {
            return new HexDump(
                    lines,
                    charset,
                    new Range<>(startByteOffsetInc, endByteOffsetExc));
        }

        private boolean isEmpty() {
            return lines.isEmpty();
        }
    }
}
