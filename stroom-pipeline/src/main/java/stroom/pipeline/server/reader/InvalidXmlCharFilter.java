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

    public InvalidXmlCharFilter(final Reader in,
                                final XmlChars xmlChars,
                                final boolean replace,
                                final char replacementChar) {
        super(in);
        this.xmlChars = xmlChars;
        this.replace = replace;
        this.replacementChar = replacementChar;
        buffer = new char[StreamUtil.BUFFER_SIZE];
    }

//    @Override
//    public int read() throws IOException {
//        if (hasReadAhead) {
//            hasReadAhead = false;
//            return readAhead;
//        }
//
//        boolean ok = false;
//        int val = -1;
//        while (!ok) {
//            val = in.read();
//            if (val < 0) {
//                return val;
//            }
//
//            final char ch = (char) val;
//            if (!xmlChars.isValid(ch)) {
//                if (Character.isHighSurrogate(ch)) {
//
//                    final int val2 = in.read();
//                    if (val2 < 0) {
//                        modified = true;
//                        return val2;
//                    }
//
//                    final char ch2 = (char) val2;
//                    if (Character.isLowSurrogate(ch2)) {
//                        final int supplemental = Character.toCodePoint(ch, ch2);
//                        if (xmlChars.isValid(supplemental)) {
//                            readAhead = ch2;
//                            hasReadAhead = true;
//                            ok = true;
//                        }
//                    }
//                }
//
//                if (!ok) {
//                    modified = true;
//                }
//
//            } else {
//                ok = true;
//            }
//        }
//
//        return val;
//    }
//
//    @Override
//    public int read(final char[] cbuf, int off, int len) throws IOException {
//        final int originalOff = off;
//        if (hasReadAhead && len - off > 0) {
//            hasReadAhead = false;
//            cbuf[off++] = readAhead;
//            --len;
//        }
//
//        if (len > 0) {
//            final int length = in.read(cbuf, off, len);
//            if (length >= 0) {
//                final int endOffset = off + length;
//                while (off < endOffset) {
//                    final char ch = cbuf[off];
//                    if (!xmlChars.isValid(ch)) {
//                        if (Character.isHighSurrogate(ch)) {
//                            final int nextOff = off + 1;
//                            final boolean usingBuffer = nextOff < endOffset;
//
//                            boolean validSurrogate = false;
//
//                            if (usingBuffer) {
//                                final char ch2 = cbuf[nextOff];
//                                if (Character.isLowSurrogate(ch2)) {
//                                    final int supplemental = Character.toCodePoint(ch, ch2);
//                                    validSurrogate = xmlChars.isValid(supplemental);
//                                }
//                            } else {
//                                validSurrogate = validLowSurrogateAhead(ch);
//                            }
//
//                            if (!validSurrogate) {
//                                if (usingBuffer) {
//                                    cbuf[off++] = REPLACEMENT_CHAR;
//                                    cbuf[off] = REPLACEMENT_CHAR;
//                                } else {
//                                    cbuf[off] = REPLACEMENT_CHAR;
//                                    readAhead = REPLACEMENT_CHAR;
//                                }
//                                modified = true;
//                            }
//
//                        } else {
//                            cbuf[off] = REPLACEMENT_CHAR;
//                            modified = true;
//                        }
//                    }
//                    off++;
//                }
//            }
//        }
//        return (originalOff == off) ? -1 : off - originalOff;
//    }






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
            if (len == 0) {
                return 1;
            }
        }

        final char[] inBuffer = buffer;

        final int maxLen = Math.min(len, inBuffer.length);
        final int length = in.read(inBuffer, 0, maxLen);
        if (length >= 0) {
            for (int i = 0; i < length; i++) {
                final char ch = inBuffer[i];

                if (!xmlChars.isValid(ch)) {
                    if (Character.isHighSurrogate(ch)) {
                        final int nextOff = i + 1;
                        final boolean usingBuffer = nextOff < length;

                        boolean validSurrogate = false;

                        if (usingBuffer) {
                            final char ch2 = inBuffer[nextOff];
                            if (Character.isLowSurrogate(ch2)) {
                                final int supplemental = Character.toCodePoint(ch, ch2);
                                validSurrogate = xmlChars.isValid(supplemental);
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
