////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2015 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package stroom.entity.util;

import net.sf.saxon.serialize.XMLEmitter;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.CompressedWhitespace;

/**
 * An extension of the Saxon XMLEmitter that prevents forbidden XML characters U+FFFE and U+FFFF.
 */
public class MyXmlEmitter extends XMLEmitter {
    private static boolean[] specialInText;         // lookup table for special characters in text
    private static boolean[] specialInAtt;          // lookup table for special characters in attributes
    // create look-up table for ASCII characters that need special treatment

    static {
        specialInText = new boolean[128];
        for (int i = 0; i <= 31; i++) specialInText[i] = true;  // allowed in XML 1.1 as character references
        for (int i = 32; i <= 127; i++) specialInText[i] = false;
        //    note, 0 is used to switch escaping on and off for mapped characters
        specialInText['\n'] = false;
        specialInText['\t'] = false;
        specialInText['\r'] = true;
        specialInText['<'] = true;
        specialInText['>'] = true;
        specialInText['&'] = true;

        specialInAtt = new boolean[128];
        for (int i = 0; i <= 31; i++) specialInAtt[i] = true; // allowed in XML 1.1 as character references
        for (int i = 32; i <= 127; i++) specialInAtt[i] = false;
        specialInAtt[(char) 0] = true;
        // used to switch escaping on and off for mapped characters
        specialInAtt['\r'] = true;
        specialInAtt['\n'] = true;
        specialInAtt['\t'] = true;
        specialInAtt['<'] = true;
        specialInAtt['>'] = true;
        specialInAtt['&'] = true;
        specialInAtt['\"'] = true;
    }

    /**
     * Write contents of array to current writer, after escaping special characters.
     * This method converts the XML special characters (such as < and &) into their
     * predefined entities.
     *
     * @param chars       The character sequence containing the string
     * @param inAttribute Set to true if the text is in an attribute value
     */

    protected void writeEscape(final CharSequence chars, final boolean inAttribute)
            throws java.io.IOException, XPathException {
        int segstart = 0;
        boolean disabled = false;
        final boolean[] specialChars = (inAttribute ? specialInAtt : specialInText);

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
                        int cc = UTF16CharacterSet.combinePair(c, chars.charAt(i + 1));
                        if (!characterSet.inCharset(cc)) {
                            XPathException de = new XPathException("Character x" + Integer.toHexString(cc) +
                                    " is not available in the chosen encoding");
                            de.setErrorCode("SERE0008");
                            throw de;
                        }
                    } else if (!characterSet.inCharset(c)) {
                        XPathException de = new XPathException("Character " + c + " (x" + Integer.toHexString((int) c) +
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
                char d = chars.charAt(++i);
                int charval = UTF16CharacterSet.combinePair(c, d);
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
    private boolean isForbidden(final char c) {
        // surrogates, U+FFFE and U+FFFF are forbidden in XML.
        return c == '\ufffe' || c == '\uffff';
    }
}

