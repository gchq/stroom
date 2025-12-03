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

package stroom.pipeline.xml.util;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XMLWriter implements ContentHandler {

    public static final char SURROGATE1_MIN = 0xD800;
    public static final char SURROGATE1_MAX = 0xDBFF;
    // Lookup table for special characters in text
    private static final boolean[] specialInText;
    // Lookup table for special characters in attributes
    private static final boolean[] specialInAtt;
    private static final XMLVersion DEFAULT_VERSION = XMLVersion.VERSION_1_1;
    private static final int DEFAULT_INDENTATION = 2;

    // Create look-up table for ASCII characters that need special treatment
    static {
        specialInText = new boolean[128];
        for (int i = 0; i <= 31; i++) {
            // Allowed in XML 1.1 as character references
            specialInText[i] = true;
        }
        for (int i = 32; i <= 127; i++) {
            specialInText[i] = false;
        }
        // note, 0 is used to switch escaping on and off for mapped characters
        specialInText['\n'] = false;
        specialInText['\t'] = false;
        specialInText['\r'] = true;
        specialInText['<'] = true;
        specialInText['>'] = true;
        specialInText['&'] = true;

        specialInAtt = new boolean[128];
        for (int i = 0; i <= 31; i++) {
            // Allowed in XML 1.1 as character references
            specialInAtt[i] = true;
        }
        for (int i = 32; i <= 127; i++) {
            specialInAtt[i] = false;
        }

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

    private final XMLVersion version = DEFAULT_VERSION;
    private final List<String> attList = new ArrayList<>();
    private final Map<String, String> prefixMap = new HashMap<>();
    private final List<String> unwrittenPrefixes = new ArrayList<>();
    private final Writer writer;
    private final StringBuilder buf = new StringBuilder();
    private final StringWriter stringWriter = new StringWriter();
    private final AttributeNameComparator attributeNameComparator = new AttributeNameComparator();
    private char[] indentChars = {'\n', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '};
    // Output Options
    private boolean sortAtts = true;
    private boolean outputXMLDecl = false;
    private int indentation = DEFAULT_INDENTATION;
    private boolean normalizeSpace = false;
    private boolean inStart;
    private int level;
    private int attIndent = 1;
    private boolean sameline;
    private boolean afterStartTag = false;
    private boolean afterEndTag = false;
    private boolean allWhite = true;
    // Line and column measure the number of lines and columns
    private int line = 0;
    private int column = 0; // .. in whitespace text nodes between tags

    public XMLWriter(final Writer writer) {
        this.writer = writer;
    }

    @Override
    public void setDocumentLocator(final Locator locator) {
    }

    @Override
    public void startDocument() throws SAXException {
        try {
            if (outputXMLDecl) {
                writer.write("<?xml version=\"" + version.getOutput() + "\" encoding=\"UTF-8\"?>");
                afterEndTag = true;
            }
        } catch (final IOException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void endDocument() throws SAXException {
        try {
            if (afterEndTag) {
                writer.write('\n');
            }
        } catch (final IOException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        prefixMap.put(prefix, uri);
        unwrittenPrefixes.add(prefix);
    }

    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        prefixMap.remove(prefix);
        unwrittenPrefixes.remove(prefix);
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        try {
            if (inStart) {
                inStart = false;
                writer.write('>');
            }

            writeCharacters(false);

            if (afterStartTag || afterEndTag) {
                indent();
            }

            writer.write('<');
            writer.write(qName);

            // Output NS prefixes.
            if (unwrittenPrefixes.size() > 0) {
                // Create an indent string for attributes.

                // Figure out how many whitespace chars to insert before the
                // next attribute.
                final int spaces = (indentation * level) + qName.length() + 3;
                // Make sure the indent chars array is big enough to supply
                // enough whitespace at this level.
                growIndentChars(spaces + 2);
                final int nextAttIndent = spaces;

                Collections.sort(unwrittenPrefixes);
                for (final String prefix : unwrittenPrefixes) {
                    final String prefixUri = prefixMap.get(prefix);
                    buf.append("xmlns");
                    if (prefix.length() > 0) {
                        buf.append(":");
                        buf.append(prefix);
                    }
                    final String attName = getChars();
                    writeAttribute(writer, attName, prefixUri);

                    attIndent = nextAttIndent;
                }
                unwrittenPrefixes.clear();
            }

            writeAtts(writer, atts);
            attIndent = 1;

            inStart = true;
            level++;
            sameline = true;
            afterStartTag = true;
            afterEndTag = false;
            allWhite = true;
        } catch (final IOException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        try {
            level--;

            writeCharacters(afterStartTag);

            if (inStart) {
                inStart = false;
                writer.write("/>");

            } else {
                if (afterEndTag && !sameline) {
                    indent();
                }

                writer.write("</");
                writer.write(qName);
                writer.write(">");
            }

            sameline = false;
            afterEndTag = true;
            afterStartTag = false;
            allWhite = true;
            line = 0;
        } catch (final IOException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void characters(final char[] chars, final int start, final int length) throws SAXException {
        buf.append(chars, start, length);
    }

    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
    }

    @Override
    public void processingInstruction(final String target, final String data) throws SAXException {
    }

    @Override
    public void skippedEntity(final String name) throws SAXException {
    }

    private void writeCharacters(final boolean neverNormalize) throws SAXException {
        try {
            // Escape these characters so that they are XML compliant.
            String string = getEscapedChars();
            if (normalizeSpace && !neverNormalize) {
                string = string.trim();
            }

            if (string.length() > 0) {
                // Find out how much whitespace is contained in the escaped
                // string.
                for (int i = 0; i < string.length(); i++) {
                    final char c = string.charAt(i);
                    if (c == '\n') {
                        sameline = false;
                        line++;
                        column = 0;
                    }
                    if (!Character.isWhitespace(c)) {
                        allWhite = false;
                    }
                    column++;
                }

                // If we are still inside the start element then write the
                // closing bracket.
                if (inStart) {
                    inStart = false;
                    writer.write('>');
                }

                // Output the escaped characters.
                writer.write(string);

                // If we write something other than whitespace then set the
                // flags to indicate that we haven't just written a start or end
                // element.
                if (!allWhite) {
                    afterStartTag = false;
                    afterEndTag = false;
                }
            }
        } catch (final IOException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    private void writeAtts(final Writer writer, final Attributes atts) throws SAXException {
        if (sortAtts) {
            for (int i = 0; i < atts.getLength(); i++) {
                attList.add(atts.getQName(i));
            }
            Collections.sort(attList, attributeNameComparator);
            for (final String qName : attList) {
                writeAttribute(writer, qName, atts.getValue(qName));
            }
            attList.clear();
        } else {
            for (int i = 0; i < atts.getLength(); i++) {
                writeAttribute(writer, atts.getQName(i), atts.getValue(i));
            }
        }
    }

    private void writeAttribute(final Writer writer, final String name, final String value) throws SAXException {
        try {
            if (attIndent > 1) {
                writer.write(indentChars, 0, attIndent);
            } else {
                writer.write(' ');
            }
            writer.write(name);
            writer.write("=\"");
            writeEscape(writer, value, true);
            writer.write('\"');
        } catch (final IOException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    private String getEscapedChars() throws SAXException {
        try {
            String chars = buf.toString();
            buf.setLength(0);
            writeEscape(stringWriter, chars, false);
            stringWriter.flush();
            chars = stringWriter.toString();
            stringWriter.getBuffer().setLength(0);
            return chars;
        } catch (final IOException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    private String getChars() throws SAXException {
        final String chars = buf.toString();
        buf.setLength(0);
        return chars;
    }

    private void writeEscape(final Writer writer, final String chars, final boolean inAttribute)
            throws java.io.IOException, SAXException {
        int segstart = 0;
        final boolean[] specialChars = (inAttribute
                ? specialInAtt
                : specialInText);

        final int clength = chars.length();
        while (segstart < clength) {
            int i = segstart;
            // Find a maximal sequence of "ordinary" characters
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
                } else if (isHighSurrogate(c)) {
                    break;
                } else if (c >= 0xfffe) {
                    break;
                } else {
                    i++;
                }
            }

            // If this was the whole string write it out and exit
            if (i >= clength) {
                if (segstart == 0) {
                    writer.write(chars);
                } else {
                    writer.write(chars.substring(segstart, i));
                }
                return;
            }

            // Otherwise write out this sequence
            if (i > segstart) {
                writer.write(chars.substring(segstart, i));
            }

            // Examine the special character that interrupted the scan
            final char c = chars.charAt(i);
            if (c == 0) {
                // Ignore character.
            } else if (c < 127) {
                // Process special ASCII characters
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
                } else if (c <= 31) {
                    // 0x1 = 1
                    // 0x1F = 31

                    // Invalid char in XML 1.0 so ignore unless we are writing
                    // XML 1.1.
                    if (XMLVersion.VERSION_1_1.equals(version)) {
                        outputCharacterReference(writer, c);
                    }
                } else {
                    // C0 control characters
                    outputCharacterReference(writer, c);
                }
            } else if (c < 160 || c == 0x2028) {
                // 0x7F = 127
                // 0x9F = 159

                // XML 1.0 and 1.1 requires these characters to be written as
                // character references
                outputCharacterReference(writer, c);
            } else if (isHighSurrogate(c)) {
                final char d = chars.charAt(++i);
                writer.write(c);
                writer.write(d);
            } else if (c >= 0xfffe) {
                // Invalid characters in XML 1.0 and XML 1.1.
            } else {
                // Process characters not available in the current encoding
                outputCharacterReference(writer, c);
            }
            segstart = ++i;
        }
    }

    private void outputCharacterReference(final Writer writer, final int charval) throws IOException {
        writer.write("&#x");
        writer.write(Integer.toHexString(charval));
        writer.write(';');
    }

    /**
     * Test whether the given character is a high surrogate
     *
     * @param ch The character to test.
     * @return true if the character is the first character in a surrogate pair
     */
    private boolean isHighSurrogate(final int ch) {
        return (SURROGATE1_MIN <= ch && ch <= SURROGATE1_MAX);
    }

    private void indent() throws IOException {
        int spaces = level * indentation;
        // If a new line has been written in character content then we won't add
        // whitespace if the current column position is greater than the amount
        // of whitespace we would be adding.
        if (line > 0) {
            spaces -= column;
            if (spaces <= 0) {
                return; // There's already enough white space, don't add more.
            }
        }
        // Make sure the indent chars array is big enough to supply enough
        // whitespace at this level.
        growIndentChars(spaces + 2);

        // Output the initial newline character only if line==0
        final int start = (line == 0
                ? 0
                : 1);

        writer.write(indentChars, start, spaces + 1);
        sameline = false;
    }

    private void growIndentChars(final int len) {
        if (len >= indentChars.length) {
            int increment = 5 * indentation;
            if (len > indentChars.length + increment) {
                increment += len;
            }
            final char[] tmp = new char[indentChars.length + increment];
            System.arraycopy(indentChars, 0, tmp, 0, indentChars.length);
            Arrays.fill(tmp, indentChars.length, tmp.length, ' ');
            indentChars = tmp;
        }
    }

    public void setSortAtts(final boolean sortAtts) {
        this.sortAtts = sortAtts;
    }

    public void setOutputXMLDecl(final boolean outputXMLDecl) {
        this.outputXMLDecl = outputXMLDecl;
    }

    public void setIndentation(final int indentation) {
        this.indentation = indentation;
    }

    public void setNormalizeSpace(final boolean normalizeSpace) {
        this.normalizeSpace = normalizeSpace;
    }

    public enum XMLVersion {
        VERSION_1_0("1.0"),
        VERSION_1_1("1.1");

        private final String output;

        XMLVersion(final String output) {
            this.output = output;
        }

        public String getOutput() {
            return output;
        }
    }

    private static class AttributeNameComparator implements Comparator<String>, Serializable {

        private static final long serialVersionUID = -9219753718768871842L;

        @Override
        public int compare(final String o1, final String o2) {
            if (o1.startsWith("xsi:") && o2.startsWith("xsi:")) {
                return o1.compareTo(o2);
            } else if (o1.startsWith("xsi:")) {
                return -1;
            } else if (o2.startsWith("xsi:")) {
                return 1;
            }

            return o1.compareTo(o2);
        }
    }
}
