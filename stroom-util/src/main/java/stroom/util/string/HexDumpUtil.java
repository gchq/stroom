package stroom.util.string;

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
import java.util.Objects;

public class HexDumpUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(HexDumpUtil.class);

    private static final int MAX_BYTES_PER_LINE = 32;
    //    private static final char REPLACEMENT_CHAR = '�';
    private static final char REPLACEMENT_CHAR = '.';
    private static final String REPLACEMENT_STRING = String.valueOf(REPLACEMENT_CHAR);

    private HexDumpUtil() {
    }

    /**
     * {@link HexDumpUtil#hexDump(InputStream, Charset, int)}
     */
    public static String hexDump(final InputStream inputStream,
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
    public static String hexDump(final InputStream inputStream,
                                 final Charset charset,
                                 final int maxHexDumpLines) throws IOException {

        Objects.requireNonNull(inputStream);
        Objects.requireNonNull(charset);
        Objects.requireNonNull(inputStream);

        final CharsetDecoder charsetDecoder = getCharsetDecoder(charset);

        final StringBuilder stringBuilder = new StringBuilder();
        final byte[] lineBytes = new byte[MAX_BYTES_PER_LINE];
        int lineOffset = 0; // zero based for simpler maths
        while (lineOffset < maxHexDumpLines) {
            if (lineOffset != 0) {
                stringBuilder.append("\n");
            }
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
                    stringBuilder,
                    lineOffset,
                    lineBytes,
                    lineByteCount,
                    MAX_BYTES_PER_LINE,
                    charsetDecoder);
            if (len == -1) {
                break;
            }
            lineOffset++;
        }

        return stringBuilder.toString();
    }

    private static CharsetDecoder getCharsetDecoder(final Charset charset) {
        final CharsetDecoder charsetDecoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .replaceWith(REPLACEMENT_STRING);
        return charsetDecoder;
    }

    private static void appendHexDumpLine(final StringBuilder stringBuilder,
                                          final long lineOffset,
                                          final byte[] lineBytes,
                                          final int len,
                                          final int bytesPerLine,
                                          final CharsetDecoder charsetDecoder) {
        final long firstByteNo = lineOffset * bytesPerLine;
        // Add the number of the first byte on the line in hex form, zero padded
        stringBuilder
                .append(Strings.padStart(Long.toHexString(firstByteNo), 10, '0'))
                .append("  ");

        // Builder for the hex values for each byte
        final StringBuilder hexStringBuilder = new StringBuilder();
        // Builder for the decoded forms of each individual byte
        final StringBuilder decodedStringBuilder = new StringBuilder();

        for (int i = 0; i < lineBytes.length; i++) {
            if (i < len) {
                byte[] arr = new byte[]{lineBytes[i]};

                final String hex = Hex.encodeHexString(arr);
                hexStringBuilder
                        .append(hex)
                        .append(" ");

                final ByteBuffer byteBuffer = ByteBuffer.wrap(arr);
                char chr;
                try {
                    CharBuffer charBuffer = charsetDecoder.decode(byteBuffer);
                    chr = charBuffer.charAt(0);
                } catch (CharacterCodingException e) {
                    chr = REPLACEMENT_CHAR;
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
        stringBuilder
                .append(hexStringBuilder)
                .append(decodedStringBuilder);
    }

    /**
     * Replaces chr if it is a control character
     */
    public static char asPrintableChar(final char chr) {

        final char charToAppend;
        if ((int) chr < 32) {
            // replace all non-printable chars, ideally with a representative char
            if (chr == 0) {
                charToAppend = '.';
            } else if (chr == '\n') {
                charToAppend = '↲';
            } else if (chr == '\r') {
                charToAppend = '↩';
            } else if (chr == '\t') {
                charToAppend = '↹';
            } else {
                charToAppend = REPLACEMENT_CHAR;
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
                chr = REPLACEMENT_CHAR;
            }
            final char printableChar = asPrintableChar(chr);
            stringBuilder.append(printableChar);
        }
    }
}
