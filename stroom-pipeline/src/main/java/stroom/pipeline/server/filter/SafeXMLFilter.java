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

package stroom.pipeline.server.filter;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;

import stroom.util.CharBuffer;

public class SafeXMLFilter extends XMLFilterImpl {
    private SafeBuffer safeBuffer = new SafeBuffer(500);
    private SafeAttributes safeAttributes = new SafeAttributes(safeBuffer);

    @Override
    public void startElement(String uri, String localName, String name, Attributes atts) throws SAXException {
        outputChars();
        safeAttributes.setAtts(atts);
        super.startElement(uri, localName, name, safeAttributes);
    }

    @Override
    public void endElement(String uri, String localName, String name) throws SAXException {
        outputChars();
        super.endElement(uri, localName, name);
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        safeBuffer.append(ch, start, length);
    }

    private void outputChars() throws SAXException {
        safeBuffer.output(getContentHandler());
    }

    private static class SafeBuffer extends CharBuffer {
        private static final long serialVersionUID = 7457435229363903797L;

        private static final char ZERO = '0';
        private static final int MAX_DIGITS = 3;
        private static final char ENCODE_CHAR = '~';
        private static final String DIGIT_CHARS = "0123456789";
        private static final String LOWERCASE_CHARS = "abcdefghijklmnopqrstuvwxyz";
        private static final String UPPERCASE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        private static final String OTHER_CHARS = " .:-_/";
        private static final int[] DIGIT_CODEPOINTS;
        private static final int[] LOWERCASE_CODEPOINTS;
        private static final int[] UPPERCASE_CODEPOINTS;
        private static final int[] OTHER_CODEPOINTS;

        private static final int MAX_SAFE_LENGTH = 500;
        private static final String TRUNC_MARKER = "...";

        static {
            char[] chars = DIGIT_CHARS.toCharArray();
            DIGIT_CODEPOINTS = new int[chars.length];
            for (int i = 0; i < chars.length; i++) {
                DIGIT_CODEPOINTS[i] = chars[i];
            }
            chars = LOWERCASE_CHARS.toCharArray();
            LOWERCASE_CODEPOINTS = new int[chars.length];
            for (int i = 0; i < chars.length; i++) {
                LOWERCASE_CODEPOINTS[i] = chars[i];
            }
            chars = UPPERCASE_CHARS.toCharArray();
            UPPERCASE_CODEPOINTS = new int[chars.length];
            for (int i = 0; i < chars.length; i++) {
                UPPERCASE_CODEPOINTS[i] = chars[i];
            }
            chars = OTHER_CHARS.toCharArray();
            OTHER_CODEPOINTS = new int[chars.length];
            for (int i = 0; i < chars.length; i++) {
                OTHER_CODEPOINTS[i] = chars[i];
            }
        }

        private final OutputBuffer outputBuffer;

        public SafeBuffer(final int initialSize) {
            super(initialSize);
            outputBuffer = new OutputBuffer(initialSize);
        }

        private void output(final ContentHandler handler) throws SAXException {
            if (length() > 0 && handler != null) {
                // Trim the current buffer.
                trimWhitespace();
                // Move the characters to the output buffer and make them safe.
                move(buffer, start, end);
                // Clear the current buffer.
                clear();
                // Output the output buffer.
                outputBuffer.output(handler);
            }
        }

        private String makeSafe(final String string) {
            if (string.length() == 0) {
                return string;
            }

            final char[] chars = string.toCharArray();
            // Move the characters to the output buffer and make them safe.
            move(chars, 0, chars.length);

            return outputBuffer.toString();
        }

        /**
         * Move characters from a buffer to the safe buffer making them safe in
         * the process.
         */
        private void move(final char[] buffer, final int start, final int end) {
            char c = 0;
            int codePoint = 0;

            outputBuffer.clear();
            for (int i = start, j = 0; i < end; i++) {
                c = buffer[i];
                codePoint = c;

                if (inRange(codePoint, DIGIT_CODEPOINTS) || inRange(codePoint, LOWERCASE_CODEPOINTS)
                        || inRange(codePoint, UPPERCASE_CODEPOINTS) || inValues(codePoint, OTHER_CODEPOINTS)) {
                    outputBuffer.append(c);
                    j++;
                } else {
                    j = encode(j, codePoint, outputBuffer);
                }

                if (j >= MAX_SAFE_LENGTH) {
                    // Add a truncation marker so we know that the original
                    // string has been truncated.
                    outputBuffer.append(TRUNC_MARKER);

                    // Get out of this loop.
                    i = end;
                }
            }
        }

        private boolean inRange(final int codePoint, final int[] codePoints) {
            return codePoint >= codePoints[0] && codePoint <= codePoints[codePoints.length - 1];
        }

        private boolean inValues(final int codePoint, final int[] codePoints) {
            for (int i = 0; i < codePoints.length; i++) {
                if (codePoint == codePoints[i]) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Encode a code point and add it to the safe buffer.
         */
        private int encode(final int pos, final int codePoint, final CharBuffer buffer) {
            final String digits = String.valueOf(codePoint);
            // We will ignore any numbers with more than 3 digits.
            if (digits.length() <= 3) {
                // If encoding this character is going to make the buffer bigger
                // than the max length then we don't want to add it. Just return
                // the max length so we can truncate the value.
                if (pos > MAX_SAFE_LENGTH - 4) {
                    return MAX_SAFE_LENGTH;
                }

                // Output the encode character.
                buffer.append(ENCODE_CHAR);
                // Pad the number to ensure we have 3 chars.
                buffer.pad(MAX_DIGITS - digits.length(), ZERO);
                // Add the digits on.
                buffer.append(digits);

                // We added 4 chars so return the position plus 4.
                return pos + 4;
            }

            // We didn't encode the character so just return the original
            // position.
            return pos;
        }
    }

    private static class OutputBuffer extends CharBuffer {
        private static final long serialVersionUID = 5324120855707144530L;

        public OutputBuffer(final int initialSize) {
            super(initialSize);
        }

        public void output(final ContentHandler handler) throws SAXException {
            handler.characters(buffer, start, end - start);
        }
    }

    private class SafeAttributes extends AttributesImpl {
        private final SafeBuffer safeBuffer;

        public SafeAttributes(final SafeBuffer safeBuffer) {
            this.safeBuffer = safeBuffer;
        }

        public void setAtts(final Attributes atts) {
            clear();

            for (int i = 0; i < atts.getLength(); i++) {
                final String value = atts.getValue(i);
                final String safeValue = safeBuffer.makeSafe(value);
                addAttribute(atts.getURI(i), atts.getLocalName(i), atts.getQName(i), atts.getType(i), safeValue);
                safeBuffer.clear();
            }
        }
    }
}
