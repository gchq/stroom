/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.server.filter;

import java.util.ArrayDeque;
import java.util.Deque;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.xml.event.simple.StartElement;
import stroom.xml.event.simple.StartPrefixMapping;

/**
 * Merges XML that has been split into separate XML instances.
 */
public class MergeFilter extends AbstractXMLFilter {
    private boolean started;
    private int depth;
    private StartElement root;
    private final Deque<StartPrefixMapping> prefixDeque = new ArrayDeque<StartPrefixMapping>();

    @Override
    public void startStream() {
        try {
            depth = 0;
        } finally {
            super.startStream();
        }
    }

    @Override
    public void endStream() {
        try {
            super.endStream();
        } finally {
            depth = 0;
        }
    }

    /**
     * Called by the pipeline when processing of a file is complete.
     *
     * @see stroom.pipeline.server.filter.AbstractXMLFilter#endProcessing()
     */
    @Override
    public void endProcessing() {
        try {
            if (started) {
                // End root element.
                if (root != null) {
                    super.endElement(root.getUri(), root.getLocalName(), root.getQName());
                }

                // End stored prefix mappings.
                while (!prefixDeque.isEmpty()) {
                    final StartPrefixMapping prefixMapping = prefixDeque.pop();
                    super.endPrefixMapping(prefixMapping.getPrefix());
                }

                // End the document.
                super.endDocument();
            }
        } catch (final SAXException e) {
            throw new ProcessException(e.getMessage(), e);
        } finally {
            super.endProcessing();
        }
    }

    /**
     * @throws SAXException
     *             Not thrown.
     *
     * @see stroom.pipeline.server.filter.AbstractXMLFilter#startDocument()
     */
    @Override
    public void startDocument() throws SAXException {
        // Only write one start document event.
        if (!started) {
            started = true;
            super.startDocument();
        }
    }

    /**
     * @throws SAXException
     *             Not thrown.
     *
     * @see stroom.pipeline.server.filter.AbstractXMLFilter#endDocument()
     */
    @Override
    public void endDocument() throws SAXException {
        // Deliberately suppressed.
    }

    /**
     * We want to suppress prefix mappings for first level elements that we see
     * again.
     */
    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        if (depth > 1) {
            // If we are deeper than the root element then treat prefix mappings
            // as normal.
            super.startPrefixMapping(prefix, uri);

        } else if (root == null) {
            // Store prefix mappings and output them if we haven't seen the root
            // element yet.
            prefixDeque.push(new StartPrefixMapping(prefix, uri));
            super.startPrefixMapping(prefix, uri);
        }
    }

    /**
     * Don't end prefix mappings if we are at the root element. Leave the end
     * processing method to do this.
     */
    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        if (depth > 1) {
            super.endPrefixMapping(prefix);
        }
    }

    /**
     * @param uri
     *            the Namespace URI, or the empty string if the element has no
     *            Namespace URI or if Namespace processing is not being
     *            performed
     * @param localName
     *            the local name (without prefix), or the empty string if
     *            Namespace processing is not being performed
     * @param qName
     *            the qualified name (with prefix), or the empty string if
     *            qualified names are not available
     * @param atts
     *            the attributes attached to the element. If there are no
     *            attributes, it shall be an empty Attributes object. The value
     *            of this object after startElement returns is undefined
     * @throws org.xml.sax.SAXException
     *             any SAX exception, possibly wrapping another exception
     *
     * @see #endElement
     * @see org.xml.sax.Attributes
     * @see org.xml.sax.helpers.AttributesImpl
     * @see stroom.pipeline.server.filter.AbstractXMLFilter#startElement(java.lang.String,
     *      java.lang.String, java.lang.String, org.xml.sax.Attributes)
     */
    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        depth++;

        if (depth > 1) {
            super.startElement(uri, localName, qName, atts);
        } else if (root == null) {
            root = new StartElement(uri, localName, qName, atts);
            super.startElement(uri, localName, qName, atts);
        }
    }

    /**
     * @param uri
     *            the Namespace URI, or the empty string if the element has no
     *            Namespace URI or if Namespace processing is not being
     *            performed
     * @param localName
     *            the local name (without prefix), or the empty string if
     *            Namespace processing is not being performed
     * @param qName
     *            the qualified XML name (with prefix), or the empty string if
     *            qualified names are not available
     * @throws org.xml.sax.SAXException
     *             any SAX exception, possibly wrapping another exception
     *
     * @see stroom.pipeline.server.filter.AbstractXMLFilter#endElement(java.lang.String,
     *      java.lang.String, java.lang.String)
     */
    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if (depth > 1) {
            super.endElement(uri, localName, qName);
        }
        depth--;
    }

    /**
     * @param ch
     *            the characters from the XML document
     * @param start
     *            the start position in the array
     * @param length
     *            the number of characters to read from the array
     * @throws org.xml.sax.SAXException
     *             any SAX exception, possibly wrapping another exception
     *
     * @see #ignorableWhitespace
     * @see org.xml.sax.Locator
     * @see stroom.pipeline.server.filter.AbstractXMLFilter#characters(char[],
     *      int, int)
     */
    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        if (depth > 0) {
            super.characters(ch, start, length);
        }
    }

    /**
     * @param ch
     *            the characters from the XML document
     * @param start
     *            the start position in the array
     * @param length
     *            the number of characters to read from the array
     * @throws org.xml.sax.SAXException
     *             any SAX exception, possibly wrapping another exception
     *
     * @see #characters
     * @see stroom.pipeline.server.filter.AbstractXMLFilter#ignorableWhitespace(char[],
     *      int, int)
     */
    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
        if (depth > 0) {
            super.ignorableWhitespace(ch, start, length);
        }
    }
}
