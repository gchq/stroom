package stroom.data.store.impl;

import com.google.common.base.Strings;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

class TestDataFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestDataFetcher.class);

    @Test
    void testHexViewer() throws IOException {

        final int bytesPerLine = 32;
        final Charset charset = StandardCharsets.UTF_8;
        final CharsetDecoder charsetDecoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);

        final String sourceStr = "These are a load of boring words to view in hex.\n" +
                "And so are these, waffle, waffle, etc.\n" +
                "And here is a cheeky emoji 😀.";

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(
                sourceStr.getBytes(charset));

        final StringBuilder stringBuilder = new StringBuilder();
        int lineNo = 0;
        while (true) {
            if (lineNo != 0) {
                stringBuilder.append("\n");
            }
            final byte[] lineBytes = new byte[bytesPerLine];
            final int len = byteArrayInputStream.read(lineBytes);

            if (len == -1) {
                break;
            }

            bytesToString(stringBuilder, lineNo++, lineBytes, len, bytesPerLine, charsetDecoder);
        }

        LOGGER.debug("\n{}", stringBuilder.toString());
    }

    private void bytesToString(final StringBuilder stringBuilder,
                               final long lineNo,
                               final byte[] lineBytes,
                               final int len,
                               final int bytesPerLine,
                               final CharsetDecoder charsetDecoder) {
        final long firstByteNo = lineNo * bytesPerLine;
        stringBuilder
                .append(Strings.padStart(Long.toHexString(firstByteNo), 10, '0'))
                .append(" ");

        final StringBuilder hexStringBuilder = new StringBuilder();
        final StringBuilder decodedStringBuilder = new StringBuilder();
        for (int i = 0; i < lineBytes.length; i++) {
            if (i < len) {
                byte[] arr = new byte[]{lineBytes[i]};

                hexStringBuilder
                        .append(Hex.encodeHexString(arr))
                        .append(" ");

                ByteBuffer byteBuffer = ByteBuffer.wrap(arr);
                char chr;
                try {
                    CharBuffer charBuffer = charsetDecoder.decode(byteBuffer);
                    chr = charBuffer.charAt(0);
                } catch (CharacterCodingException e) {
                    chr = '�';
                }
                appendChar(chr, decodedStringBuilder);

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

    private void appendChar(final char c, final StringBuilder stringBuilder) {
        if (c == 0) {
            stringBuilder.append(" ");
        } else if (c == ' ') {
            stringBuilder.append('␣');
        } else if (c == '\n') {
            stringBuilder.append('↲');
        } else if (c == '\r') {
            stringBuilder.append('↩');
        } else if (c == '\t') {
            stringBuilder.append('↹');
        } else {
            stringBuilder.append(c);
        }
    }

}
