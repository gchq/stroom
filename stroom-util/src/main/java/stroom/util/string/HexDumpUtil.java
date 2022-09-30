package stroom.util.string;

import stroom.util.logging.LogUtil;
import stroom.util.shared.Range;

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
import java.util.Optional;
import java.util.stream.Collectors;

public class HexDumpUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(HexDumpUtil.class);

    static final char DEFAULT_REPLACEMENT_CHAR = '.';
    static final char NULL_REPLACEMENT_CHAR = '.';
    static final char LINE_FEED_REPLACEMENT_CHAR = '↲';
    static final char CARRIAGE_RETURN_REPLACEMENT_CHAR = '↩';
    static final char TAB_REPLACEMENT_CHAR = '↹';
    static final String DEFAULT_REPLACEMENT_STRING = String.valueOf(DEFAULT_REPLACEMENT_CHAR);

    // Dump line looks like (assuming MAX_BYTES_PER_LINE == 32)
    @SuppressWarnings("checkstyle:linelength")
    // 0000000580  69 64 69 74  61 74 65 20  6f 64 69 6f  20 64 6f 6c  6f 72 20 61  6e 69 6d 69  20 64 65 73  65 72 75 6e  iditate odio dolor animi deserun

    private static final int MAX_BYTES_PER_LINE = 32;
    // This depends on how we render the line of MAX_BYTES_PER_LINE (includes the  at the end \n)
    private static final int MAX_CHARS_PER_DUMP_LINE = 149;

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
        long effectiveByteOffset = byteOffset == 0
                ? 0
                : ((long)((double) byteOffset / MAX_BYTES_PER_LINE)) * MAX_BYTES_PER_LINE;

        final CharsetDecoder charsetDecoder = getCharsetDecoder(charset);
        final HexDumpBuilder hexDumpBuilder = new HexDumpBuilder();
        if (effectiveByteOffset > 0) {
            final long lenSkipped = inputStream.skip(effectiveByteOffset);
            if (lenSkipped < effectiveByteOffset) {
                throw new RuntimeException(LogUtil.message("Failed to skip to byte offset {}. Not enough data",
                        effectiveByteOffset));
            }
        }

        // zero based for simpler maths
        int lineOffset = (int) (effectiveByteOffset / MAX_BYTES_PER_LINE);
        int lineCount = 0;
        while (lineCount < maxHexDumpLines) {
            int len = decodeHexDumpLine(inputStream, charsetDecoder, hexDumpBuilder, lineOffset);
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
        final byte[] lineBytes = new byte[MAX_BYTES_PER_LINE];

//        if (!stringBuilder.isEmpty()) {
//            stringBuilder.append("\n");
//        }
        int remaining = MAX_BYTES_PER_LINE;
        int len = 0;
        int lineByteCount = 0;
        // Keep reading from the stream till we have a full line of our hex or the
        // stream has ended
        while (remaining > 0 && len >= 0) {
            len = inputStream.read(lineBytes, MAX_BYTES_PER_LINE - remaining, remaining);
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
                MAX_BYTES_PER_LINE,
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

        // Add the number of the first byte on the line in hex form, zero padded
        final StringBuilder lineStringBuilder = new StringBuilder();
        lineStringBuilder
                .append(Strings.padStart(
                        Long.toHexString(firstByteNo).toUpperCase(),
                        10,
                        '0'))
                .append("  ");

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

                char chr;
                try {
                    final CharBuffer charBuffer = charsetDecoder.decode(singleByteBuffer);
                    chr = charBuffer.charAt(0);
                } catch (CharacterCodingException e) {
                    chr = DEFAULT_REPLACEMENT_CHAR;
                }
                final char printableChar = asPrintableChar(chr);
                LOGGER.trace("hex: {}, printableChar {}", hex, printableChar);
                decodedStringBuilder.append(printableChar);
            } else {
                hexStringBuilder
                        .append("   ");
                decodedStringBuilder.append(" ");
            }

            if (i != 0
                    && (i + 1) % 4 == 0) {
                hexStringBuilder.append(" ");
            }
        }
        lineStringBuilder
                .append(hexStringBuilder)
                .append(decodedStringBuilder);

        final HexDumpLine hexDumpLine = new HexDumpLine(
                lineStringBuilder.toString(),
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

    public static String decodeAsPrintableChars(final byte[] bytes, final Charset charset) {

        final CharsetDecoder charsetDecoder = getCharsetDecoder(charset);
        final StringBuilder stringBuilder = new StringBuilder();
        decodeAsPrintableChars(charsetDecoder, bytes, stringBuilder);
        return stringBuilder.toString();
    }

    public static int getMaxBytesPerLine() {
        return MAX_BYTES_PER_LINE;
    }

    public static int getDumpCharsPerLine() {
        return MAX_BYTES_PER_LINE;
    }

    private static void decodeAsPrintableChars(final CharsetDecoder charsetDecoder,
                                               final byte[] bytes,
                                               final StringBuilder stringBuilder) {

        final ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

        for (int i = 0; i < bytes.length; i++) {
            byteBuffer.position(i);
            byteBuffer.limit(i + 1);
            char chr;
            try {
                final CharBuffer charBuffer = charsetDecoder.decode(byteBuffer);
                chr = charBuffer.charAt(0);
            } catch (CharacterCodingException e) {
                chr = DEFAULT_REPLACEMENT_CHAR;
            }
            final char printableChar = asPrintableChar(chr);
            stringBuilder.append(printableChar);
        }
    }

    public static class HexDump {
        private final List<HexDumpLine> lines;
        // The charset of the decoded bytes.
        private final Charset charset;
        // The range covered by all lines in this hex dump
        private final Range<Long> byteOffsetRange;

        public HexDump(final List<HexDumpLine> lines,
                       final Charset charset,
                       final Range<Long> byteOffsetRange) {
            this.lines = lines;
            this.charset = charset;
            this.byteOffsetRange = byteOffsetRange;
        }

        public List<HexDumpLine> getLines() {
            return lines;
        }

        public boolean isEmpty() {
            return lines.isEmpty();
        }

        public Charset getCharset() {
            return charset;
        }

        public int getMaxBytesPerLine() {
            return MAX_BYTES_PER_LINE;
        }

        public int getMaxDumpCharsPerLine() {
            return MAX_CHARS_PER_DUMP_LINE;
        }

        public Optional<HexDumpLine> getLine(final int lineNo) {
            if (lines.isEmpty()) {
                return Optional.empty();
            } else {
                final long firstLineNo = lines.get(0).getLineNo();
                if (lineNo < firstLineNo) {
                    return Optional.empty();
                } else {
                    final long idx = lineNo - firstLineNo;
                    if (idx >= lines.size()) {
                        return Optional.empty();
                    } else {
                        return Optional.ofNullable(lines.get((int) idx));
                    }
                }
            }
        }

        public int getLineCount() {
            return lines.size();
        }

        public Optional<HexDumpLine> getFirstLine() {
            if (lines.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(lines.get(0));
            }
        }

        public Optional<HexDumpLine> getLastLine() {
            if (lines.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(lines.get(lines.size() - 1));
            }
        }

        /**
         * @return Number of bytes rendered by the hex dump.
         */
        public long getDumpByteCount() {
            return byteOffsetRange.size().longValue();
        }

        /**
         * @return The range of bytes rendered by the hex dump, e.g. if only a subset of
         * an {@link InputStream} is dumped.
         */
        public Range<Long> getByteOffsetRange() {
            return byteOffsetRange;
        }

        /**
         * @return The size of the hex dump when output as a string. Not the
         * number of chars rendered by the hex dump.
         */
        public long getDumpCharCount() {
            return lines.stream()
                    .mapToInt(HexDumpLine::getDumpLineCharCount)
                    .sum();
        }

        public String getHexDumpAsStr() {
            return lines.stream()
                    .map(HexDumpLine::getLine)
                    .collect(Collectors.joining("\n"));
        }

        @Override
        public String toString() {
            return "HexDump{" +
                    "lines=" + lines +
                    ", charset=" + charset +
                    '}';
        }
    }

    public static class HexDumpLine {
        private final String line;
        // The number (1 based) of this line in a complete hex dump
        private final int lineNo;
        // The range of bytes covered by this line
        private final Range<Long> byteOffsetRange;

        public HexDumpLine(final String line,
                           final int lineNo,
                           final Range<Long> byteOffsetRange) {
            this.line = line;
            this.lineNo = lineNo;
            this.byteOffsetRange = byteOffsetRange;
        }

        public String getLine() {
            return line;
        }

        public int getLineNo() {
            return lineNo;
        }

        public long getByteCount() {
            return byteOffsetRange.size().longValue();
        }

        public Range<Long> getByteOffsetRange() {
            return byteOffsetRange;
        }

        /**
         * @return The size of the hex dump line when output as a string. Not the
         * number of chars rendered by the hex dump.
         */
        public int getDumpLineCharCount() {
            return line.length();
        }

        @Override
        public String toString() {
            return "HexDumpLine{" +
                    "line='" + line + '\'' +
                    ", lineNo=" + lineNo +
                    ", byteOffsetRange=" + byteOffsetRange +
                    '}';
        }
    }

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
