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

package stroom.analytics.impl;

import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.filter.XMLFilter;
import stroom.util.shared.ElementId;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.List;
import java.util.Locale;

public class DetectionWriter implements DetectionConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DetectionWriter.class);

    private static final String XSI_SCHEMA_LOCATION = "xsi:schemaLocation";
    private static final String SCHEMA_LOCATION = "schemaLocation";
    private static final String XMLNS_XSI = "xmlns:xsi";
    private static final String XML_TYPE_STRING = "string";
    private static final String XMLNS = "xmlns";
    private static final String XMLSCHEMA_INSTANCE = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String XSI = "xsi";
    private static final String EMPTY_STRING = "";


    //    private static final String XML_ATTRIBUTE_VERSION = "version";
    private static final String NAMESPACE = "detection:1";
    private static final String LOCATION = "file://detection-v1.1.xsd";
//    private static final String VERSION = "2.0";

    private static final Attributes EMPTY_ATTS = new AttributesImpl();
    private static final AttributesImpl ROOT_ATTS = new AttributesImpl();

    static {
        ROOT_ATTS.addAttribute(EMPTY_STRING, XMLNS, XMLNS, XML_TYPE_STRING, NAMESPACE);
        ROOT_ATTS.addAttribute(EMPTY_STRING, XSI, XMLNS_XSI, XML_TYPE_STRING, XMLSCHEMA_INSTANCE);
        ROOT_ATTS.addAttribute(XMLSCHEMA_INSTANCE, SCHEMA_LOCATION, XSI_SCHEMA_LOCATION, XML_TYPE_STRING,
                NAMESPACE + " " + LOCATION);
//        ROOT_ATTS.addAttribute(EMPTY_STRING, XML_ATTRIBUTE_VERSION, XML_ATTRIBUTE_VERSION, XML_TYPE_STRING, VERSION);
    }

    private static final String DETECTIONS = "detections";
    private static final String DETECTION = "detection";
    private static final String DETECT_TIME = "detectTime";
    private static final String DETECTOR_NAME = "detectorName";
    private static final String DETECTOR_UUID = "detectorUuid";
    private static final String DETECTOR_VERSION = "detectorVersion";
    private static final String DETECTOR_ENVIRONMENT = "detectorEnvironment";
    private static final String HEADLINE = "headline";
    private static final String DETAILED_DESCRIPTION = "detailedDescription";
    private static final String FULL_DESCRIPTION = "fullDescription";
    private static final String DETECTION_UNIQUE_ID = "detectionUniqueId";
    private static final String DETECTION_REVISION = "detectionRevision";
    private static final String DEFUNCT = "defunct";
    private static final String EXECUTION_SCHEDULE = "executionSchedule";
    private static final String EXECUTION_TIME = "executionTime";
    private static final String EFFECTIVE_EXECUTION_TIME = "effectiveExecutionTime";
    private static final String VALUE = "value";
    private static final String NAME = "name";
    private static final String LINKED_EVENTS = "linkedEvents";
    private static final String LINKED_EVENT = "linkedEvent";
    private static final String STROOM = "stroom";
    private static final String STREAM_ID = "streamId";
    private static final String EVENT_ID = "eventId";


    private final ErrorReceiverProxy errorReceiverProxy;
    private XMLFilter handler;
    private boolean written;

    @Inject
    public DetectionWriter(final ErrorReceiverProxy errorReceiverProxy) {
        this.errorReceiverProxy = errorReceiverProxy;
    }

    public void setHandler(final XMLFilter handler) {
        this.handler = handler;
    }

    @Override
    public synchronized void start() {
    }

    @Override
    public synchronized void end() {
        try {
            if (written) {
                writeEndElement(DETECTIONS);
                handler.endDocument();
                handler.endStream();
                handler.endProcessing();
            }
        } catch (final SAXException e) {
            logError(e.getMessage(), e);
        }
    }

    private void logError(final String message, final Exception e) {
        LOGGER.error(message, e);
        errorReceiverProxy.log(Severity.ERROR, null,
                new ElementId(getClass().getSimpleName()), message, e);
    }

    @Override
    public synchronized void accept(final Detection detection) {
        try {
            if (!written) {
                handler.startProcessing();
                handler.startStream();
                handler.startDocument();
                handler.startPrefixMapping(EMPTY_STRING, NAMESPACE);
                handler.startPrefixMapping(XSI, XMLSCHEMA_INSTANCE);
                writeStartElement(DETECTIONS, ROOT_ATTS);
                written = true;
            }

            writeStartElement(DETECTION);
            writeOptionalDataElement(DETECT_TIME, detection.getDetectTime());
            writeOptionalDataElement(DETECTOR_NAME, detection.getDetectorName());
            writeOptionalDataElement(DETECTOR_UUID, detection.getDetectorUuid());
            writeOptionalDataElement(DETECTOR_VERSION, detection.getDetectorVersion());
            writeOptionalDataElement(DETECTOR_ENVIRONMENT, detection.getDetectorEnvironment());
            writeOptionalDataElement(HEADLINE, detection.getHeadline());
            writeOptionalDataElement(DETAILED_DESCRIPTION, detection.getDetailedDescription());
            writeOptionalDataElement(FULL_DESCRIPTION, detection.getFullDescription());
            writeOptionalDataElement(DETECTION_UNIQUE_ID, detection.getDetectionUniqueId());
            writeOptionalDataElement(DETECTION_REVISION, detection.getDetectionRevision());
            writeOptionalDataElement(DEFUNCT, detection.getDefunct());
            writeOptionalDataElement(EXECUTION_SCHEDULE, detection.getExecutionSchedule());
            writeOptionalDataElement(EXECUTION_TIME, detection.getExecutionTime());
            writeOptionalDataElement(EFFECTIVE_EXECUTION_TIME, detection.getEffectiveExecutionTime());
            writeValues(detection.getValues());
            writeLinkedEvents(detection.getLinkedEvents());
            writeEndElement(DETECTION);

        } catch (final SAXException e) {
            logError(e.getMessage(), e);
        }
    }

    private void writeValues(final List<DetectionValue> values) throws SAXException {
        if (NullSafe.hasItems(values)) {
            for (final DetectionValue value : values) {
                writeValue(value);
            }
        }
    }

    private void writeValue(final DetectionValue value) throws SAXException {
        final AttributesImpl attrs = new AttributesImpl();
        if (value.getName() != null) {
            attrs.addAttribute(NAMESPACE, NAME, NAME, XML_TYPE_STRING, value.getName());
        }
        writeDataElement(VALUE, attrs, value.getValue());
    }


    private void writeLinkedEvents(final List<DetectionLinkedEvent> linkedEvents) throws SAXException {
        if (NullSafe.hasItems(linkedEvents)) {
            writeStartElement(LINKED_EVENTS);
            for (final DetectionLinkedEvent linkedEvent : linkedEvents) {
                writeLinkedEvent(linkedEvent);
            }
            writeEndElement(LINKED_EVENTS);
        }
    }

    private void writeLinkedEvent(final DetectionLinkedEvent linkedEvent) throws SAXException {
        writeStartElement(LINKED_EVENT);
        if (linkedEvent.getStroom() != null) {
            writeDataElement(STROOM, linkedEvent.getStroom());
        }
        if (linkedEvent.getStreamId() != null) {
            writeDataElement(STREAM_ID, linkedEvent.getStreamId().toString());
        }
        if (linkedEvent.getEventId() != null) {
            writeDataElement(EVENT_ID, linkedEvent.getEventId().toString());
        }
        writeEndElement(LINKED_EVENT);
    }

    private void writeOptionalDataElement(final String elementName,
                                          final Integer integer) throws SAXException {
        if (integer != null) {
            writeDataElement(elementName, EMPTY_ATTS, integer.toString());
        }
    }

    private void writeOptionalDataElement(final String elementName,
                                          final Boolean bool) throws SAXException {
        if (bool != null) {
            writeDataElement(elementName, EMPTY_ATTS, bool.toString().toLowerCase(Locale.ROOT));
        }
    }

    private void writeOptionalDataElement(final String elementName,
                                          final String text) throws SAXException {
        if (NullSafe.isNonEmptyString(text)) {
            writeDataElement(elementName, EMPTY_ATTS, text);
        }
    }

    private void writeDataElement(final String elementName,
                                  final String text) throws SAXException {
        writeDataElement(elementName, EMPTY_ATTS, text);
    }

    private void writeDataElement(final String elementName,
                                  final Attributes attributes,
                                  final String text) throws SAXException {
        writeStartElement(elementName, attributes);
        writeText(text);
        writeEndElement(elementName);
    }

    private void writeStartElement(final String elementName) throws SAXException {
        writeStartElement(elementName, EMPTY_ATTS);
    }

    private void writeStartElement(final String elementName, final Attributes attributes) throws SAXException {
        handler.startElement(NAMESPACE, elementName, elementName, attributes);
    }

    private void writeEndElement(final String elementName) throws SAXException {
        handler.endElement(NAMESPACE, elementName, elementName);
    }

    private void writeText(final String text) throws SAXException {
        if (text != null) {
            final char[] chars = text.toCharArray();
            handler.characters(chars, 0, chars.length);
        }
    }
}
