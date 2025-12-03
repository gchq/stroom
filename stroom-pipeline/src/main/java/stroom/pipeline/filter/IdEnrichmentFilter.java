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

import stroom.meta.shared.Meta;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.state.IdEnrichmentExpectedIds;
import stroom.pipeline.state.MetaHolder;
import stroom.svg.shared.SvgImage;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A SAX filter used to count the number of first level elements in an XML
 * instance. The first level elements are assumed to be records in the context
 * of event processing.
 */
@ConfigurableElement(
        type = "IdEnrichmentFilter",
        displayValue = "ID Enrichment Filter",
        category = Category.FILTER,
        description = """
                Adds the attributes 'StreamId' and 'EventId' to the 'event' element to enrich the event \
                with its ordinal number in the stream and the ID of the stream that it belongs to.
                ID enrichment is required to be able to index events as it provides them with an ID that is \
                unique within Stroom.
                It assumes that an record/event is an XML element at the first level below the root element, i.e. \
                for 'event-logging:3' XML this means the `<Event>` element.""",
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.VISABILITY_STEPPING,
                PipelineElementType.ROLE_MUTATOR},
        icon = SvgImage.PIPELINE_ID)
public class IdEnrichmentFilter extends AbstractXMLFilter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IdEnrichmentFilter.class);

    private static final String URI = "";
    private static final String STRING = "string";
    private static final String STREAM_ID = "StreamId";
    private static final String EVENT_ID = "EventId";

    private final MetaHolder metaHolder;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final IdEnrichmentExpectedIds idEnrichmentExpectedIds;

    private int depth;
    private long count;

    @Inject
    public IdEnrichmentFilter(final MetaHolder metaHolder,
                              final ErrorReceiverProxy errorReceiverProxy,
                              final IdEnrichmentExpectedIds idEnrichmentExpectedIds) {
        this.metaHolder = metaHolder;
        this.errorReceiverProxy = errorReceiverProxy;
        this.idEnrichmentExpectedIds = idEnrichmentExpectedIds;
    }

    /**
     * This method tells filters that a stream is about to be parsed so that
     * they can complete any setup necessary.
     */
    @Override
    public void startStream() {
        try {
            if (idEnrichmentExpectedIds.getStreamId() == null) {
                final Meta meta = metaHolder.getMeta();
                if (meta != null) {
                    idEnrichmentExpectedIds.setStreamId(meta.getId());
                } else {
                    final String msg = "No stream set in stream holder";
                    errorReceiverProxy
                            .log(Severity.WARNING, null, getElementId(), msg, ProcessException.create(msg));
                }
            }
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
        Attributes newAtts = atts;

        depth++;

        if (depth == 2) {
            // Modify the attributes if this is an event element so that a
            // unique ID is inserted.
            final Long streamId = idEnrichmentExpectedIds.getStreamId();
            final long[] eventIds = idEnrichmentExpectedIds.getEventIds();
            if (streamId != null) {
                // This is a first level element.
                count++;

                final String eventId;
                // If we are using this is search result output then we need to
                // get event ids from a list.
                if (eventIds != null) {
                    // Check we haven't found more events than we are expecting to extract.
                    if (count > eventIds.length) {
                        final String msg = "Expected " +
                                eventIds.length +
                                " events but extracted " +
                                count +
                                " from stream " +
                                streamId;

                        LOGGER.debug(() -> msg);
                        LOGGER.trace(() -> Arrays.toString(eventIds));
                        errorReceiverProxy.log(Severity.WARNING, null, getElementId(), msg, null);

                        // Double check for duplicated ids.
                        final Set<Long> duplicateSet = new HashSet<>();
                        boolean duplicate = false;
                        for (final long id : eventIds) {
                            if (!duplicateSet.add(id)) {
                                duplicate = true;
                                break;
                            }
                        }
                        if (duplicate) {
                            final String err = "Duplicate id found in event id list: " + Arrays.toString(eventIds);
                            LOGGER.error(() -> err);
                            errorReceiverProxy.log(Severity.ERROR, null, getElementId(), err, null);
                        }
                    }

                    final int index = (int) (count - 1);
                    eventId = String.valueOf(eventIds[index]);
                } else {
                    eventId = String.valueOf(count);
                }

                final AttributesImpl idAtts = new AttributesImpl(newAtts);

                // Remove any existing id attribute.
                int index = idAtts.getIndex(STREAM_ID);
                if (index != -1) {
                    idAtts.removeAttribute(index);
                }
                index = idAtts.getIndex(EVENT_ID);
                if (index != -1) {
                    idAtts.removeAttribute(index);
                }

                // Add the ids to the element.
                idAtts.addAttribute(URI, STREAM_ID, STREAM_ID, STRING, String.valueOf(streamId));
                idAtts.addAttribute(URI, EVENT_ID, EVENT_ID, STRING, eventId);

                newAtts = idAtts;
            }
        }

        super.startElement(uri, localName, qName, newAtts);
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
        super.endElement(uri, localName, qName);
        depth--;
    }
}
