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

import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.ErrorStatistics;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.xml.event.Event;
import stroom.pipeline.xml.event.simple.SimpleEventList;
import stroom.pipeline.xml.event.simple.StartElement;
import stroom.pipeline.xml.event.simple.StartPrefixMapping;
import stroom.svg.shared.SvgImage;

import jakarta.inject.Inject;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.util.List;

/**
 * Filters out records that have raised an error or warning during processing.
 */
@ConfigurableElement(
        type = "RecordOutputFilter",
        category = Category.FILTER,
        description = """
                Filters out records/events that have raised an Error or Fatal Error during processing.
                If all records/events have raised at least an Error then no XML events will be output.
                It assumes that an record/event is an XML element at the first level below the root element, i.e. \\
                for 'event-logging:3' XML this means the `<Event>` element.""\",
                """,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS},
        icon = SvgImage.PIPELINE_RECORD_OUTPUT)
public class RecordOutputFilter extends BufferFilter {

    private final ErrorReceiverProxy errorReceiverProxy;

    private int depth;
    private long count;

    private SimpleEventList rootEvents;
    private boolean hasOutputRoot;

    @Inject
    public RecordOutputFilter(final ErrorReceiverProxy errorReceiverProxy) {
        this.errorReceiverProxy = errorReceiverProxy;
    }

    @Override
    public void startDocument() throws SAXException {
        try {
            hasOutputRoot = false;
            depth = 0;
            startBuffer();
        } finally {
            super.startDocument();
        }
    }

    @Override
    public void endDocument() throws SAXException {
        try {
            super.endDocument();
        } finally {
            clearBuffer();
            depth = 0;

            if (hasOutputRoot) {
                hasOutputRoot = false;

                // Fire end events to match the root start events.
                final List<Event> events = rootEvents.getEvents();
                for (int i = events.size() - 1; i >= 0; i--) {
                    final Event event = events.get(i);
                    if (event.isStartElement()) {
                        final StartElement startElement = (StartElement) event;
                        super.endElement(startElement.getUri(), startElement.getLocalName(), startElement.getQName());
                    } else if (event.isStartPrefixMapping()) {
                        final StartPrefixMapping startPrefixMapping = (StartPrefixMapping) event;
                        super.endPrefixMapping(startPrefixMapping.getPrefix());
                    } else if (event.isStartDocument()) {
                        super.endDocument();
                    }
                }
            }
        }
    }

    /**
     * Fired on start element.
     *
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
        depth++;

        if (depth == 2) {
            // Store root events for output later if necessary.
            if (rootEvents == null) {
                rootEvents = getEvents();
            }

            // Clear the buffer and prepare to store more SAX events.
            startBuffer();
        }

        super.startElement(uri, localName, qName, atts);
    }

    /**
     * Fired on an end element.
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
        if (depth == 2) {
            super.endElement(uri, localName, qName);

            // Check to see if the record is ok?
            boolean recordOk = true;
            if (errorReceiverProxy.getErrorReceiver() instanceof final ErrorStatistics errorStatistics) {
                recordOk = errorStatistics.checkRecord(count) == 0;
            }

            // If the record is ok then fire the contents of the buffer at the
            // next filter in the chain.
            if (recordOk) {
                if (!hasOutputRoot) {
                    hasOutputRoot = true;
                    rootEvents.fire(getFilter());
                }

                stopBuffer(getFilter());

                // We have output a record so increase the record count.
                count++;
            }

            // Clear the buffer and prepare to store more SAX events.
            startBuffer();

        } else if (depth > 2) {
            super.endElement(uri, localName, qName);
        }

        depth--;
    }
}
