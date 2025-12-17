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

/**
 * Locates the element number (start plus end) and content position that an
 * error occurred.
 */
public class LocationFilter extends AbstractXMLFilter implements Locator {
    private int elementCount;
    private int contentPos;

    /**
     * This method tells filters that a stream is about to be parsed so that
     * they can complete any setup necessary.
     *
     * @throws SAXException Could be thrown by an implementing class.
     */
    @Override
    public void startStream() {
        elementCount = 0;
        contentPos = 0;
        super.startStream();
    }

    /**
     * Counts a start element.
     *
     * @param uri       The element's Namespace URI, or the empty string.
     * @param localName The element's local name, or the empty string.
     * @param qName     The element's qualified (prefixed) name, or the empty string.
     * @param atts      The element's attributes.
     * @throws SAXException Not thrown.
     * @see stroom.pipeline.filter.AbstractXMLFilter#startElement(java.lang.String,
     * java.lang.String, java.lang.String, org.xml.sax.Attributes)
     */
    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        contentPos = 0;
        elementCount++;
        super.startElement(uri, localName, qName, atts);
    }

    /**
     * Counts an end element.
     *
     * @param uri       The element's Namespace URI, or the empty string.
     * @param localName The element's local name, or the empty string.
     * @param qName     The element's qualified (prefixed) name, or the empty string.
     * @throws SAXException Not thrown.
     * @see stroom.pipeline.filter.AbstractXMLFilter#endElement(java.lang.String,
     * java.lang.String, java.lang.String)
     */
    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        contentPos = 0;
        elementCount++;
        super.endElement(uri, localName, qName);
    }

    /**
     * Adds to the content position.
     *
     * @param ch     the characters from the XML document
     * @param start  the start position in the array
     * @param length the number of characters to read from the array
     * @throws SAXException Not thrown.
     * @see stroom.pipeline.filter.AbstractXMLFilter#characters(char[],
     * int, int)
     */
    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        contentPos += length;
        super.characters(ch, start, length);
    }

    /**
     * This method actually gets the position within the element content.
     *
     * @return The position in the element content.
     * @see org.xml.sax.Locator#getColumnNumber()
     */
    @Override
    public int getColumnNumber() {
        return contentPos;
    }

    /**
     * This method actually gets the element count (start plus end) that is
     * currently being processed.
     *
     * @return The element count (start plus end).
     * @see org.xml.sax.Locator#getLineNumber()
     */
    @Override
    public int getLineNumber() {
        return elementCount;
    }

    /**
     * Not implemented.
     *
     * @return null.
     * @see org.xml.sax.Locator#getPublicId()
     */
    @Override
    public String getPublicId() {
        return null;
    }

    /**
     * Not implemented.
     *
     * @return null.
     * @see org.xml.sax.Locator#getSystemId()
     */
    @Override
    public String getSystemId() {
        return null;
    }
}
