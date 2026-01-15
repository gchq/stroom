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

package stroom.pipeline.util;

import stroom.pipeline.xml.event.Event;
import stroom.pipeline.xml.event.simple.Characters;
import stroom.pipeline.xml.event.simple.EndDocument;
import stroom.pipeline.xml.event.simple.EndElement;
import stroom.pipeline.xml.event.simple.EndPrefixMapping;
import stroom.pipeline.xml.event.simple.IgnorableWhitespace;
import stroom.pipeline.xml.event.simple.ProcessingInstruction;
import stroom.pipeline.xml.event.simple.SetDocumentLocator;
import stroom.pipeline.xml.event.simple.SkippedEntity;
import stroom.pipeline.xml.event.simple.StartDocument;
import stroom.pipeline.xml.event.simple.StartElement;
import stroom.pipeline.xml.event.simple.StartPrefixMapping;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.XMLFilterImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * This filter is used to buffer SAX events in memory if required. Having
 * buffered SAX events this filter can then fire them at a content handler.
 */
public abstract class BufferFilter extends XMLFilterImpl {

    private List<Event> events;
    private boolean buffer;

    /**
     * Starts buffering SAX events.
     */
    public void startBuffer() {
        buffer = true;
        events = new ArrayList<>();
    }

    /**
     * Stops buffering SAX events and fires all buffered events at the specified
     * content handler.
     */
    public void stopBuffer(final ContentHandler contentHandler) throws SAXException {
        buffer = false;

        for (final Event event : events) {
            event.fire(contentHandler);
        }

        events = null;
    }

    /**
     * Stops buffering SAX events and clears the buffer.
     */
    public void clearBuffer() {
        buffer = false;
        events = null;
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
        if (buffer) {
            events.add(new SetDocumentLocator(locator));
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
        if (buffer) {
            events.add(new StartDocument());
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
        if (buffer) {
            events.add(new EndDocument());
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
        if (buffer) {
            events.add(new StartPrefixMapping(prefix, uri));
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
        if (buffer) {
            events.add(new EndPrefixMapping(prefix));
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
        if (buffer) {
            events.add(new StartElement(uri, localName, qName, atts));
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
        if (buffer) {
            events.add(new EndElement(uri, localName, qName));
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
        if (buffer) {
            events.add(new Characters(ch, start, length));
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
        if (buffer) {
            events.add(new IgnorableWhitespace(ch, start, length));
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
        if (buffer) {
            events.add(new ProcessingInstruction(target, data));
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
        if (buffer) {
            events.add(new SkippedEntity(name));
        } else {
            super.skippedEntity(name);
        }
    }
}
