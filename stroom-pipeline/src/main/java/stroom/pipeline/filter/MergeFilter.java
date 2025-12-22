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

import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.xml.event.simple.StartElement;
import stroom.pipeline.xml.event.simple.StartPrefixMapping;
import stroom.svg.shared.SvgImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Merges XML that has been split into separate XML instances.
 */
@ConfigurableElement(
        type = "MergeFilter",
        description = """
                 Merges XML that has been split into separate XML instances.
                """,
        category = Category.FILTER,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS},
        icon = SvgImage.PIPELINE_MERGE)
public class MergeFilter extends AbstractXMLFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SplitFilter.class);
    private final Deque<StartPrefixMapping> prefixDeque = new ArrayDeque<>();
    private boolean started;
    private StartElement root;

    private int depth;
    private int mergeDepth;
    private boolean mergeContainerStarted = false;
    private String mergeContainerURI;
    private String mergeContainerLocalName;
    private String mergeContainerQName;

    private boolean firstOccurrence = true;

    @Override
    public void startStream() {
        try {
            depth = 0;
            firstOccurrence = true;
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
     * @see stroom.pipeline.filter.AbstractXMLFilter#endProcessing()
     */
    @Override
    public void endProcessing() {
        try {
            if (started) {
                // End root element.
                if (root != null) {
                    System.out.println("ending root element: " + root.getLocalName());
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
            throw ProcessException.wrap(e);
        } finally {
            super.endProcessing();
        }
    }

    /**
     * @throws SAXException Not thrown.
     * @see stroom.pipeline.filter.AbstractXMLFilter#startDocument()
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
     * @throws SAXException Not thrown.
     * @see stroom.pipeline.filter.AbstractXMLFilter#endDocument()
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
     * @see stroom.pipeline.filter.AbstractXMLFilter#startElement(java.lang.String,
     * java.lang.String, java.lang.String, org.xml.sax.Attributes)
     */

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        depth++;
        LOGGER.debug("Start Element: " + localName + ", Depth: " + depth + ": MergeStarted: " + mergeContainerStarted);

        if (depth == mergeDepth) {
            mergeContainerStarted = true;
            mergeContainerURI = uri;
            mergeContainerLocalName = localName;
            mergeContainerQName = qName;
            if (firstOccurrence) {
                super.startElement(uri, localName, qName, atts);
                firstOccurrence = false;
            }
        } else if (depth > mergeDepth && mergeContainerStarted) {
            LOGGER.debug("Merging Element: " + localName);
            super.startElement(uri, localName, qName, atts);
        } else {
            // there should be a case in here to handle when an Element == mergeDepth but != mergeContainerLocalName
            // TODO: Handle cases where there may be different elements at
            //  the same mergeDepth that we need to handle somehow
            super.startElement(uri, localName, qName, atts);
        }
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {

        LOGGER.debug("End Element: " + localName + ", Depth: " + depth + ": MergeStarted: " + mergeContainerStarted);

        if (depth > mergeDepth && mergeContainerStarted) {
            LOGGER.debug("Ending Merge Element: " + localName);
            super.endElement(uri, localName, qName);
            // this may be an issue where merge depth is 0
        } else if (depth == mergeDepth - 1 && mergeContainerStarted) {
            LOGGER.debug("Adding final EndElement: " + localName);
            super.endElement(mergeContainerURI, mergeContainerLocalName, mergeContainerQName);
            super.endElement(uri, localName, qName);
            mergeContainerStarted = false;
        } else if (mergeContainerStarted
                   && uri.equals(mergeContainerURI)
                   && localName.equals(mergeContainerLocalName)
                   && qName.equals(mergeContainerQName)) {
            // Do nothing.
            LOGGER.debug("Skipping over Element: " + localName);
        } else {
            super.endElement(uri, localName, qName);
        }
        depth--;
    }

    /**
     * @param ch     the characters from the XML document
     * @param start  the start position in the array
     * @param length the number of characters to read from the array
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     * @see #ignorableWhitespace
     * @see org.xml.sax.Locator
     * @see stroom.pipeline.filter.AbstractXMLFilter#characters(char[],
     * int, int)
     */
    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        if (depth > 0) {
            super.characters(ch, start, length);
        }
    }

    /**
     * @param ch     the characters from the XML document
     * @param start  the start position in the array
     * @param length the number of characters to read from the array
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     * @see #characters
     * @see stroom.pipeline.filter.AbstractXMLFilter#ignorableWhitespace(char[],
     * int, int)
     */
    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
        if (depth > 0) {
            super.ignorableWhitespace(ch, start, length);
        }
    }

    @PipelineProperty(
            description = "The depth of XML elements to merge at.",
            defaultValue = "1",
            displayPriority = 1)
    public void setMergeDepth(final int mergeDepth) {
        // Add a fudge in here to cope with legacy depth being 0 based.
        this.mergeDepth = mergeDepth;
    }
}
