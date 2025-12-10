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

import stroom.util.CharBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * A filter used to display individual SAX events at any point in the XML
 * pipeline. Many instances of this filter can be used and added one or more
 * times to any MultiWayXMLFilters.
 */
public class TestSAXEventFilter extends AbstractXMLFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSAXEventFilter.class);

    private static final String NEW_LINE = "\n";
    private static final String SPACE = " ";
    private static final String START_PREFIX_MAPPING = "startPrefixMapping:";
    private static final String START_ELEMENT = "startElement:";
    private static final String COMMA = ",";
    private static final String START_DOCUMENT = "startDocument:";
    private static final String SKIPPED_ENTITY = "skippedEntity:";
    private static final String SET_DOCUMENT_LOCATOR = "setDocumentLocator:";
    private static final String PROCESSING_INSTRUCTION = "processingInstruction:";
    private static final String IGNORABLE_WHITESPACE = "ignorableWhitespace:";
    private static final String END_PREFIX_MAPPING = "endPrefixMapping:";
    private static final String DELIMITER = "|";
    private static final String END_ELEMENT = "endElement:";
    private static final String END_DOCUMENT = "endDocument:";
    private static final Pattern NEW_LINE_PATTERN = Pattern.compile(NEW_LINE);
    final CharBuffer cb = new CharBuffer(100);
    private final CharBuffer content = new CharBuffer(500);
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    /**
     * @param locator an object that can return the location of any SAX document
     *                event
     * @see org.xml.sax.Locator
     */
    @Override
    public void setDocumentLocator(final Locator locator) {
        super.setDocumentLocator(locator);
        writeln(SET_DOCUMENT_LOCATOR);
    }

    /**
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     * @see #endDocument
     */
    @Override
    public void startDocument() throws SAXException {
        super.startDocument();
        writeln(START_DOCUMENT);
    }

    /**
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     * @see #startDocument
     */
    @Override
    public void endDocument() throws SAXException {
        super.endDocument();
        writeln(END_DOCUMENT);
    }

    /**
     * @param prefix the Namespace prefix being declared. An empty string is used
     *               for the default element namespace, which has no prefix.
     * @param uri    the Namespace URI the prefix is mapped to
     * @throws org.xml.sax.SAXException the client may throw an exception during processing
     * @see #endPrefixMapping
     * @see #startElement
     */
    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        super.startPrefixMapping(prefix, uri);

        cb.append(START_PREFIX_MAPPING);
        cb.append(prefix);
        cb.append(DELIMITER);
        cb.append(uri);

        writeln(cb);
    }

    /**
     * @param prefix the prefix that was being mapped. This is the empty string
     *               when a default mapping scope ends.
     * @throws org.xml.sax.SAXException the client may throw an exception during processing
     * @see #startPrefixMapping
     * @see #endElement
     */
    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        super.endPrefixMapping(prefix);

        cb.append(END_PREFIX_MAPPING);
        cb.append(prefix);

        writeln(cb);
    }

    /**
     * @param uri       the Namespace URI, or the empty string if the element has no
     *                  Namespace URI or if Namespace processing is not being
     *                  performed
     * @param localName the local name (without prefix), or the empty string if
     *                  Namespace processing is not being performed
     * @param qName     the qualified name (with prefix), or the empty string if
     *                  qualified names are not available
     * @param atts      the attributes attached to the element. If there are no
     *                  attributes, it shall be an empty Attributes object. The value
     *                  of this object after startElement returns is undefined
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     * @see #endElement
     * @see org.xml.sax.Attributes
     * @see org.xml.sax.helpers.AttributesImpl
     */
    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        super.startElement(uri, localName, qName, atts);

        cb.append(START_ELEMENT);
        cb.append(uri);
        cb.append(DELIMITER);
        cb.append(localName);
        cb.append(DELIMITER);
        cb.append(qName);
        cb.append(DELIMITER);

        for (int i = 0; i < atts.getLength(); i++) {
            cb.append(atts.getURI(i));
            cb.append(COMMA);
            cb.append(atts.getLocalName(i));
            cb.append(COMMA);
            cb.append(atts.getQName(i));
            cb.append(COMMA);
            cb.append(atts.getType(i));
            cb.append(COMMA);
            cb.append(atts.getValue(i));
        }

        writeln(cb);
    }

    /**
     * @param uri       the Namespace URI, or the empty string if the element has no
     *                  Namespace URI or if Namespace processing is not being
     *                  performed
     * @param localName the local name (without prefix), or the empty string if
     *                  Namespace processing is not being performed
     * @param qName     the qualified XML name (with prefix), or the empty string if
     *                  qualified names are not available
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     */
    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        super.endElement(uri, localName, qName);

        cb.append(END_ELEMENT);
        cb.append(uri);
        cb.append(DELIMITER);
        cb.append(localName);
        cb.append(DELIMITER);
        cb.append(qName);

        writeln(cb);
    }

    /**
     * @param ch     the characters from the XML document
     * @param start  the start position in the array
     * @param length the number of characters to read from the array
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     * @see #ignorableWhitespace
     * @see org.xml.sax.Locator
     */
    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        super.characters(ch, start, length);
        content.append(ch, start, length);
    }

    /**
     * @param ch     the characters from the XML document
     * @param start  the start position in the array
     * @param length the number of characters to read from the array
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     * @see #characters
     */
    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
        super.ignorableWhitespace(ch, start, length);
        cb.append(IGNORABLE_WHITESPACE);
        writeln(cb);
    }

    /**
     * @param target the processing instruction target
     * @param data   the processing instruction data, or null if none was supplied.
     *               The data does not include any whitespace separating it from
     *               the target
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     */
    @Override
    public void processingInstruction(final String target, final String data) throws SAXException {
        super.processingInstruction(target, data);
        cb.append(PROCESSING_INSTRUCTION);
        cb.append(target);
        cb.append(DELIMITER);
        cb.append(data);
        writeln(cb);
    }

    /**
     * @param name the name of the skipped entity. If it is a parameter entity,
     *             the name will begin with '%', and if it is the external DTD
     *             subset, it will be the string "[dtd]"
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     */
    @Override
    public void skippedEntity(final String name) throws SAXException {
        super.skippedEntity(name);

        cb.append(SKIPPED_ENTITY);
        cb.append(name);
        writeln(cb);
    }

    private void writeln(final CharBuffer cb) {
        try {
            // Output content first if we have any.
            if (!content.isEmpty()) {
                final String out = NEW_LINE_PATTERN.matcher(content).replaceAll(SPACE);
                output.write(out.getBytes());
                output.write(NEW_LINE.getBytes());
                content.clear();
            }

            final String out = NEW_LINE_PATTERN.matcher(cb).replaceAll(SPACE);
            output.write(out.getBytes());
            output.write(NEW_LINE.getBytes());
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }

        cb.clear();
    }

    private void writeln(final String type) {
        try {
            // Output content first if we have any.
            if (!content.isEmpty()) {
                final String out = NEW_LINE_PATTERN.matcher(content).replaceAll(SPACE);
                output.write(out.getBytes());
                output.write(NEW_LINE.getBytes());
                content.clear();
            }

            output.write(type.getBytes());
            output.write(NEW_LINE.getBytes());
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    /**
     * Gets the output that it has generated from received SAX events as a
     * string.
     *
     * @return The output that it has generated from received SAX events as a
     * string.
     */
    public String getOutput() {
        return output.toString();
    }
}
