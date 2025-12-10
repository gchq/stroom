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

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

public class ResultLocator extends AbstractXMLFilter implements Locator {
    private static final int INDENT = 2;

    private final boolean steppingMode;
    private int lineNo;
    private int colNo;
    private int depth;
    private boolean inStartElement;
    private Locator locator;

    public ResultLocator(final boolean steppingMode) {
        this.steppingMode = steppingMode;
    }

    /**
     * This method tells filters that a stream is about to be parsed so that
     * they can complete any setup necessary.
     */
    @Override
    public void startStream() {
        // Assume the first line is the XML declaration.
        lineNo = 1;
        colNo = 0;
        depth = 0;
        inStartElement = false;
        super.startStream();
    }

    @Override
    public void setDocumentLocator(final Locator locator) {
        this.locator = locator;
        super.setDocumentLocator(this);
    }

    @Override
    public void startDocument() throws SAXException {
        // If we are stepping then we always reset for each doc.
        if (steppingMode) {
            // Assume the first line is the XML declaration.
            lineNo = 1;
            colNo = 0;
            depth = 0;
            inStartElement = false;
        }

        super.startDocument();
    }

    /**
     * This method is entered for every start element. If this is the first
     * start element in a document it looks for a schema declaration to use to
     * validate the rest of the document.
     *
     * @param uri       The element's namespace URI, or the empty string.
     * @param localName The element's local name, or the empty string.
     * @param qName     The element's qualified (prefixed) name, or the empty string.
     * @param atts      The element's attributes.
     * @throws org.xml.sax.SAXException The client may throw an exception during processing.
     * @see stroom.pipeline.filter.AbstractXMLFilter#startElement(java.lang.String,
     * java.lang.String, java.lang.String, org.xml.sax.Attributes)
     */
    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        colNo = depth * INDENT;
        lineNo++;
        inStartElement = true;

        depth++;

        super.startElement(uri, localName, qName, atts);
    }

    /**
     * Receive notification of the end of an element.
     * <p>
     * <p>
     * The SAX parser will invoke this method at the end of every element in the
     * XML document; there will be a corresponding {@link #startElement
     * startElement} event for every endElement event (even when the element is
     * empty).
     * </p>
     * <p>
     * <p>
     * For information on the names, see startElement.
     * </p>
     *
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
        depth--;

        if (!inStartElement) {
            lineNo++;
            colNo = depth * INDENT;
        }
        inStartElement = false;

        super.endElement(uri, localName, qName);
    }

    /**
     * Sends characters to the current validator handler.
     *
     * @param ch     Characters.
     * @param start  Start of char buffer.
     * @param length Number of chars to send.
     * @throws SAXException Not thrown.
     * @see stroom.pipeline.filter.AbstractXMLFilter#characters(char[],
     * int, int)
     */
    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        colNo += length;
        super.characters(ch, start, length);
    }

    /**
     * This method gets the calculated column number by assuming that the output
     * is pretty printed..
     *
     * @return The assumed pretty printed column number.
     * @see org.xml.sax.Locator#getColumnNumber()
     */
    @Override
    public int getColumnNumber() {
        return colNo;
    }

    /**
     * This method gets the calculated line number by assuming that the output
     * is pretty printed.
     *
     * @return The assumed pretty printed line number.
     * @see org.xml.sax.Locator#getLineNumber()
     */
    @Override
    public int getLineNumber() {
        return lineNo;
    }

    /**
     * Not implemented.
     *
     * @return null.
     * @see org.xml.sax.Locator#getPublicId()
     */
    @Override
    public String getPublicId() {
        return locator.getPublicId();
    }

    /**
     * Not implemented.
     *
     * @return null.
     * @see org.xml.sax.Locator#getSystemId()
     */
    @Override
    public String getSystemId() {
        return locator.getSystemId();
    }
}
