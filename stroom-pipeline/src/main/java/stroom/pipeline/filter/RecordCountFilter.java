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
import stroom.pipeline.state.Incrementor;
import stroom.pipeline.state.RecordCount;
import stroom.pipeline.state.RecordCountService;
import stroom.svg.shared.SvgImage;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * A SAX filter used to count the number of first level elements in an XML
 * instance. The first level elements are assumed to be records in the context
 * of event processing.
 */
@ConfigurableElement(
        type = "RecordCountFilter",
        category = Category.FILTER,
        description = """
                Counts events/records in the stream.
                An event/record is taken to be an XML element that is at the first level below the \
                root element, i.e. for 'event-logging:3' XML this means the `<Event>` element.""",
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS},
        icon = SvgImage.PIPELINE_RECORD_COUNT)
public class RecordCountFilter extends AbstractXMLFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecordCountFilter.class);
    private static final int LOG_COUNT = 10000;

    /**
     * The default depth of records. Set to 2 as we normally count second
     * level elements.
     */
    private static final int DEFAULT_RECORD_DEPTH = 2;

    private final RecordCountService recordCountService;
    private final RecordCount recordCount;

    /**
     * This defines the depth at which to count records. It is zero based (root
     * element = 0).
     */
    private int recordDepth = DEFAULT_RECORD_DEPTH;

    private boolean countRead = true;
    private int depth = 0;
    private long logCounter = 0;

    private Incrementor incrementor = () -> {
        // Do nothing.
    };

    @Inject
    public RecordCountFilter(final RecordCountService recordCountService,
                             final RecordCount recordCount) {
        this.recordCountService = recordCountService;
        this.recordCount = recordCount;
    }

    /**
     * @see stroom.pipeline.filter.AbstractXMLFilter#startProcessing()
     */
    @Override
    public void startProcessing() {
        try {
            recordCount.setStartMs(System.currentTimeMillis());
            if (countRead) {
                incrementor = () -> {
                    recordCount.getReadIncrementor().increment();
                    recordCountService.getReadIncrementor().increment();
                };
            } else {
                incrementor = () -> {
                    recordCount.getWriteIncrementor().increment();
                    recordCountService.getWriteIncrementor().increment();
                };
            }
        } finally {
            super.startProcessing();
        }
    }

    /**
     * This method tells filters that a stream is about to be parsed so that
     * they can complete any setup necessary.
     */
    @Override
    public void startStream() {
        try {
            depth = 0;
        } finally {
            super.startStream();
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
        super.startElement(uri, localName, qName, atts);

        depth++;

        if (depth == recordDepth) {
            // This is a first level element.
            incrementor.increment();

            if (LOGGER.isDebugEnabled()) {
                logCounter++;
                if (logCounter % LOG_COUNT == 0) {
                    if (countRead) {
                        LOGGER.debug("Records read = " + logCounter);
                    } else {
                        LOGGER.debug("Records written = " + logCounter);
                    }
                }
            }
        }
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

    /**
     * @param countRead Sets whether we are counting records read or records written.
     */
    @PipelineProperty(
            description = "Is this filter counting records read or records written?",
            defaultValue = "true",
            displayPriority = 1)
    public void setCountRead(final boolean countRead) {
        this.countRead = countRead;
    }

    @PipelineProperty(
            description = "The depth of XML elements to count records.",
            defaultValue = "1",
            displayPriority = 2)
    public void setRecordDepth(final int recordDepth) {
        // Add a fudge in here to cope with legacy depth being 0 based.
        this.recordDepth = recordDepth + 1;
    }
}
