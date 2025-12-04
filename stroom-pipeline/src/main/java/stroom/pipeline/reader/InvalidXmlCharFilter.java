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

import stroom.util.io.StreamUtil;

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
public class InvalidXmlCharFilter extends TransformReader {

    private final XmlChars xmlChars;
    private final boolean replace;
    private final char replacementChar;

    private final char[] buffer;
    private boolean hasReadAhead;
    private char readAhead;
    private boolean eof;

    public static InvalidXmlCharFilter createRemoveCharsFilter(final Reader in,
                                                               final XmlChars xmlChars) {
        return new InvalidXmlCharFilter(in, xmlChars, false, ' ');
    }

    public static InvalidXmlCharFilter createReplaceCharsFilter(final Reader in,
                                                                final XmlChars xmlChars,
                                                                final boolean replace,
                                                                final char replacementChar) {
        return new InvalidXmlCharFilter(in, xmlChars, replace, replacementChar);
    }

    private InvalidXmlCharFilter(final Reader in,
                                 final XmlChars xmlChars,
                                 final boolean replace,
                                 final char replacementChar) {
        super(in);
        this.xmlChars = xmlChars;
        this.replace = replace;
        this.replacementChar = replacementChar;
        buffer = new char[StreamUtil.BUFFER_SIZE];
    }

    public int read() throws IOException {
        final char[] cb = new char[1];
        if (read(cb, 0, 1) == -1) {
            return -1;
        } else {
            return cb[0];
        }
    }

    @Override
    public int read(final char[] cbuf, int off, int len) throws IOException {
        // Guard against 0 or invalid lengths.
        if (len <= 0) {
            return 0;
        }

        final int originalOff = off;

        // Use read ahead char if we have one.
        if (hasReadAhead) {
            hasReadAhead = false;
            cbuf[off++] = readAhead;

            // Reduce the available length in the target buffer as we have already added a char.
            --len;

            // If we've no more characters requested then just return 1 for the char we added.
            if (len == 0 || eof) {
                return 1;
            }
        } else if (eof) {
            return -1;
        }

        final int maxLen = Math.min(len, buffer.length);
        final int length = in.read(buffer, 0, maxLen);
        eof = length < 0;

        for (int i = 0; i < length; i++) {
            final char ch = buffer[i];

            if (!xmlChars.isValidLiteral(ch)) {
                if (Character.isHighSurrogate(ch)) {
                    boolean validSurrogate = false;

                    // Move the index on one.
                    i++;
                    // See if we can use the buffer to read the next char into.
                    final boolean usingBuffer = i < length;
                    if (usingBuffer) {
                        final char ch2 = buffer[i];
                        if (Character.isLowSurrogate(ch2)) {
                            final int supplemental = Character.toCodePoint(ch, ch2);
                            validSurrogate = xmlChars.isValidLiteral(supplemental);
                        }
                    } else {
                        validSurrogate = validLowSurrogateAhead(ch);
                    }

                    if (!validSurrogate) {
                        if (replace) {
                            cbuf[off++] = replacementChar;
                            if (usingBuffer) {
                                cbuf[off++] = replacementChar;
                            } else {
                                readAhead = replacementChar;
                            }
                        } else {
                            hasReadAhead = false;
                        }

                        modified = true;
                    } else {
                        cbuf[off++] = ch;
                        if (usingBuffer) {
                            cbuf[off++] = buffer[i];
                        }
                    }

                } else {
                    if (replace) {
                        cbuf[off++] = replacementChar;
                    }
                    modified = true;
                }
            } else {
                cbuf[off++] = ch;
            }
        }

        return (originalOff == off)
                ? -1
                : off - originalOff;
    }

    private boolean validLowSurrogateAhead(final char ch) throws IOException {
        if (!eof) {
            final int val2 = in.read();
            if (val2 >= 0) {
                final char ch2 = (char) val2;
                readAhead = ch2;
                hasReadAhead = true;

                if (Character.isLowSurrogate(ch2)) {
                    final int supplemental = Character.toCodePoint(ch, ch2);
                    return xmlChars.isValidLiteral(supplemental);
                }
            }
        }
        return false;
    }
}
