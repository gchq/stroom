////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2015 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package stroom.util.xml;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.AttributeMap;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NamespaceMap;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.serialize.CharacterReferenceGenerator;
import net.sf.saxon.serialize.HexCharacterReferenceGenerator;
import net.sf.saxon.serialize.XMLEmitter;
import net.sf.saxon.serialize.charcode.UTF8CharacterSet;
import net.sf.saxon.str.StringConstants;
import net.sf.saxon.str.StringView;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeChar;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.str.WhitespaceString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.Whitespace;
import net.sf.saxon.z.IntIterator;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.Stack;
import java.util.function.IntPredicate;
import javax.xml.transform.OutputKeys;

/**
 * An extension of the Saxon XMLEmitter that prevents forbidden XML characters U+FFFE and U+FFFF.
 */
@SuppressWarnings({"checkstyle:LineLength", "checkstyle:EmptyLineSeparator"})
public class MyXmlEmitter extends XMLEmitter {

    /**
     * @66: Added these lines to ensure these invalid chars are not written.
     */
    private static final char INVALID_CHARACTER_1 = '\ufffe'; // U+FFFE: INVALID CHARACTER
    private static final char INVALID_CHARACTER_2 = '\uffff'; // U+FFFF: INVALID CHARACTER

    // NOTE: we experimented with XMLUTF8Emitter which combines XML escaping and UTF8 encoding
    // into a single loop. Scrapped it because we couldn't measure any benefits - but there
    // ought to be, in theory. Perhaps we weren't buffering the writes carefully enough.

    protected boolean canonical = false;
    protected boolean started = false;
    protected boolean startedElement = false;
    protected boolean openStartTag = false;
    protected boolean declarationIsWritten = false;
    protected NodeName elementCode;
    protected int indentForNextAttribute = -1;
    protected boolean undeclareNamespaces = false;
    protected boolean unfailing = false;
    protected String internalSubset = null;
    protected char delimiter = '"';
    protected boolean[] attSpecials = specialInAtt;

    // The element stack holds the display names (lexical QNames) of elements that
    // have been started but not finished. It is used to obtain the element name
    // for the end tag.

    protected Stack<String> elementStack = new Stack<>();

    // For other names we use a hashtable. It

    private boolean indenting = false;
    private boolean requireWellFormed = false;
    protected CharacterReferenceGenerator characterReferenceGenerator = HexCharacterReferenceGenerator.THE_INSTANCE;


    protected static boolean[] specialInText;         // lookup table for special characters in text
    protected static boolean[] specialInAtt;          // lookup table for special characters in attributes
    protected static boolean[] specialInAttSingle;    // lookup table for special characters in attributes with single-quote delimiter
    // create look-up table for ASCII characters that need special treatment

    static {
        specialInText = new boolean[128];
        for (int i = 0; i <= 31; i++) {
            specialInText[i] = true;  // allowed in XML 1.1 as character references
        }
        for (int i = 32; i <= 127; i++) {
            specialInText[i] = false;
        }
        //    note, 0 is used to switch escaping on and off for mapped characters
        specialInText['\n'] = false;
        specialInText['\t'] = false;
        specialInText['\r'] = true;
        specialInText['<'] = true;
        specialInText['>'] = true;
        specialInText['&'] = true;

        specialInAtt = new boolean[128];
        for (int i = 0; i <= 31; i++) {
            specialInAtt[i] = true; // allowed in XML 1.1 as character references
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

        specialInAttSingle = Arrays.copyOf(specialInAtt, 128);
        specialInAttSingle['\"'] = false;
        specialInAttSingle['\''] = true;
    }

    IntPredicate isSpecialInText;
    IntPredicate isSpecialInAttribute;

    public MyXmlEmitter() {

    }

    /**
     * Set the character reference generator to be used for generating hexadecimal or decimal
     * character references
     *
     * @param generator the character reference generator to be used
     */

    public void setCharacterReferenceGenerator(CharacterReferenceGenerator generator) {
        this.characterReferenceGenerator = generator;
    }

    /**
     * Say that all non-ASCII characters should be escaped, regardless of the character encoding
     *
     * @param escape true if all non ASCII characters should be escaped
     */

    public void setEscapeNonAscii(Boolean escape) {
        // no action (not currently supported for this output method
    }

    /**
     * Start of the event stream. Nothing is done at this stage: the opening of the output
     * file is deferred until some content is written to it.
     */

    @Override
    public void open() throws XPathException {
    }

    /**
     * Start of a document node. Nothing is done at this stage: the opening of the output
     * file is deferred until some content is written to it.
     */

    @Override
    public void startDocument(int properties) throws XPathException {
    }

    /**
     * Notify the end of a document node
     */

    @Override
    public void endDocument() throws XPathException {
        // Following code removed as a result of bug 2323. If a failure occurs during xsl:result-document processing,
        // and the output is being written to a SAXResult, then the ContentHandler.endDocument() method is called in order
        // to close any open files; and this calls Emitter.endDocument() at a point where the output is incomplete.
//        if (!elementStack.isEmpty()) {
//            throw new IllegalStateException("Attempt to end document in serializer when elements are unclosed");
//        }
    }

    /**
     * Do the real work of starting the document. This happens when the first
     * content is written.
     *
     * @throws XPathException if an error occurs opening the output file
     */

    protected void openDocument() throws XPathException {
        assert writer != null;
//        if (writer == null) {
//            makeWriter();
//        }
        if (characterSet == null) {
            characterSet = UTF8CharacterSet.getInstance();
        }
        if (outputProperties == null) {
            outputProperties = new Properties();
        }

        undeclareNamespaces = "yes".equals(outputProperties.getProperty(SaxonOutputKeys.UNDECLARE_PREFIXES));
        canonical = "yes".equals(outputProperties.getProperty(SaxonOutputKeys.CANONICAL));
        unfailing = "yes".equals(outputProperties.getProperty(SaxonOutputKeys.UNFAILING));
        internalSubset = outputProperties.getProperty(SaxonOutputKeys.INTERNAL_DTD_SUBSET);

        if ("yes".equals(outputProperties.getProperty(SaxonOutputKeys.SINGLE_QUOTES))) {
            delimiter = '\'';
            attSpecials = specialInAttSingle;
        }

        if (allCharactersEncodable) {
            isSpecialInText = c -> (c < 127
                    ? specialInText[c]
                    : (
                            c < 160 ||
                                    c == 0x2028 ||
                                    c == INVALID_CHARACTER_1 ||
                                    c == INVALID_CHARACTER_2
                    ));
            isSpecialInAttribute = c -> (c < 127
                    ? attSpecials[c]
                    : (
                            c < 160 ||
                                    c == 0x2028 ||
                                    c == INVALID_CHARACTER_1 ||
                                    c == INVALID_CHARACTER_2
                    ));
        } else {
            isSpecialInText = c -> (c < 127
                    ? specialInText[c]
                    : (
                            c < 160 ||
                                    c == 0x2028 ||
                                    c > 65535 ||
                                    c == INVALID_CHARACTER_1 ||
                                    c == INVALID_CHARACTER_2 ||
                                    !characterSet.inCharset(c)
                    ));
            isSpecialInAttribute = c -> (c < 127
                    ? attSpecials[c]
                    : (
                            c < 160 ||
                                    c == 0x2028 ||
                                    c > 65535 ||
                                    c == INVALID_CHARACTER_1 ||
                                    c == INVALID_CHARACTER_2 ||
                                    !characterSet.inCharset(c)
                    ));
        }

        writeDeclaration();
    }

    /**
     * Output the XML declaration
     *
     * @throws XPathException if any error occurs
     */

    public void writeDeclaration() throws XPathException {
        if (declarationIsWritten) {
            return;
        }
        declarationIsWritten = true;
        try {
            indenting = "yes".equals(outputProperties.getProperty(OutputKeys.INDENT));

            String byteOrderMark = outputProperties.getProperty(SaxonOutputKeys.BYTE_ORDER_MARK);
            String encoding = outputProperties.getProperty(OutputKeys.ENCODING);
            if (encoding == null || encoding.equalsIgnoreCase("utf8") || canonical) {
                encoding = "UTF-8";
            }

            if ("yes".equals(byteOrderMark) && !canonical && (
                    "UTF-8".equalsIgnoreCase(encoding) ||
                            "UTF-16LE".equalsIgnoreCase(encoding) ||
                            "UTF-16BE".equalsIgnoreCase(encoding))) {
                writer.writeCodePoint(0xFEFF);
            }

            String omitXMLDeclaration = outputProperties.getProperty(OutputKeys.OMIT_XML_DECLARATION);
            if (omitXMLDeclaration == null) {
                omitXMLDeclaration = "no";
            }

            if (canonical) {
                omitXMLDeclaration = "yes";
            }

            String version = outputProperties.getProperty(OutputKeys.VERSION);
            if (version == null) {
                version = getConfiguration().getXMLVersion() == Configuration.XML10
                        ? "1.0"
                        : "1.1";
            } else {
                if (!version.equals("1.0") && !version.equals("1.1")) {
                    if (unfailing) {
                        version = "1.0";
                    } else {
                        XPathException err = new XPathException("XML version must be 1.0 or 1.1");
                        err.setErrorCode("SESU0013");
                        throw err;
                    }
                }
                if (!version.equals("1.0") && omitXMLDeclaration.equals("yes") &&
                        outputProperties.getProperty(OutputKeys.DOCTYPE_SYSTEM) != null) {
                    if (!unfailing) {
                        XPathException err = new XPathException(
                                "Values of 'version', 'omit-xml-declaration', and 'doctype-system' conflict");
                        err.setErrorCode("SEPM0009");
                        throw err;
                    }
                }
            }

            String undeclare = outputProperties.getProperty(SaxonOutputKeys.UNDECLARE_PREFIXES);
            if ("yes".equals(undeclare)) {
                undeclareNamespaces = true;
            }

            if (version.equals("1.0") && undeclareNamespaces) {
                if (unfailing) {
                    undeclareNamespaces = false;
                } else {
                    XPathException err = new XPathException("Cannot undeclare namespaces with XML version 1.0");
                    err.setErrorCode("SEPM0010");
                    throw err;
                }
            }

            String standalone = outputProperties.getProperty(OutputKeys.STANDALONE);
            if ("omit".equals(standalone)) {
                standalone = null;
            }

            if (standalone != null) {
                requireWellFormed = true;
                if (omitXMLDeclaration.equals("yes") && !unfailing) {
                    XPathException err = new XPathException("Values of 'standalone' and 'omit-xml-declaration' conflict");
                    err.setErrorCode("SEPM0009");
                    throw err;
                }
            }

            String systemId = outputProperties.getProperty(OutputKeys.DOCTYPE_SYSTEM);
            if (systemId != null && !"".equals(systemId)) {
                requireWellFormed = true;
            }

            if (omitXMLDeclaration.equals("no")) {
                writer.writeAscii(XML_DECL_VERSION);
                writer.write(version);
                writer.writeAscii(QUOTE_SPACE);
                writer.writeAscii(XML_DECL_ENCODING);
                writer.write(encoding);
                writer.writeCodePoint('"');
                if (standalone != null) {
                    writer.writeAscii(XML_DECL_STANDALONE);
                    writer.write(standalone);
                    writer.writeCodePoint('"');
                }
                writer.writeAscii(StringConstants.PI_END);
//                writer.write("<?xml version=\"" + version + "\" " + "encoding=\"" + encoding + '\"' +
//                                     (standalone != null ? " standalone=\"" + standalone + '\"' : "") + "?>");
                // don't write a newline character: it's wrong if the output is an
                // external general parsed entity
            }
        } catch (java.io.IOException err) {
            throw new XPathException("Failure writing to " + getSystemId(), err);
        }
    }

    private static final byte[] XML_DECL_VERSION = StringConstants.bytes("<?xml version=\"");
    private static final byte[] XML_DECL_ENCODING = StringConstants.bytes("encoding=\"");
    private static final byte[] XML_DECL_STANDALONE = StringConstants.bytes(" standalone=\"");
    private static final byte[] QUOTE_SPACE = StringConstants.bytes("\" ");
    protected static final byte[] DOCTYPE = StringConstants.bytes("<!DOCTYPE ");
    private static final byte[] SYSTEM = StringConstants.bytes("  SYSTEM ");
    private static final byte[] PUBLIC = StringConstants.bytes("  PUBLIC \"");
    protected static final byte[] RIGHT_ANGLE_NEWLINE = StringConstants.bytes(">\n");

    /**
     * Output the document type declaration
     *
     * @param name        the qualified name of the element
     * @param displayName The element name as displayed
     * @param systemId    The DOCTYPE system identifier
     * @param publicId    The DOCTYPE public identifier
     * @throws net.sf.saxon.trans.XPathException if an error occurs writing to the output
     */

    protected void writeDocType(NodeName name, String displayName, String systemId, String publicId)
            throws XPathException {
        try {
            if (!canonical) {
                if (declarationIsWritten && !indenting) {
                    // don't add a newline if indenting, because the indenter will already have done so
                    writer.writeCodePoint(0x0A);
                }
                writer.writeAscii(DOCTYPE);
                writer.write(displayName);
                writer.writeCodePoint(0x0A);
                String quotedSystemId = null;
                if (systemId != null) {
                    if (systemId.contains("\"")) {
                        quotedSystemId = "'" + systemId + "'";
                    } else {
                        quotedSystemId = '"' + systemId + '"';
                    }
                }
                if (systemId != null && publicId == null) {
                    writer.writeAscii(SYSTEM);
                    writer.write(quotedSystemId);
                } else if (systemId == null && publicId != null) {     // handles the HTML case
                    writer.writeAscii(PUBLIC);
                    writer.write(publicId);
                    writer.writeCodePoint('"');
                } else if (publicId != null) {
                    writer.writeAscii(PUBLIC);
                    writer.write(publicId);
                    writer.writeAscii(QUOTE_SPACE);
                    writer.write(quotedSystemId);
                }
                if (internalSubset != null) {
                    writer.writeCodePoint('[');
                    writer.writeCodePoint(0x0A);
                    writer.write(internalSubset);
                    writer.writeCodePoint(0x0A);
                    writer.writeCodePoint(']');
                }
                writer.writeAscii(RIGHT_ANGLE_NEWLINE);
            }
        } catch (java.io.IOException err) {
            throw new XPathException("Failure writing to " + getSystemId(), err);
        }
    }

    /**
     * End of the document.
     */

    @Override
    public void close() throws XPathException {
        // if nothing has been written, we should still create the file and write an XML declaration
        if (!started) {
            openDocument();
        }
        try {
            if (writer != null) {
                writer.flush();
            }
        } catch (java.io.IOException err) {
            throw new XPathException("Failure writing to " + getSystemId(), err);
        }
        super.close();
    }

    /**
     * Start of an element. Output the start tag, escaping special characters.
     *
     * @param elemName   the name of the element
     * @param type       the type annotation of the element
     * @param attributes the attributes of this element
     * @param namespaces the in-scope namespaces of this element: generally this is all the in-scope
     *                   namespaces, without relying on inheriting namespaces from parent elements
     * @param location   an object providing information about the module, line, and column where the node originated
     * @param properties bit-significant properties of the element node. If there are no relevant
     *                   properties, zero is supplied. The definitions of the bits are in class {@link ReceiverOption}
     * @throws XPathException if an error occurs
     */

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {
        previousAtomic = false;
        if (!started) {
            openDocument();
        } else if (requireWellFormed && elementStack.isEmpty() && startedElement && !unfailing) {
            XPathException err = new XPathException("When 'standalone' or 'doctype-system' is specified, " +
                    "the document must be well-formed; but this document contains more than one top-level element");
            err.setErrorCode("SEPM0004");
            throw err;
        }
        startedElement = true;

        String displayName = elemName.getDisplayName();
        if (!allCharactersEncodable) {
            int badchar = testCharacters(StringView.of(displayName));
            if (badchar != 0) {
                XPathException err = new XPathException("Element name contains a character (decimal + " +
                        badchar + ") not available in the selected encoding");
                err.setErrorCode("SERE0008");
                throw err;
            }
        }

        elementStack.push(displayName);
        elementCode = elemName;

        try {
            if (!started) {
                String systemId = outputProperties.getProperty(OutputKeys.DOCTYPE_SYSTEM);
                String publicId = outputProperties.getProperty(OutputKeys.DOCTYPE_PUBLIC);
                // Treat "" as equivalent to absent. This goes beyond what the spec strictly allows.
                if ("".equals(systemId)) {
                    systemId = null;
                }
                if ("".equals(publicId)) {
                    publicId = null;
                }
                if (systemId != null) {
                    requireWellFormed = true;
                    writeDocType(elemName, displayName, systemId, publicId);
                } else if (writeDocTypeWithNullSystemId()) {
                    writeDocType(elemName, displayName, null, publicId);
                }
                started = true;
            }
            if (openStartTag) {
                closeStartTag();
            }
            writer.writeCodePoint('<');
            writer.write(displayName);

            if (indentForNextAttribute >= 0) {
                indentForNextAttribute += displayName.length();
            }

            boolean isFirst = true;

            for (NamespaceBinding ns : namespaces) {
                namespace(ns.getPrefix(), ns.getURI(), isFirst);
                isFirst = false;
            }

            for (AttributeInfo att : attributes) {
                attribute(att.getNodeName(), att.getValue(), att.getProperties(), isFirst);
                isFirst = false;
            }

            openStartTag = true;
            indentForNextAttribute = -1;

        } catch (java.io.IOException err) {
            throw new XPathException("Failure writing to " + getSystemId(), err);
        }
    }

    protected boolean writeDocTypeWithNullSystemId() {
        return internalSubset != null;
    }

    public void namespace(String nsprefix, String nsuri, boolean isFirst) throws XPathException {
        try {
            if (nsprefix.isEmpty()) {
                if (isFirst) {
                    writer.writeCodePoint(' ');
                } else {
                    writeAttributeIndentString();
                }
                writeAttribute(elementCode, "xmlns", nsuri, ReceiverOption.NONE);
            } else //noinspection StatementWithEmptyBody
                if (nsprefix.equals("xml")) {
                    //return;
                } else {
                    int badchar = testCharacters(StringView.of(nsprefix));
                    if (badchar != 0) {
                        XPathException err = new XPathException("Namespace prefix contains a character (decimal + " +
                                badchar + ") not available in the selected encoding");
                        err.setErrorCode("SERE0008");
                        throw err;
                    }
                    if (undeclareNamespaces || !nsuri.isEmpty()) {
                        if (isFirst) {
                            writer.writeCodePoint(' ');
                        } else {
                            writeAttributeIndentString();
                        }
                        writeAttribute(elementCode, "xmlns:" + nsprefix, nsuri, ReceiverOption.NONE);
                    }
                }

        } catch (java.io.IOException err) {
            throw new XPathException("Failure writing to " + getSystemId(), err);
        }
    }

    /**
     * Set the indentation to be used for attributes (this excludes the length of the
     * element name itself)
     *
     * @param indent the number of spaces to be output before each attribute (on a new line)
     */

    public void setIndentForNextAttribute(int indent) {
        indentForNextAttribute = indent;
    }

    private void attribute(NodeName nameCode, String value, int properties, boolean isFirst)
            throws XPathException {

        String displayName = nameCode.getDisplayName();
        if (!allCharactersEncodable) {
            int badchar = testCharacters(StringView.of(displayName));
            if (badchar != 0) {
                if (unfailing) {
                    displayName = convertToAscii(StringView.of(displayName)).toString();
                } else {
                    XPathException err = new XPathException("Attribute name contains a character (decimal + " +
                            badchar + ") not available in the selected encoding");
                    err.setErrorCode("SERE0008");
                    throw err;
                }
            }
        }

        try {
            if (isFirst) {
                writer.writeCodePoint(' ');
            } else {
                writeAttributeIndentString();
            }
        } catch (java.io.IOException err) {
            throw new XPathException("Failure writing to " + getSystemId(), err);
        }


        writeAttribute(
                elementCode,
                displayName,
                value,
                properties);


    }

    protected void writeAttributeIndentString() throws IOException {
        if (indentForNextAttribute < 0) {
            writer.writeCodePoint(' ');
        } else {
            writer.writeCodePoint('\n');
            if (indentForNextAttribute > 0) {
                // Changed to match pre Saxon v11 behaviour.
                writer.writeRepeatedAscii((byte) 0x20, indentForNextAttribute - 1);
            }
        }
    }


    /**
     * Mark the end of the start tag
     *
     * @throws XPathException if an IO exception occurs
     */

    public void closeStartTag() throws XPathException {
        try {
            if (openStartTag) {
                writer.writeCodePoint('>');
                openStartTag = false;
            }
        } catch (java.io.IOException err) {
            throw new XPathException("Failure writing to " + getSystemId(), err);
        }
    }

    /**
     * Close an empty element tag. (This is overridden in XHTMLEmitter).
     *
     * @param displayName the name of the empty element
     * @param nameCode    the fingerprint of the name of the empty element
     * @throws IOException if an IO exception occurs
     */

    protected void writeEmptyElementTagCloser(String displayName, NodeName nameCode) throws IOException {
        if (canonical) {
            writer.writeCodePoint('>');
            writer.writeAscii(StringConstants.END_TAG_START);
            writer.write(displayName);
            writer.writeCodePoint('>');
        } else {
            writer.writeAscii(StringConstants.EMPTY_TAG_END);
        }
    }

    /**
     * Write attribute name=value pair.
     *
     * @param elCode     The element name is not used in this version of the
     *                   method, but is used in the HTML subclass.
     * @param attname    The attribute name, which has already been validated to ensure
     *                   it can be written in this encoding
     * @param value      The value of the attribute
     * @param properties Any special properties of the attribute
     * @throws net.sf.saxon.trans.XPathException if an error occurs
     */

    protected void writeAttribute(NodeName elCode, String attname, String value, int properties) throws XPathException {
        try {
            writer.write(attname);
            if (ReceiverOption.contains(properties, ReceiverOption.NO_SPECIAL_CHARS)) {
                writer.writeCodePoint('=');
                writer.writeCodePoint(delimiter);
                writer.write(value);
                writer.writeCodePoint(delimiter);
            } else if (ReceiverOption.contains(properties, ReceiverOption.USE_NULL_MARKERS)) {
                // null (0) characters will be used before and after any section of
                // the value generated from a character map
                writer.writeCodePoint('=');
                char delim = value.indexOf('"') >= 0 && value.indexOf('\'') < 0
                        ? '\''
                        : delimiter;
                writer.writeCodePoint(delim);
                writeEscape(StringView.tidy(value), true);
                writer.writeCodePoint(delim);
            } else {
                writer.writeCodePoint('=');
                writer.writeCodePoint(delimiter);
                if (ReceiverOption.contains(properties, ReceiverOption.DISABLE_ESCAPING)) {
                    writer.write(value);
                } else {
                    writeEscape(StringView.tidy(value), true);
                }
                writer.writeCodePoint(delimiter);
            }
        } catch (java.io.IOException err) {
            throw new XPathException("Failure writing to " + getSystemId(), err);
        }
    }


    /**
     * Test that all characters in a name (for example) are supported in the target encoding.
     *
     * @param chars the characters to be tested
     * @return zero if all the characters are available, or the value of the
     * first offending character if not
     */

    protected int testCharacters(UnicodeString chars) {
        IntIterator iter = chars.codePoints();
        while (iter.hasNext()) {
            int ch = iter.next();
            if (ch > 127 && !characterSet.inCharset(ch)) {
                return ch;
            }
        }
        return 0;
    }

    /**
     * Where characters are not available in the selected encoding, substitute them
     *
     * @param chars the characters to be converted
     * @return the converted string
     */

    protected UnicodeString convertToAscii(UnicodeString chars) {
        UnicodeBuilder buff = new UnicodeBuilder();
        IntIterator iter = chars.codePoints();
        while (iter.hasNext()) {
            int c = iter.next();
            if (c >= 20 && c < 127) {
                buff.append(c);
            } else {
                buff.append("_" + c + "_");
            }
        }
        return buff.toUnicodeString();
    }

    /**
     * End of an element.
     */

    @Override
    public void endElement() throws XPathException {
        String displayName = elementStack.pop();
        try {
            if (openStartTag) {
                writeEmptyElementTagCloser(displayName, elementCode);
                openStartTag = false;
            } else {
                writer.writeAscii(StringConstants.END_TAG_START);
                writer.write(displayName);
                writer.writeCodePoint('>');
            }
        } catch (java.io.IOException err) {
            throw new XPathException("Failure writing to " + getSystemId(), err);
        }
    }

    /**
     * Character data.
     */

    @Override
    public void characters(UnicodeString chars, Location locationId, int properties) throws XPathException {
        if (!started) {
            openDocument();
        }

        if (requireWellFormed && elementStack.isEmpty() && !Whitespace.isAllWhite(chars) && !unfailing) {
            XPathException err = new XPathException("When 'standalone' or 'doctype-system' is specified, " +
                    "the document must be well-formed; but this document contains a top-level text node");
            err.setErrorCode("SEPM0004");
            throw err;
        }

        try {
            if (openStartTag) {
                closeStartTag();
            }
            if (chars instanceof WhitespaceString) {
                ((WhitespaceString) chars).write(writer);
            } else if (ReceiverOption.contains(properties, ReceiverOption.NO_SPECIAL_CHARS)) {
                writer.write(chars);
            } else if (!ReceiverOption.contains(properties, ReceiverOption.DISABLE_ESCAPING)) {
                writeEscape(chars, false);
            } else {
                // disable-output-escaping="yes"
                if (testCharacters(chars) == 0) {
                    if (!ReceiverOption.contains(properties, ReceiverOption.USE_NULL_MARKERS)) {
                        // null (0) characters will be used before and after any section of
                        // the value generated from a character map
                        writer.write(chars);
                    } else {
                        // Need to strip out any null markers. See test output-html109
                        IntIterator iter = chars.codePoints();
                        while (iter.hasNext()) {
                            int c = iter.next();
                            if (c != 0) {
                                writer.writeCodePoint(c);
                            }
                        }
                    }
                } else {
                    // Using disable output escaping with characters
                    // that are not available in the target encoding
                    // The required action is to ignore d-o-e in respect of those characters that are
                    // not available in the encoding. This is slow...
                    IntIterator iter = chars.codePoints();
                    while (iter.hasNext()) {
                        int c = iter.next();
                        if (c != 0) {
                            if (characterSet.inCharset(c)) {
                                writer.writeCodePoint(c);
                            } else {
                                writeEscape(new UnicodeChar(c), false);
                            }
                        }
                    }
                }
            }
        } catch (java.io.IOException err) {
            throw new XPathException("Failure writing to " + getSystemId(), err);
        }
    }

    /**
     * Handle a processing instruction.
     */

    @Override
    public void processingInstruction(String target, UnicodeString data, Location locationId, int properties)
            throws XPathException {
        if (!started) {
            openDocument();
        }
        int x = testCharacters(StringView.of(target));
        if (x != 0) {
            if (unfailing) {
                target = convertToAscii(StringView.of(target)).toString();
            } else {
                XPathException err = new XPathException(
                        "Character in processing instruction name cannot be represented " +
                                "in the selected encoding (code " + x + ')');
                err.setErrorCode("SERE0008");
                throw err;
            }
        }
        x = testCharacters(data);
        if (x != 0) {
            if (unfailing) {
                data = convertToAscii(data);
            } else {
                XPathException err = new XPathException(
                        "Character in processing instruction data cannot be represented " +
                                "in the selected encoding (code " + x + ')');
                err.setErrorCode("SERE0008");
                throw err;
            }
        }
        try {
            if (openStartTag) {
                closeStartTag();
            }
            writer.writeAscii(StringConstants.PI_START);
            writer.write(target);
            if (!data.isEmpty()) {
                writer.writeCodePoint(0x20);
                writer.write(data);
            }
            writer.writeAscii(StringConstants.PI_END);
        } catch (java.io.IOException err) {
            throw new XPathException("Failure writing to " + getSystemId(), err);
        }
    }

    /**
     * Write contents of array to current writer, after escaping special characters.
     * This method converts the XML special characters (such as &lt; and &amp;) into their
     * predefined entities.
     *
     * @param chars       The character sequence containing the string
     * @param inAttribute Set to true if the text is in an attribute value
     * @throws IOException    if an IO exception occurs
     * @throws XPathException if an IO exception occurs
     */

    protected void writeEscape(UnicodeString chars, final boolean inAttribute)
            throws java.io.IOException, XPathException {
        long segstart = 0;
        boolean disabled = false;
        final boolean[] specialChars = inAttribute
                ? attSpecials
                : specialInText;

        if (chars instanceof WhitespaceString) {
            ((WhitespaceString) chars).writeEscape(specialChars, writer);
            return;
        }

        IntPredicate special = inAttribute
                ? isSpecialInAttribute
                : isSpecialInText;
        final long clength = chars.length();
        while (segstart < clength) {
            // find a maximal sequence of "ordinary" characters
            long found = chars.indexWhere(special, segstart);
            long i = found == -1
                    ? clength
                    : found;

            // if this was the whole (or remainder of the) string write it out and exit
            if (found < 0) {
                if (segstart == 0) {
                    writer.write(chars);
                } else {
                    writer.write(chars.substring(segstart, clength));
                }
                return;
            }

            // otherwise write out this sequence
            if (i > segstart) {
                writer.write(chars.substring(segstart, i));
            }

            // examine the special character that interrupted the scan
            final int c = chars.codePointAt(i);
            if (c == 0) {
                // used to switch escaping on and off
                disabled = !disabled;
            } else if (disabled) {
                if (c > 127 && !characterSet.inCharset(c)) {
                    XPathException de = new XPathException("Character " + c + " (x" + Integer.toHexString(c) +
                            ") is not available in the chosen encoding");
                    de.setErrorCode("SERE0008");
                    throw de;
                }
                writeCodePoint(c);
            } else if (c < 127) {
                // process special ASCII characters
                switch (c) {
                    case '<':
                        writer.writeAscii(StringConstants.ESCAPE_LT);
                        break;
                    case '>':
                        writer.writeAscii(StringConstants.ESCAPE_GT);
                        break;
                    case '&':
                        writer.writeAscii(StringConstants.ESCAPE_AMP);
                        break;
                    case '\"':
                        writer.writeAscii(StringConstants.ESCAPE_QUOT);
                        break;
                    case '\'':
                        writer.writeAscii(StringConstants.ESCAPE_APOS);
                        break;
                    case '\n':
                        writer.writeAscii(StringConstants.ESCAPE_NL);
                        break;
                    case '\r':
                        writer.writeAscii(StringConstants.ESCAPE_CR);
                        break;
                    case '\t':
                        writer.writeAscii(StringConstants.ESCAPE_TAB);
                        break;
                    default:
                        // C0 control characters
                        characterReferenceGenerator.outputCharacterReference(c, writer);
                        break;
                }
            } else if (c < 160 || c == 0x2028) {
                // XML 1.1 requires these characters to be written as character references
                characterReferenceGenerator.outputCharacterReference(c, writer);
            } else if (c > 65535) {
                if (characterSet.inCharset(c)) {
                    writeCodePoint(c);
                } else {
                    characterReferenceGenerator.outputCharacterReference(c, writer);
                }
            } else if (isForbidden(c)) {
                // Ignore char.
            } else {
                // process characters not available in the current encoding
                characterReferenceGenerator.outputCharacterReference(c, writer);
            }
            segstart = ++i;
        }
    }

    protected void writeCodePoint(int c) throws IOException {
        writer.writeCodePoint(c);
    }


    /**
     * Handle a comment.
     */

    @Override
    public void comment(UnicodeString chars, Location locationId, int properties) throws XPathException {
        if (!started) {
            openDocument();
        }
        int x = testCharacters(chars);
        if (x != 0) {
            if (unfailing) {
                chars = convertToAscii(chars);
            } else {
                XPathException err = new XPathException("Character in comment cannot be represented " +
                        "in the selected encoding (code " + x + ')');
                err.setErrorCode("SERE0008");
                throw err;
            }
        }
        try {
            if (openStartTag) {
                closeStartTag();
            }
            writer.writeAscii(StringConstants.COMMENT_START);
            writer.write(chars);
            writer.writeAscii(StringConstants.COMMENT_END);
        } catch (java.io.IOException err) {
            throw new XPathException("Failure writing to " + getSystemId(), err);
        }
    }

    /**
     * Ask whether this Receiver (or the downstream pipeline) makes any use of the type annotations
     * supplied on element and attribute events
     *
     * @return true if the Receiver makes any use of this information. If false, the caller
     * may supply untyped nodes instead of supplying the type annotation
     */

    @Override
    public boolean usesTypeAnnotations() {
        return false;
    }

    /**
     * Ask whether anything has yet been written
     *
     * @return true if content has been output
     */

    public boolean isStarted() {
        return started;
    }

    /**
     * Determines if the supplied characters is forbidden in XML, even as a character reference.
     *
     * @param c The char to test.
     * @return True if the char is forbidden.
     */
    private boolean isForbidden(final int c) {
        // surrogates, U+FFFE and U+FFFF are forbidden in XML.
        return c == INVALID_CHARACTER_1 || c == INVALID_CHARACTER_2;
    }
}
