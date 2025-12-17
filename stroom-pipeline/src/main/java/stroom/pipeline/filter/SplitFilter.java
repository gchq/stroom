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

import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.state.LocationHolder;
import stroom.pipeline.xml.event.Event;
import stroom.pipeline.xml.event.simple.StartElement;
import stroom.pipeline.xml.event.simple.StartPrefixMapping;
import stroom.svg.shared.SvgImage;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * Splits a single XML instance into separate XML instances that contain
 * elements at a certain split depth. The parent element structure is replicated
 * for all split out instances. A split instance will fire start and end
 * document SAX events before and after a split out instance.
 */
@ConfigurableElement(
        type = "SplitFilter",
        description = """
                Splits multi-record source data into smaller groups of records prior to delivery to an XSLT.
                This allows the XSLT to process data more efficiently than loading a potentially huge input \
                stream into memory.
                """,
        category = Category.FILTER,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS},
        icon = SvgImage.PIPELINE_SPLIT)
public class SplitFilter extends AbstractXMLFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SplitFilter.class);

    /**
     * The default depth to split XML. Set to 2 as we normally split on second
     * level elements.
     */
    private static final int DEFAULT_SPLIT_DEPTH = 2;

    /**
     * The default number of elements at the split depth to count before we
     * split.
     */
    private static final int DEFAULT_SPLIT_COUNT = 10000;

    /**
     * A stack of DOM elements. Elements are put onto the stack within
     * startElement() and popped within endElement().
     */
    private Event[] events = new Event[10];

    /**
     * The current working length of the elements array.
     */
    private int length;

    /**
     * The length of the events array when the root element is added.
     */
    private int afterRoot;

    /**
     * We don't want any changes to happen to the root path of the array once it
     * has been set.
     */
    private boolean inRoot;

    /**
     * Determines if events should be added/removed from the array of passed
     * directly to child filters.
     */
    private boolean buffer;

    /**
     * This defines the depth at which to split the XML. It is zero based (root
     * element = 0). A split depth of 0 will not split the XML as the root
     * element is at depth 0.
     */
    private int splitDepth = DEFAULT_SPLIT_DEPTH;

    /**
     * The maximum number of elements at the split depth to count before we
     * split. If the split count is 0 then the XML will not be split.
     */
    private int splitCount = DEFAULT_SPLIT_COUNT;

    /**
     * Keeps track of the current element depth for performing comparisons
     * against split depth.
     */
    private int depth;

    /**
     * The current split element count.
     */
    private int count;

    /**
     * Used to determine whether any events have been passed on to the child
     * filter. If none have been passed on when this filter received an end
     * document event then the start and end document events shall be passed to
     * the child filter to ensure the rest of the pipeline does some processing.
     */
    private boolean hasFiredEvents;

    private final LocationHolder locationHolder;

    private int lastEndElementLineNo;
    private int lastEndElementColNo;

    public SplitFilter() {
        this.locationHolder = null;
    }

    @Inject
    SplitFilter(final LocationHolder locationHolder) {
        this.locationHolder = locationHolder;
    }

    @Override
    public void endProcessing() {
        super.endProcessing();
    }

    /**
     * This method tells filters that a stream is about to be parsed so that
     * they can complete any setup necessary.
     */
    @Override
    public void startStream() {
        buffer = true;
        hasFiredEvents = false;
        inRoot = false;
        length = 0;
        afterRoot = -1;
        depth = 0;
        count = 0;
        if (locationHolder != null) {
            locationHolder.reset();
        }

        super.startStream();
    }

    @Override
    public void setDocumentLocator(final Locator locator) {
        super.setDocumentLocator(locator);
        if (locationHolder != null) {
            locationHolder.setDocumentLocator(locator, splitCount);
        }
    }

    /**
     * This method is intentionally empty as the <code>startElement()</code> SAX
     * event will fire <code>startDocument()</code> when the element depth
     * equals the split depth.
     *
     * @throws org.xml.sax.SAXException The client may throw an exception during processing.
     * @see stroom.pipeline.filter.AbstractXMLFilter#startDocument()
     */
    @Override
    public void startDocument() throws SAXException {
    }

    /**
     * This method is intentionally empty as the <code>endElement()</code> SAX
     * event will fire <code>endDocument()</code> when the element depth equals
     * the split depth.
     *
     * @throws org.xml.sax.SAXException The client may throw an exception during processing.
     * @see stroom.pipeline.filter.AbstractXMLFilter#startDocument()
     */
    @Override
    public void endDocument() throws SAXException {
        fireRemainingEvents();
    }

    /**
     * Fired when a prefix mapping is in scope.
     *
     * @param prefix The prefix.
     * @param uri    The URI of the prefix.
     * @throws SAXException Not thrown.
     * @see stroom.pipeline.filter.AbstractXMLFilter#startPrefixMapping(java.lang.String,
     * java.lang.String)
     */
    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        if (!buffer) {
            super.startPrefixMapping(prefix, uri);
        } else if (inRoot || afterRoot == -1) {
            ensureArraySize();
            events[length++] = new StartPrefixMapping(prefix, uri);
        }
    }

    /**
     * Fired when a prefix mapping moves out of scope.
     *
     * @param prefix The prefix.
     * @throws SAXException Not thrown.
     * @see stroom.pipeline.filter.AbstractXMLFilter#endPrefixMapping(java.lang.String)
     */
    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        if (!buffer) {
            super.endPrefixMapping(prefix);
        } else if (inRoot || afterRoot == -1) {
            length--;
        }
    }

    /**
     * <p>
     * This method stores the start element if the element depth is less than
     * the split depth. If the element depth is the same as the split depth then
     * it will fire all stored start events and begin output of all child
     * elements.
     * <p>
     *
     * @param uri       The element's Namespace URI, or the empty string.
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
        depth++;

        // Mark the start location on every start element with a depth lower (closer to root)
        // than our split depth so we can get the start position to be just before
        // our record start element
        if (depth < splitDepth) {
            LOGGER.trace("startElement({}), depth: {}", localName, depth);
            if (locationHolder != null) {
                locationHolder.markStartLocation();
            }
        } else if (depth == splitDepth) {
            LOGGER.trace("startElement({}), depth: {}", localName, depth);
            buffer = false;

            // We are going to fire some events at the child filter so set this
            // flag so that start and end document events aren't fired again
            // when end document is called.
            hasFiredEvents = true;

            // Increment the split count.
            count++;

            if (count == 1) {
                // We should only fire all buffered SAX events if the current
                // count is 1.
                super.startDocument();
                fireStartEvents(0, length);

            } else {
                // Otherwise just fire buffered SAX events after the root
                // element.
                fireStartEvents(afterRoot, length);
            }
        }

        if (!buffer) {
            super.startElement(uri, localName, qName, atts);
        } else if (inRoot || afterRoot == -1) {
            ensureArraySize();
            events[length++] = new StartElement(uri, localName, qName, atts);
        }

        // If this is the root element then remember the position in the
        // stack for this element.
        if (depth == 1) {
            if (afterRoot == -1) {
                afterRoot = length;
            }

            inRoot = true;
        }
    }

    /**
     * <p>
     * This method outputs an end element if the depth is greater than the split
     * depth. If the element depth is the same as the split depth then it will
     * fire all stored start element events as end element events and fire an
     * end document event. If the depth is less than the split depth then it
     * will pop a start element from the stack.
     * <p>
     *
     * @param uri       The element's Namespace URI, or the empty string.
     * @param localName The element's local name, or the empty string.
     * @param qName     The element's qualified (prefixed) name, or the empty string.
     * @throws org.xml.sax.SAXException The client may throw an exception during processing.
     * @see stroom.pipeline.filter.AbstractXMLFilter#endElement(java.lang.String,
     * java.lang.String, java.lang.String)
     */
    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if (depth == 1) {
            inRoot = false;
        }

        if (!buffer) {
            super.endElement(uri, localName, qName);
        } else if (inRoot || afterRoot == -1) {
            length--;
        }

        if (depth == splitDepth) {
            if (locationHolder != null) {
                // The location reported by the locator held in locationHolder may not be the same as the actual
                // location of the (start|end)Element. It depends on how the parser in question reads the source
                // data and closely tied the locator is to the parser.  For XML parsing it will be accurate. The
                // fragment parser locations are partially messed up by the reading of the entity. The data splitter
                // is ok if DSLocator is used. Json parsing is completely wrong as large chunks of the stream
                // are read at once.
                locationHolder.storeLocation();
            }

            buffer = true;

            if (count == splitCount || afterRoot < 0) {
                // Reset the element count if it equals the split count.
                count = 0;
                // Fire all end events.
                fireEndEvents(length, 0);

                super.endDocument();
            } else {
                // If the count has not yet reached the split count then we only
                // need to fire end SAX events down to the root element.
                fireEndEvents(length, afterRoot);
            }
        }

        // Mark the start location on every end element so when storeLocation is called
        // it will get the range from the end of the element just before this record to the end
        // of this record.  This is because the locator gets the location of '<myElement>'.
        if (depth <= splitDepth) {
            LOGGER.trace("endElement({}), depth: {}", localName, depth);
            if (locationHolder != null) {
                locationHolder.markStartLocation();
            }
        }

        depth--;
    }

    /**
     * This method will fire character events if the current element depth is
     * greater than the split depth, i.e. no content is output for elements that
     * are lower in the element hierarchy than the split depth.
     *
     * @param ch     An array of characters.
     * @param start  The starting position in the array.
     * @param length The number of characters to use from the array.
     * @throws org.xml.sax.SAXException The client may throw an exception during processing.
     * @see stroom.pipeline.filter.AbstractXMLFilter#characters(char[],
     * int, int)
     */
    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        if (!buffer) {
            super.characters(ch, start, length);
        }
    }

    private void fireStartEvents(final int from, final int to) throws SAXException {
        // Fire necessary start events.
        for (int i = from; i < to; i++) {
            events[i].fire(getFilter());
        }
    }

    private void fireEndEvents(final int from, final int to) throws SAXException {
        // Fire necessary end events.
        for (int i = from - 1; i >= to; i--) {
            final Event event = events[i];

            if (event.isStartElement()) {
                final StartElement elem = (StartElement) event;
                super.endElement(elem.getUri(), elem.getLocalName(), elem.getQName());

            } else if (event.isStartPrefixMapping()) {
                final StartPrefixMapping mapping = (StartPrefixMapping) event;
                super.endPrefixMapping(mapping.getPrefix());
            }
        }
    }

    /**
     * Ideally we would only call this method on endProcessing() which would
     * allow us to supply the XSLT filter with events that span multiple streams
     * within an aggregate and so improve translation performance, however this
     * is not possible as some translations fetch meta and context data during
     * translation which is no longer valid when endProcessing() is called.
     * Instead it will still be necessary for endDocument() to call this method
     * instead unless we can figure out a way of the XSLT filter knowing which
     * sub stream it is currently processing and opening the appropriate one.
     */
    private void fireRemainingEvents() throws SAXException {
        if (!hasFiredEvents) {
            super.startDocument();
            fireStartEvents(0, length);
            fireEndEvents(length, 0);
            super.endDocument();

        } else if (count > 0) {
            // Fire all end events as the XML will not have been closed off if
            // currentCount > 0.
            fireEndEvents(afterRoot, 0);
            super.endDocument();
        }
    }

    /**
     * Make sure the event array is going to be big enough to hold the item that
     * is just about to be added.
     */
    private void ensureArraySize() {
        if (events.length == length) {
            final Event[] tmp = new Event[events.length * 2];
            System.arraycopy(events, 0, tmp, 0, events.length);
            events = tmp;
        }
    }

    @PipelineProperty(
            description = "The depth of XML elements to split at.",
            defaultValue = "1",
            displayPriority = 1)
    public void setSplitDepth(final int splitDepth) {
        // Add a fudge in here to cope with legacy depth being 0 based.
        this.splitDepth = splitDepth + 1;
    }

    @PipelineProperty(
            description = "The number of elements at the split depth to count before the XML is split.",
            defaultValue = "10000",
            displayPriority = 2)
    public void setSplitCount(final int splitCount) {
        this.splitCount = splitCount;
    }

    @PipelineProperty(
            description = "Should this split filter store processing locations.",
            defaultValue = "true",
            displayPriority = 3)
    public void setStoreLocations(final boolean storeLocations) {
        if (locationHolder != null) {
            locationHolder.setStoreLocations(storeLocations);
        }
    }

    public int getSplitDepth() {
        // Add a fudge in here to cope with legacy depth being 0 based.
        return splitDepth - 1;
    }
}
