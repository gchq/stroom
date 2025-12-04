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

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2015 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package stroom.util.xml;

import net.sf.saxon.serialize.XMLEmitter;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.CompressedWhitespace;

/**
 * An extension of the Saxon XMLEmitter that prevents forbidden XML characters U+FFFE and U+FFFF.
 */
public class MyXmlEmitter extends XMLEmitter {

    private static final boolean[] IS_SPECIAL_IN_TEXT; // lookup table for special characters in text
    private static final boolean[] IS_SPECIAL_IN_ATT; // lookup table for special characters in attributes
    private static final char INVALID_CHARACTER_1 = '\ufffe'; // U+FFFE: INVALID CHARACTER
    private static final char INVALID_CHARACTER_2 = '\uffff'; // U+FFFF: INVALID CHARACTER

    static {
        // create look-up table for ASCII characters that need special treatment
        IS_SPECIAL_IN_TEXT = new boolean[128];
        for (int i = 0; i <= 31; i++) {
            IS_SPECIAL_IN_TEXT[i] = true;  // allowed in XML 1.1 as character references
        }
        for (int i = 32; i <= 127; i++) {
            IS_SPECIAL_IN_TEXT[i] = false;
        }
        //    note, 0 is used to switch escaping on and off for mapped characters
        IS_SPECIAL_IN_TEXT['\n'] = false;
        IS_SPECIAL_IN_TEXT['\t'] = false;
        IS_SPECIAL_IN_TEXT['\r'] = true;
        IS_SPECIAL_IN_TEXT['<'] = true;
        IS_SPECIAL_IN_TEXT['>'] = true;
        IS_SPECIAL_IN_TEXT['&'] = true;

        IS_SPECIAL_IN_ATT = new boolean[128];
        for (int i = 0; i <= 31; i++) {
            IS_SPECIAL_IN_ATT[i] = true; // allowed in XML 1.1 as character references
        }
        for (int i = 32; i <= 127; i++) {
            IS_SPECIAL_IN_ATT[i] = false;
        }
        IS_SPECIAL_IN_ATT[(char) 0] = true;
        // used to switch escaping on and off for mapped characters
        IS_SPECIAL_IN_ATT['\r'] = true;
        IS_SPECIAL_IN_ATT['\n'] = true;
        IS_SPECIAL_IN_ATT['\t'] = true;
        IS_SPECIAL_IN_ATT['<'] = true;
        IS_SPECIAL_IN_ATT['>'] = true;
        IS_SPECIAL_IN_ATT['&'] = true;
        IS_SPECIAL_IN_ATT['\"'] = true;
    }

    /**
     * Write contents of array to current writer, after escaping special characters.
     * This method converts the XML special characters (such as &lt; and &amp;) into their
     * predefined entities.
     *
     * @param chars       The character sequence containing the string
     * @param inAttribute Set to true if the text is in an attribute value
     */

    protected void writeEscape(final CharSequence chars, final boolean inAttribute)
            throws java.io.IOException, XPathException {
        int segstart = 0;
        boolean disabled = false;
        final boolean[] specialChars = (inAttribute
                ? IS_SPECIAL_IN_ATT
                : IS_SPECIAL_IN_TEXT);

        if (chars instanceof CompressedWhitespace) {
            ((CompressedWhitespace) chars).writeEscape(specialChars, writer);
            return;
        }

        final int clength = chars.length();
        while (segstart < clength) {
            int i = segstart;
            // find a maximal sequence of "ordinary" characters
            while (i < clength) {
                final char c = chars.charAt(i);
                if (c < 127) {
                    if (specialChars[c]) {
                        break;
                    } else {
                        i++;
                    }
                } else if (c < 160) {
                    break;
                } else if (c == 0x2028) {
                    break;
                } else if (UTF16CharacterSet.isHighSurrogate(c)) {
                    break;
                } else if (!characterSet.inCharset(c)) {
                    break;
                } else if (isForbidden(c)) { // surrogates, U+FFFE and U+FFFF are forbidden in XML.
                    break;
                } else {
                    i++;
                }
            }

            // if this was the whole string write it out and exit
            if (i >= clength) {
                if (segstart == 0) {
                    writeCharSequence(chars);
                } else {
                    writeCharSequence(chars.subSequence(segstart, i));
                }
                return;
            }

            // otherwise write out this sequence
            if (i > segstart) {
                writeCharSequence(chars.subSequence(segstart, i));
            }

            // examine the special character that interrupted the scan
            final char c = chars.charAt(i);
            if (c == 0) {
                // used to switch escaping on and off
                disabled = !disabled;
            } else if (disabled) {
                if (c > 127) {
                    if (UTF16CharacterSet.isHighSurrogate(c)) {
                        final int cc = UTF16CharacterSet.combinePair(c, chars.charAt(i + 1));
                        if (!characterSet.inCharset(cc)) {
                            final XPathException de = new XPathException("Character x" + Integer.toHexString(cc) +
                                                                         " is not available in the chosen encoding");
                            de.setErrorCode("SERE0008");
                            throw de;
                        }
                    } else if (!characterSet.inCharset(c)) {
                        final XPathException de = new XPathException("Character " + c + " (x" + Integer.toHexString(c) +
                                                                     ") is not available in the chosen encoding");
                        de.setErrorCode("SERE0008");
                        throw de;
                    }
                }
                writer.write(c);
            } else if (c < 127) {
                // process special ASCII characters
                if (c == '<') {
                    writer.write("&lt;");
                } else if (c == '>') {
                    writer.write("&gt;");
                } else if (c == '&') {
                    writer.write("&amp;");
                } else if (c == '\"') {
                    writer.write("&#34;");
                } else if (c == '\n') {
                    writer.write("&#xA;");
                } else if (c == '\r') {
                    writer.write("&#xD;");
                } else if (c == '\t') {
                    writer.write("&#x9;");
                } else {
                    // C0 control characters
                    characterReferenceGenerator.outputCharacterReference(c, writer);
                }
            } else if (c < 160 || c == 0x2028) {
                // XML 1.1 requires these characters to be written as character references
                characterReferenceGenerator.outputCharacterReference(c, writer);
            } else if (UTF16CharacterSet.isHighSurrogate(c)) {
                final char d = chars.charAt(++i);
                final int charval = UTF16CharacterSet.combinePair(c, d);
                if (characterSet.inCharset(charval)) {
                    writer.write(c);
                    writer.write(d);
                } else {
                    characterReferenceGenerator.outputCharacterReference(charval, writer);
                }
            } else if (!isForbidden(c)) { // surrogates, U+FFFE and U+FFFF are forbidden in XML.
                // process characters not available in the current encoding
                characterReferenceGenerator.outputCharacterReference(c, writer);
            }
            segstart = ++i;
        }
    }

    /**
     * Determines if the supplied characters is forbidden in XML, even as a character reference.
     *
     * @param c The char to test.
     * @return True if the char is forbidden.
     */
    @SuppressWarnings("checkstyle:AvoidEscapedUnicodeCharacters")
    private boolean isForbidden(final char c) {
        // surrogates, U+FFFE and U+FFFF are forbidden in XML.
        return c == INVALID_CHARACTER_1 || c == INVALID_CHARACTER_2;
    }
}

