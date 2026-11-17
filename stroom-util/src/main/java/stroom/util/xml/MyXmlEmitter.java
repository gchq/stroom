/*
 * Copyright 2019 Crown Copyright
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

/// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2015 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
/// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package stroom.util.xml;

import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.serialize.XMLEmitter;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.CompressedWhitespace;

/// An extension of the Saxon XMLEmitter that prevents forbidden XML characters U+0000, U+FFFE and U+FFFF.
///
/// Saxon uses U+0000 as an in-band marker to switch escaping off and on around text that an
/// `xsl:character-map` has substituted, and flags such text with [ReceiverOptions#USE_NULL_MARKERS].
/// U+0000 is only treated as a marker in text flagged that way. Anywhere else it came from the data, which XML
/// cannot hold in any form, so it is dropped.
public class MyXmlEmitter extends XMLEmitter {

    private static final char NUL = '\u0000';

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
        //    note, 0 is used to switch escaping on and off for mapped characters, see writeEscape
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
        // 0 is used to switch escaping on and off for mapped characters, see writeEscape
        IS_SPECIAL_IN_ATT['\r'] = true;
        IS_SPECIAL_IN_ATT['\n'] = true;
        IS_SPECIAL_IN_ATT['\t'] = true;
        IS_SPECIAL_IN_ATT['<'] = true;
        IS_SPECIAL_IN_ATT['>'] = true;
        IS_SPECIAL_IN_ATT['&'] = true;
        IS_SPECIAL_IN_ATT['\"'] = true;
    }

    /**
     * Use this flag to determine if we are going to let the control character 0 toggle escape switching.
     */
    private final boolean preventEscapeSwitching;

    // True while writing text or an attribute value in which U+0000 is a marker that switches escaping off and on.
    private boolean nullMarkersActive;

    public MyXmlEmitter(final boolean preventEscapeSwitching) {
        this.preventEscapeSwitching = preventEscapeSwitching;
    }

    /// Removes every U+0000 from a character sequence.
    ///
    /// @param chars The characters to clean, may be null.
    /// @return The same sequence if it holds no U+0000, otherwise a copy without them.
    static CharSequence removeNul(final CharSequence chars) {
        if (chars == null || chars instanceof CompressedWhitespace) {
            return chars;
        }
        final int length = chars.length();
        int firstNul = -1;
        for (int i = 0; i < length && firstNul == -1; i++) {
            if (chars.charAt(i) == NUL) {
                firstNul = i;
            }
        }
        if (firstNul == -1) {
            return chars;
        }
        final StringBuilder sb = new StringBuilder(length - 1);
        sb.append(chars, 0, firstNul);
        for (int i = firstNul + 1; i < length; i++) {
            final char c = chars.charAt(i);
            if (c != NUL) {
                sb.append(c);
            }
        }
        return sb;
    }

    /// Writes character data. Any U+0000 in the data is dropped unless Saxon has flagged the text as holding
    /// character map markers and escape switching is allowed.
    ///
    /// @param chars      The character data.
    /// @param locationId The location of the data.
    /// @param properties The Saxon receiver options for the data.
    /// @throws XPathException If the data cannot be written.
    @Override
    public void characters(final CharSequence chars,
                           final Location locationId,
                           final int properties) throws XPathException {
        final boolean markersActive = isNullMarkers(properties);
        final CharSequence cleaned = markersActive
                ? chars
                : removeNul(chars);
        nullMarkersActive = markersActive;
        try {
            super.characters(cleaned, locationId, properties);
        } finally {
            nullMarkersActive = false;
        }
    }

    /// Writes an attribute. Any U+0000 in the value is dropped unless Saxon has flagged the value as holding
    /// character map markers and escape switching is allowed.
    ///
    /// @param elCode     The element name.
    /// @param attname    The attribute name.
    /// @param value      The attribute value.
    /// @param properties The Saxon receiver options for the attribute.
    /// @throws XPathException If the attribute cannot be written.
    @Override
    protected void writeAttribute(final NodeName elCode,
                                  final String attname,
                                  final CharSequence value,
                                  final int properties) throws XPathException {
        final boolean markersActive = isNullMarkers(properties);
        final CharSequence cleaned = markersActive
                ? value
                : removeNul(value);
        nullMarkersActive = markersActive;
        try {
            super.writeAttribute(elCode, attname, cleaned, properties);
        } finally {
            nullMarkersActive = false;
        }
    }

    /// Writes a comment, dropping any U+0000 as XML cannot hold it.
    ///
    /// @param chars      The comment text.
    /// @param locationId The location of the comment.
    /// @param properties The Saxon receiver options for the comment.
    /// @throws XPathException If the comment cannot be written.
    @Override
    public void comment(final CharSequence chars,
                        final Location locationId,
                        final int properties) throws XPathException {
        super.comment(removeNul(chars), locationId, properties);
    }

    /// Writes a processing instruction, dropping any U+0000 from its data as XML cannot hold it.
    ///
    /// @param target     The processing instruction target.
    /// @param data       The processing instruction data.
    /// @param locationId The location of the processing instruction.
    /// @param properties The Saxon receiver options for the processing instruction.
    /// @throws XPathException If the processing instruction cannot be written.
    @Override
    public void processingInstruction(final String target,
                                      final CharSequence data,
                                      final Location locationId,
                                      final int properties) throws XPathException {
        super.processingInstruction(target, removeNul(data), locationId, properties);
    }

    private boolean isNullMarkers(final int properties) {
        return !preventEscapeSwitching && (properties & ReceiverOptions.USE_NULL_MARKERS) != 0;
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
            if (c == NUL) {
                // U+0000 is never written. It switches escaping on and off only when Saxon has flagged
                // the text as holding character map markers, otherwise it came from the data.
                if (nullMarkersActive) {
                    disabled = !disabled;
                }
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

