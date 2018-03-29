/*
 * Copyright 2016 Crown Copyright
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

import org.junit.Before;
import org.junit.Test;

import java.io.CharArrayReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestInvalidXMLCharFilterReader {
    public static final char REPLACE_CHAR = 0xfffd;

    private final int[] m_test_chunk_sizes = {1, 2, 3, 5, 7, 11, 13, 16};
    private char[] m_bmp_rep_twice, m_brokenutf16str;

    private static boolean isValidXmlCP(final int ch, final InvalidXMLCharFilterReader.XMLmode mode) {
        switch (mode) {
            case XML_1_0:
                return ch == 0x9 || ch == 0xa || ch == 0xd || (ch >= 0x20 && ch <= 0xd7ff) || (ch >= 0xe000 && ch <= 0xfffd)
                        || (ch >= 0x10000 && ch <= 0x10ffff);
            case XML_1_1:
                return (ch >= 0x1 && ch <= 0xd7ff) || (ch >= 0xe000 && ch <= 0xfffd) || (ch >= 0x10000 && ch <= 0x10ffff);
            default:
                assertTrue(false);
        }
        return false;
    }

    @Before
    public void setUp() {
        m_bmp_rep_twice = new char[0x20000];
        for (int i = 0; i != m_bmp_rep_twice.length; ++i)
            m_bmp_rep_twice[i] = (char) i;
        m_brokenutf16str = new char[0x20000];
        final Random gen = new Random(12345);
        int i = 0;
        while (i < m_brokenutf16str.length) {
            final int randomCP = gen.nextInt() & (~Integer.MIN_VALUE) % Character.MAX_CODE_POINT;
            final char[] rchars = Character.toChars(randomCP);
            final int cpylen = (i < (m_brokenutf16str.length - 1)) ? rchars.length : 1;
            System.arraycopy(rchars, 0, m_brokenutf16str, i, cpylen);
            i += cpylen;
        }
    }

    private Reader getReader(final char[] data, final InvalidXMLCharFilterReader.XMLmode mode) {
        final Reader r = new CharArrayReader(data);
        return new InvalidXMLCharFilterReader(r, mode);
    }

    private void readCharBMP(final char[] testData, final InvalidXMLCharFilterReader.XMLmode mode) throws IOException {
        final Reader r = getReader(testData, mode);
        for (int idx = 0; idx != testData.length; ++idx) {
            final int rch = r.read();
            assertTrue(isValidXmlCP(rch, mode));
            assertTrue((char) idx == rch || rch == REPLACE_CHAR);
        }
        final int rch = r.read();
        assertEquals(-1, rch);
    }

    private void readArrayBMP(final char[] testData, final InvalidXMLCharFilterReader.XMLmode mode) throws IOException {
        for (final int chunkSize : m_test_chunk_sizes) {
            final Reader r = getReader(testData, mode);
            final char[] buf = new char[chunkSize];
            char origchar = 0;
            final int trail_size = testData.length % chunkSize;
            final int num_chunks = testData.length / chunkSize;
            for (int idx = 0; idx <= num_chunks; ++idx) {
                final int expect_read = (idx == num_chunks) ? trail_size : buf.length;
                if (expect_read == 0)
                    break;
                final int rch = r.read(buf, 0, buf.length);
                // as idx < floor(char_len /chunk_len)
                assertEquals(rch, expect_read);
                for (int i = 0; i != expect_read; ++i, ++origchar)
                    assertEquals(isValidXmlCP(origchar, mode) ? origchar : REPLACE_CHAR, buf[i]);
            }
            final int reof = r.read();
            assertEquals(-1, reof);
        }
    }

    private void readCharFullUTF16(final char[] testData, final InvalidXMLCharFilterReader.XMLmode mode)
            throws IOException {
        final Reader r = getReader(testData, mode);
        for (int idx = 0; idx != testData.length; ++idx) {
            final int rch = r.read();
            assertTrue(rch > 0);
            if (Character.isHighSurrogate((char) rch)) {
                final int rchl = r.read();
                assertTrue(rchl > 0);
                assertTrue(Character.isLowSurrogate((char) rchl));
                assertTrue(isValidXmlCP(Character.toCodePoint((char) rch, (char) rchl), mode));
                assertTrue(testData[idx] == rch || (rch == REPLACE_CHAR && rchl == REPLACE_CHAR));
                ++idx;
            } else {
                assertTrue(isValidXmlCP(rch, mode));
                assertTrue(testData[idx] == rch || rch == REPLACE_CHAR);
            }
        }
        final int rch = r.read();
        assertEquals(-1, rch);
    }

    private void readArrayFullUTF16(final char[] testData, final InvalidXMLCharFilterReader.XMLmode mode)
            throws IOException {
        for (final int chunkSize : m_test_chunk_sizes) {
            final Reader r = getReader(testData, InvalidXMLCharFilterReader.XMLmode.XML_1_0);
            final char[] buf = new char[chunkSize];
            int origidx = 0;
            final int trail_size = testData.length % chunkSize;
            final int num_chunks = testData.length / chunkSize;
            char highSurrogate = 0;
            for (int idx = 0; idx <= num_chunks; ++idx) {
                final int expect_read = (idx == num_chunks) ? trail_size : buf.length;
                if (expect_read == 0)
                    break;
                final int rch = r.read(buf, 0, buf.length);
                if (rch != expect_read)
                    // as idx < floor(char_len / chunk_len)
                    assertEquals(rch, expect_read);
                for (int i = 0; i != expect_read; ++i, ++origidx) {
                    if (highSurrogate != 0) {
                        assertTrue(Character.isLowSurrogate(buf[i]));
                        assertTrue(isValidXmlCP(Character.toCodePoint(highSurrogate, buf[i]), mode));
                        assertTrue(m_brokenutf16str[origidx] == buf[i]);
                        highSurrogate = 0;
                    } else {
                        if (Character.isHighSurrogate(buf[i]))
                            highSurrogate = buf[i];
                        else {
                            if (!isValidXmlCP(buf[i], mode))
                                assertTrue(isValidXmlCP(buf[i], mode));
                            assertTrue(m_brokenutf16str[origidx] == buf[i] || buf[i] == REPLACE_CHAR);
                        }
                    }
                }
            }
            final int reof = r.read();
            assertEquals(-1, reof);
        }
    }

    @Test
    public void testReadCharBMP_XML10() throws IOException {
        readCharBMP(m_bmp_rep_twice, InvalidXMLCharFilterReader.XMLmode.XML_1_0);
    }

    @Test
    public void testReadCharBMP_XML11() throws IOException {
        readCharBMP(m_bmp_rep_twice, InvalidXMLCharFilterReader.XMLmode.XML_1_1);
    }

    @Test
    public void testReadArrayBMP_XML10() throws IOException {
        readArrayBMP(m_bmp_rep_twice, InvalidXMLCharFilterReader.XMLmode.XML_1_0);
    }

    @Test
    public void testReadArrayBMP_XML11() throws IOException {
        readArrayBMP(m_bmp_rep_twice, InvalidXMLCharFilterReader.XMLmode.XML_1_1);
    }

    @Test
    public void testReadCharFullUTF16_XML10() throws IOException {
        readCharFullUTF16(m_brokenutf16str, InvalidXMLCharFilterReader.XMLmode.XML_1_0);
    }

    @Test
    public void testReadCharFullUTF16_XML11() throws IOException {
        readCharFullUTF16(m_brokenutf16str, InvalidXMLCharFilterReader.XMLmode.XML_1_1);
    }

    @Test
    public void testReadArrayFullUTF16_XML10() throws IOException {
        readArrayFullUTF16(m_brokenutf16str, InvalidXMLCharFilterReader.XMLmode.XML_1_0);
    }

    @Test
    public void testReadArrayFullUTF16_XML11() throws IOException {
        readArrayFullUTF16(m_brokenutf16str, InvalidXMLCharFilterReader.XMLmode.XML_1_1);
    }
}
