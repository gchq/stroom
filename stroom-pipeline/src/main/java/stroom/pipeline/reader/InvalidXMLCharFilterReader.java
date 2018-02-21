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

package stroom.pipeline.server.reader;

import java.io.IOException;
import java.io.Reader;

/**
 * An implementation of TransformReader to replace invalid XML (1.0 or 1.1)
 * characters with the replacement character. The purpose of this class is to
 * modify character streams (in UTF-16) such as to avoid fatal parse errors
 * arising in the XML parser.
 * <p>
 * The valid XML character ranges are taken from the XML standards documents
 * available from:
 * <p>
 * http://www.w3.org/TR/xml http://www.w3.org/TR/xml11
 * <p>
 * SH
 */
public class InvalidXMLCharFilterReader extends TransformReader {
    private final char REPLACEMENT_CHAR = 0xfffd; // The <?> symbol.
    final private XMLmode m_xmlmode;
    private boolean m_haveReadAhead;
    private char m_readAhead;

    public InvalidXMLCharFilterReader(final Reader in, final XMLmode mode) {
        super(in);
        m_xmlmode = mode;
    }

    /**
     * Check that surrogate character pair are valid and that these represent a
     * valid XML character code point.
     */
    private static boolean isValidCharSurrogatePair(final char chA, final char chB) {
        if (!Character.isLowSurrogate(chB))
            return false;
        final int cp = Character.toCodePoint(chA, chB);
        assert (cp >= 0x10000); // Consequence of implementation of toCodePoint
        return cp < 0x10ffff;
    }

    /**
     * Read a single UTF-16 char value.
     */
    @Override
    public int read() throws IOException {
        if (m_haveReadAhead) {
            m_haveReadAhead = false;
            return m_readAhead;
        }
        int val = in.read();
        if (val >= 0) {
            final char ch = (char) val;
            assert (ch == val);
            if (Character.isHighSurrogate(ch)) {
                final int readAhead = in.read();
                if (readAhead >= 0) {
                    m_haveReadAhead = true;
                    m_readAhead = (char) readAhead;
                    assert (m_readAhead == readAhead);
                    if (isValidCharSurrogatePair(ch, m_readAhead))
                        return val;
                    m_readAhead = REPLACEMENT_CHAR;
                }
            } else if (isValidCharBMP(ch))
                return val;
            val = REPLACEMENT_CHAR;
            m_streamModified = true;
        }
        return val;
    }

    /**
     * Read UTF-16 char values into an array.
     */
    @Override
    public int read(final char cbuf[], int off, int len) throws IOException {
        final int originalOff = off;
        if (m_haveReadAhead && len - off > 0) {
            m_haveReadAhead = false;
            cbuf[off++] = m_readAhead;
            --len;
        }
        if (len > 0) {
            final int nread = in.read(cbuf, off, len);
            if (nread >= 0) {
                final int endOffset = off + nread;
                while (off < endOffset) {
                    if (Character.isHighSurrogate(cbuf[off])) {
                        final int lowSurrogateIdx = off + 1;
                        if (lowSurrogateIdx < endOffset) {
                            if (!isValidCharSurrogatePair(cbuf[off], cbuf[lowSurrogateIdx])) {
                                cbuf[off] = cbuf[lowSurrogateIdx] = REPLACEMENT_CHAR;
                                m_streamModified = true;
                            }
                        } else {
                            final int readAhead = in.read();
                            if (readAhead >= 0) {
                                m_haveReadAhead = true;
                                m_readAhead = (char) readAhead;
                                assert (m_readAhead == readAhead);
                                if (isValidCharSurrogatePair(cbuf[off], m_readAhead))
                                    break;
                                else {
                                    cbuf[off] = m_readAhead = REPLACEMENT_CHAR;
                                    m_streamModified = true;
                                }
                            } else
                                cbuf[off] = REPLACEMENT_CHAR;

                        }
                        off += 2;
                    } else {
                        if (!isValidCharBMP(cbuf[off])) {
                            cbuf[off] = REPLACEMENT_CHAR;
                            m_streamModified = true;
                        }
                        ++off;
                    }
                }
                // Count of new characters + any read-ahead character.
                return endOffset - originalOff;
            }
            // An error reading from underlying character stream.
            assert (nread == -1);
        }
        return (originalOff == off) ? -1 : 1;
    }

    /**
     * Check character is valid in the Basic Multilingual Page. Precondition:
     * chr is not a surrogate character.
     */
    private boolean isValidCharBMP(final char chr) {
        if (chr >= 0x20) {
            if (chr <= 0xd7ff || chr >= 0xe000 && chr <= 0xfffd)
                return true;
        } else
            switch (m_xmlmode) {
                case XML_1_0:
                    return chr == 0xa || chr == 0xd || chr == 0x9;
                case XML_1_1:
                    return chr > 0;
                default:
                    assert (false);
            }
        return false;
    }
    public enum XMLmode {
        XML_1_0, XML_1_1
    }

}
