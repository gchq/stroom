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

import stroom.pipeline.xml.event.simple.SimpleEventList;
import stroom.pipeline.xml.event.simple.SimpleEventListBuilder;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * This filter is used to buffer SAX events in memory if required. Having
 * buffered SAX events this filter can then fire them at a content handler.
 */
public abstract class BufferFilter extends AbstractXMLFilter {

    private SimpleEventListBuilder handler;

    /**
     * Called just after a pipeline has finished processing.
     *
     * @see stroom.pipeline.filter.XMLFilter#endProcessing()
     */
    @Override
    public void endProcessing() {
        try {
            super.endProcessing();
        } finally {
            // Make sure there is no further allocation of the buffer.
            handler = null;
        }
    }

    /**
     * This method tells filters that a stream has finished parsing so cleanup
     * can be performed.
     */
    @Override
    public void endStream() {
        try {
            super.endStream();
        } finally {
            // Make sure there is no further allocation of the buffer.
            handler = null;
        }
    }

    /**
     * Starts buffering SAX events.
     */
    public void startBuffer() {
        handler = new SimpleEventListBuilder();
    }

    /**
     * Stops buffering SAX events and fires all buffered events at the specified
     * content handler.
     *
     * @param filter The filter to fire buffered SAX events at.
     * @throws SAXException Not thrown.
     */
    public void stopBuffer(final XMLFilter filter) throws SAXException {
        if (handler != null) {
            handler.getEventList().fire(filter);
            handler = null;
        }
    }

    /**
     * Stops buffering SAX events and clears the buffer.
     */
    public void clearBuffer() {
        handler = null;
    }

    /**
     * Buffers a setDocumentLocator event if buffer is set to true.
     *
     * @param locator an object that can return the location of any SAX document
     *                event
     * @see stroom.pipeline.filter.AbstractXMLFilter#setDocumentLocator(org.xml.sax.Locator)
     */
    @Override
    public void setDocumentLocator(final Locator locator) {
        if (handler != null) {
            handler.setDocumentLocator(locator);
        } else {
            super.setDocumentLocator(locator);
        }
    }

    /**
     * Buffers a startDocument event if buffer is set to true.
     *
     * @throws SAXException Not thrown.
     * @see stroom.pipeline.filter.AbstractXMLFilter#startDocument()
     */
    @Override
    public void startDocument() throws SAXException {
        if (handler != null) {
            handler.startDocument();
        } else {
            super.startDocument();
        }
    }

    /**
     * Buffers a endDocument event if buffer is set to true.
     *
     * @throws SAXException Not thrown.
     * @see stroom.pipeline.filter.AbstractXMLFilter#endDocument()
     */
    @Override
    public void endDocument() throws SAXException {
        if (handler != null) {
            handler.endDocument();
        } else {
            super.endDocument();
        }
    }

    /**
     * Buffers a startPrefixMapping event if buffer is set to true.
     *
     * @param prefix the Namespace prefix being declared. An empty string is used
     *               for the default element namespace, which has no prefix.
     * @param uri    the Namespace URI the prefix is mapped to
     * @throws SAXException Not thrown.
     * @see stroom.pipeline.filter.AbstractXMLFilter#startPrefixMapping(java.lang.String,
     * java.lang.String)
     */
    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        if (handler != null) {
            handler.startPrefixMapping(prefix, uri);
        } else {
            super.startPrefixMapping(prefix, uri);
        }
    }

    /**
     * Buffers a endPrefixMapping event if buffer is set to true.
     *
     * @param prefix the prefix that was being mapped. This is the empty string
     *               when a default mapping scope ends.
     * @throws SAXException Not thrown.
     * @see stroom.pipeline.filter.AbstractXMLFilter#endPrefixMapping(java.lang.String)
     */
    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        if (handler != null) {
            handler.endPrefixMapping(prefix);
        } else {
            super.endPrefixMapping(prefix);
        }
    }

    /**
     * Buffers a startElement event if buffer is set to true.
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
        if (handler != null) {
            handler.startElement(uri, localName, qName, atts);
        } else {
            super.startElement(uri, localName, qName, atts);
        }
    }

    /**
     * Buffers a endElement event if buffer is set to true.
     *
     * @param uri       the Namespace URI, or the empty string if the element has no
     *                  Namespace URI or if Namespace processing is not being
     *                  performed
     * @param localName the local name (without prefix), or the empty string if
     *                  Namespace processing is not being performed
     * @param qName     the qualified XML name (with prefix), or the empty string if
     *                  qualified names are not available
     * @throws SAXException Not thrown.
     * @see stroom.pipeline.filter.AbstractXMLFilter#endElement(java.lang.String,
     * java.lang.String, java.lang.String)
     */
    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if (handler != null) {
            handler.endElement(uri, localName, qName);
        } else {
            super.endElement(uri, localName, qName);
        }
    }

    /**
     * Buffers a characters event if buffer is set to true.
     *
     * @param ch     An array of characters.
     * @param start  The starting position in the array.
     * @param length The number of characters to use from the array.
     * @throws SAXException Not thrown.
     * @see stroom.pipeline.filter.AbstractXMLFilter#characters(char[],
     * int, int)
     */
    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        if (handler != null) {
            handler.characters(ch, start, length);
        } else {
            super.characters(ch, start, length);
        }
    }

    /**
     * Buffers a ignorableWhitespace event if buffer is set to true.
     *
     * @param ch     the characters from the XML document
     * @param start  the start position in the array
     * @param length the number of characters to read from the array
     * @throws SAXException Not thrown.
     * @see stroom.pipeline.filter.AbstractXMLFilter#ignorableWhitespace(char[],
     * int, int)
     */
    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
        if (handler != null) {
            handler.ignorableWhitespace(ch, start, length);
        } else {
            super.ignorableWhitespace(ch, start, length);
        }
    }

    /**
     * Buffers a processingInstruction event if buffer is set to true.
     *
     * @param target the processing instruction target
     * @param data   the processing instruction data, or null if none was supplied.
     *               The data does not include any whitespace separating it from
     *               the target
     * @throws SAXException Not thrown.
     * @see stroom.pipeline.filter.AbstractXMLFilter#processingInstruction(java.lang.String,
     * java.lang.String)
     */
    @Override
    public void processingInstruction(final String target, final String data) throws SAXException {
        if (handler != null) {
            handler.processingInstruction(target, data);
        } else {
            super.processingInstruction(target, data);
        }
    }

    /**
     * Buffers a skippedEntity event if buffer is set to true.
     *
     * @param name the name of the skipped entity. If it is a parameter entity,
     *             the name will begin with '%', and if it is the external DTD
     *             subset, it will be the string "[dtd]"
     * @throws SAXException Not thrown.
     * @see stroom.pipeline.filter.AbstractXMLFilter#skippedEntity(java.lang.String)
     */
    @Override
    public void skippedEntity(final String name) throws SAXException {
        if (handler != null) {
            handler.skippedEntity(name);
        } else {
            super.skippedEntity(name);
        }
    }

    public SimpleEventList getEvents() {
        if (handler == null) {
            return null;
        }
        return (SimpleEventList) handler.getEventList();
    }
}
