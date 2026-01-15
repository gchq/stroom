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

package stroom.pipeline.reader;

import stroom.util.logging.LogUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.CharArrayReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

public class TestInvalidXmlCharReplacementFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestInvalidXmlCharReplacementFilter.class);

    public static final char REPLACE_CHAR = 0xfffd;

    private final int[] testChunkSizes = {1, 2, 3, 5, 7, 11, 13, 16};
    private char[] bmpRepTwice;
    private char[] brokenUTF16Str;

    private static boolean isValidXmlCP(final int ch, final XmlChars mode) {
        if (mode.getClass().equals(Xml10Chars.class)) {
            return ch == 0x9
                    || ch == 0xa
                    || ch == 0xd
                    || (ch >= 0x20 && ch <= 0xd7ff)
                    || (ch >= 0xe000 && ch <= 0xfffd)
                    || (ch >= 0x10000 && ch <= 0x10ffff);
        }

        // See
        // https://en.wikipedia.org/wiki/Valid_characters_in_XML
        // https://www.w3.org/TR/xml11/#NT-RestrictedChar
        if (mode.getClass().equals(Xml11Chars.class)) {
            return (ch > 0x8 && ch < 0xb ||
                    ch == 0xd ||
                    ch > 0x1f && ch < 0x7f ||
                    ch == 0x85 ||
                    ch > 0x9f && ch <= 0xd7ff)
                    || (ch >= 0xe000 && ch <= 0xfffd)
                    || (ch >= 0x10000 && ch <= 0x10ffff);
        }

        return false;
    }

    @BeforeEach
    void setUp() {
        bmpRepTwice = new char[0x20000];
        for (int i = 0; i != bmpRepTwice.length; ++i) {
            bmpRepTwice[i] = (char) i;
        }
        brokenUTF16Str = new char[0x20000];
        final Random gen = new Random(12345);
        int i = 0;
        while (i < brokenUTF16Str.length) {
            final int randomCP = gen.nextInt() & (~Integer.MIN_VALUE) % Character.MAX_CODE_POINT;
            final char[] rchars = Character.toChars(randomCP);
            final int cpylen = (i < (brokenUTF16Str.length - 1))
                    ? rchars.length
                    : 1;
            System.arraycopy(rchars, 0, brokenUTF16Str, i, cpylen);
            i += cpylen;
        }
    }

    private Reader getReader(final char[] data, final XmlChars mode) {
        final Reader r = new CharArrayReader(data);
        return InvalidXmlCharFilter.createReplaceCharsFilter(r, mode, true, REPLACE_CHAR);
    }

    private void readCharBMP(final char[] testData, final XmlChars mode) throws IOException {
        final Reader r = getReader(testData, mode);
        for (int idx = 0; idx != testData.length; ++idx) {
            final int rch = r.read();
            assertThat(isValidXmlCP(rch, mode)).isTrue();
            assertThat((char) idx == rch || rch == REPLACE_CHAR).isTrue();
        }
        final int rch = r.read();
        assertThat(rch).isEqualTo(-1);
    }

    private void readArrayBMP(final char[] testData, final XmlChars mode) throws IOException {
        for (final int chunkSize : testChunkSizes) {
            final Reader r = getReader(testData, mode);
            final char[] buf = new char[chunkSize];
            char origchar = 0;
            final int trail_size = testData.length % chunkSize;
            final int num_chunks = testData.length / chunkSize;
            for (int idx = 0; idx <= num_chunks; ++idx) {
                final int expect_read = (idx == num_chunks)
                        ? trail_size
                        : buf.length;
                if (expect_read == 0) {
                    break;
                }
                final int rch = r.read(buf, 0, buf.length);
                // as idx < floor(char_len /chunk_len)
                assertThat(rch).isEqualTo(expect_read);
                for (int i = 0; i != expect_read; ++i, ++origchar) {
                    assertThat(buf[i]).isEqualTo(isValidXmlCP(origchar, mode)
                            ? origchar
                            : REPLACE_CHAR);
                }
            }
            final int reof = r.read();
            assertThat(reof).isEqualTo(-1);
        }
    }

    private void readCharFullUTF16(final char[] testData, final XmlChars mode) throws IOException {
        final Reader r = getReader(testData, mode);
        for (int idx = 0; idx != testData.length; ++idx) {
            final int rch = r.read();
            assertThat(rch).isGreaterThan(0);
            if (Character.isHighSurrogate((char) rch)) {
                final int rchl = r.read();
                assertThat(rchl).isGreaterThan(0);
                assertThat(Character.isLowSurrogate((char) rchl)).isTrue();
                assertThat(isValidXmlCP(Character.toCodePoint((char) rch, (char) rchl), mode)).isTrue();
                assertThat(testData[idx] == rch || (rch == REPLACE_CHAR && rchl == REPLACE_CHAR)).isTrue();
                ++idx;
            } else {
                assertThat(isValidXmlCP(rch, mode)).isTrue();
                assertThat(testData[idx] == rch || rch == REPLACE_CHAR).isTrue();
            }
        }
        final int rch = r.read();
        assertThat(rch).isEqualTo(-1);
    }

    private void readArrayFullUTF16(final char[] testData, final XmlChars mode)
            throws IOException {
        int counter = 0;
        for (final int chunkSize : testChunkSizes) {
            final Reader r = getReader(testData, mode);
            final char[] buf = new char[chunkSize];
            int origidx = 0;
            final int trail_size = testData.length % chunkSize;
            final int num_chunks = testData.length / chunkSize;
            char highSurrogate = 0;
            for (int idx = 0; idx <= num_chunks; ++idx) {
                final int expect_read = (idx == num_chunks)
                        ? trail_size
                        : buf.length;
                if (expect_read == 0) {
                    break;
                }
                final int rch = r.read(buf, 0, buf.length);
                counter++;
                if (rch != expect_read) {
                    // as idx < floor(char_len / chunk_len)
                    assertThat(rch).isEqualTo(expect_read);
                }
                for (int i = 0; i != expect_read; ++i, ++origidx) {
                    if (highSurrogate != 0) {
                        assertThat(Character.isLowSurrogate(buf[i])).isTrue();
                        assertThat(isValidXmlCP(Character.toCodePoint(highSurrogate, buf[i]), mode)).isTrue();
                        assertThat(brokenUTF16Str[origidx] == buf[i]).isTrue();
                        highSurrogate = 0;
                    } else {
                        if (Character.isHighSurrogate(buf[i])) {
                            highSurrogate = buf[i];
                        } else {
                            final char chr = buf[i];

                            // All chars by this point should be valid as we have removed/replaced
                            // the invalid ones.
                            assertThat(isValidXmlCP(chr, mode))
                                    .withFailMessage(() ->
                                            LogUtil.message(
                                                    "Expecting [{}] (unicode hex: {}) to be valid",
                                                    chr, Integer.toHexString(chr)))
                                    .isTrue();

                            assertThat(brokenUTF16Str[origidx] == buf[i] || buf[i] == REPLACE_CHAR).isTrue();
                        }
                    }
                }
            }
            final int reof = r.read();
            assertThat(reof).isEqualTo(-1);
            LOGGER.info("Tested {} chars", counter);
        }
    }

    @Test
    void testReadCharBMP_XML10() throws IOException {
        readCharBMP(bmpRepTwice, new Xml10Chars());
    }

    @Test
    void testReadCharBMP_XML11() throws IOException {
        readCharBMP(bmpRepTwice, new Xml11Chars());
    }

    @Test
    void testReadArrayBMP_XML10() throws IOException {
        readArrayBMP(bmpRepTwice, new Xml10Chars());
    }

    @Test
    void testReadArrayBMP_XML11() throws IOException {
        readArrayBMP(bmpRepTwice, new Xml11Chars());
    }

    @Test
    void testReadCharFullUTF16_XML10() throws IOException {
        readCharFullUTF16(brokenUTF16Str, new Xml10Chars());
    }

    @Test
    void testReadCharFullUTF16_XML11() throws IOException {
        readCharFullUTF16(brokenUTF16Str, new Xml11Chars());
    }

    @Test
    void testReadArrayFullUTF16_XML10() throws IOException {
        readArrayFullUTF16(brokenUTF16Str, new Xml10Chars());
    }

    @Test
    void testReadArrayFullUTF16_XML11() throws IOException {
        readArrayFullUTF16(brokenUTF16Str, new Xml11Chars());
    }
}
