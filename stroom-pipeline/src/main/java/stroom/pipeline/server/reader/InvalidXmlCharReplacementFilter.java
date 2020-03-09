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
public class InvalidXmlCharReplacementFilter extends TransformReader {
    private final char REPLACEMENT_CHAR = 0xfffd; // The <?> symbol.

    private final XmlChars xmlChars;

    private boolean hasReadAhead;
    private char readAhead;

    public InvalidXmlCharReplacementFilter(final Reader in, final XmlChars xmlChars) {
        super(in);
        this.xmlChars = xmlChars;
    }

    @Override
    public int read() throws IOException {
        if (hasReadAhead) {
            hasReadAhead = false;
            return readAhead;
        }

        int val = in.read();
        if (val < 0) {
            return val;
        }

        final char ch = (char) val;
        if (!xmlChars.isValid(ch)) {
            if (Character.isHighSurrogate(ch)) {
                final boolean validSurrogate = validLowSurrogateAhead(ch);
                if (!validSurrogate) {
                    readAhead = REPLACEMENT_CHAR;
                    val = REPLACEMENT_CHAR;
                    modified = true;
                }
            } else {
                val = REPLACEMENT_CHAR;
                modified = true;
            }
        }

        return val;
    }

    @Override
    public int read(final char[] cbuf, int off, int len) throws IOException {
        final int originalOff = off;
        if (hasReadAhead && len - off > 0) {
            hasReadAhead = false;
            cbuf[off++] = readAhead;
            --len;
        }

        if (len > 0) {
            final int length = in.read(cbuf, off, len);
            if (length >= 0) {
                final int endOffset = off + length;
                while (off < endOffset) {
                    final char ch = cbuf[off];
                    if (!xmlChars.isValid(ch)) {
                        if (Character.isHighSurrogate(ch)) {
                            final int nextOff = off + 1;
                            final boolean usingBuffer = nextOff < endOffset;

                            boolean validSurrogate = false;

                            if (usingBuffer) {
                                final char ch2 = cbuf[nextOff];
                                if (Character.isLowSurrogate(ch2)) {
                                    final int supplemental = Character.toCodePoint(ch, ch2);
                                    validSurrogate = xmlChars.isValid(supplemental);
                                }
                            } else {
                                validSurrogate = validLowSurrogateAhead(ch);
                            }

                            if (!validSurrogate) {
                                if (usingBuffer) {
                                    cbuf[off++] = REPLACEMENT_CHAR;
                                    cbuf[off] = REPLACEMENT_CHAR;
                                } else {
                                    cbuf[off] = REPLACEMENT_CHAR;
                                    readAhead = REPLACEMENT_CHAR;
                                }
                                modified = true;
                            }

                        } else {
                            cbuf[off] = REPLACEMENT_CHAR;
                            modified = true;
                        }
                    }
                    off++;
                }
            }
        }
        return (originalOff == off) ? -1 : off - originalOff;
    }

    private boolean validLowSurrogateAhead(final char ch) throws IOException {
        final int val2 = in.read();
        if (val2 >= 0) {
            final char ch2 = (char) val2;
            readAhead = ch2;
            hasReadAhead = true;

            if (Character.isLowSurrogate(ch2)) {
                final int supplemental = Character.toCodePoint(ch, ch2);
                return xmlChars.isValid(supplemental);
            }
        }
        return false;
    }
}
