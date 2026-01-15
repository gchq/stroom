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

package stroom.pipeline.filter;

import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.filter.SafeXMLFilter.SafeBuffer;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.svg.shared.SvgImage;
import stroom.util.CharBuffer;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

@ConfigurableElement(
        type = "SafeXMLFilter",
        displayValue = "Safe XML Filter",
        category = Category.FILTER,
        description = "Restricts the characters to a very simple set consisting of `[a-zA-Z0-9]` and `["
                      + SafeBuffer.OTHER_CHARS
                      + "]`. All other characters are replaced by `~NNN`, where `NNN` is a three " +
                      "digit codepoint for the replaced character.",
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.VISABILITY_STEPPING,
                PipelineElementType.ROLE_MUTATOR},
        icon = SvgImage.PIPELINE_RECORD_OUTPUT)
public class SafeXMLFilter extends AbstractXMLFilter {

    private final SafeBuffer safeBuffer = new SafeBuffer(500);
    private final SafeAttributes safeAttributes = new SafeAttributes(safeBuffer);

    @Override
    public void startElement(final String uri,
                             final String localName,
                             final String name,
                             final Attributes atts) throws SAXException {
        outputChars();
        safeAttributes.setAtts(atts);
        super.startElement(uri, localName, name, safeAttributes);
    }

    @Override
    public void endElement(final String uri, final String localName, final String name) throws SAXException {
        outputChars();
        super.endElement(uri, localName, name);
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        safeBuffer.append(ch, start, length);
    }

    private void outputChars() throws SAXException {
        safeBuffer.output(getContentHandler());
    }


    // --------------------------------------------------------------------------------


    static class SafeBuffer extends CharBuffer {

        private static final char ZERO = '0';
        private static final int MAX_DIGITS = 3;
        private static final char ENCODE_CHAR = '~';
        private static final String DIGIT_CHARS = "0123456789";
        private static final String LOWERCASE_CHARS = "abcdefghijklmnopqrstuvwxyz";
        private static final String UPPERCASE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        static final String OTHER_CHARS = " .:-_/";
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

        SafeBuffer(final int initialSize) {
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
            char c;
            int codePoint;

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
            for (final int codePoint1 : codePoints) {
                if (codePoint == codePoint1) {
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


    // --------------------------------------------------------------------------------


    private static class OutputBuffer extends CharBuffer {

        OutputBuffer(final int initialSize) {
            super(initialSize);
        }

        public void output(final ContentHandler handler) throws SAXException {
            handler.characters(buffer, start, end - start);
        }
    }


    // --------------------------------------------------------------------------------


    private static class SafeAttributes extends AttributesImpl {

        private final SafeBuffer safeBuffer;

        SafeAttributes(final SafeBuffer safeBuffer) {
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
