/*
 * Copyright 2020 Crown Copyright
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
package stroom.annotation.pipeline;

import stroom.annotation.api.AnnotationCreator;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.CreateEntryRequest;
import stroom.annotation.shared.EventId;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.filter.AbstractXMLFilter;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.util.shared.Severity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import javax.inject.Inject;
import java.util.ArrayList;

/**
 * This class processes XML documents that conform to annotation:1 schema, and creates corresponding Stroom annotations.
 * Example follows:

 <?xml version="1.1" encoding="UTF-8"?>
 <annotations xmlns="annotation:1" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="annotation:1 file://annotation-v1.0.xsd">
 <annotation>
     <createTime>2020-01-23 11:11:11.111Z</createTime>
     <title>Something important happened</title>
     <description>The important thing detector noticed a HOLE in object BUCKET1 </description>
     <associateEvents>
       <associatedEvent>
         <streamId>1234</streamId>
         <eventId>56</eventId>
       </associatedEvent>
       <associatedEvent>
         <streamId>9876</streamId>
         <eventId>54</eventId>
       </associatedEvent>
     </associateEvents>
     <data Name="object" Value="BUCKET1" />
     <data Name="importance" Value="Rather Insignificant" />
   </annotation>
 </annotations>


 */


@ConfigurableElement(
        type = "AnnotationWriter",
        category = PipelineElementType.Category.DESTINATION,
        roles = {PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.VISABILITY_SIMPLE},
        icon = ElementIcons.TEXT)
class AnnotationWriter extends AbstractXMLFilter {
    private static final String ANNOTATION_TAG = "annotation";
    private static final String TITLE_TAG = "title";
    private static final String DESCRIPTION_TAG = "description";
    private static final String EVENTID_TAG = "eventId";
    private static final String STREAMID_TAG = "streamId";
    private static final String EVENT_TAG = "associatedEvent";

    private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationWriter.class);

    private final LocationFactoryProxy locationFactory;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final AnnotationCreator annotationCreator;
    private Locator locator;

    private String lastTag = null;
    private Annotation currentAnnotation = null;
    private String lastEventId = null;
    private String lastStreamId = null;

    private ArrayList<EventId> currentEventIds = null;

    @Inject
    AnnotationWriter(final AnnotationCreator annotationCreator,
                     final ErrorReceiverProxy errorReceiverProxy,
                     final LocationFactoryProxy locationFactory
                     ) {
        this.errorReceiverProxy = errorReceiverProxy;
        this.locationFactory = locationFactory;
        this.annotationCreator = annotationCreator;

    }

    /**
     * Sets the locator to use when reporting errors.
     *
     * @param locator The locator to use.
     */
    @Override
    public void setDocumentLocator(final Locator locator) {
        this.locator = locator;
        super.setDocumentLocator(locator);
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {

        lastTag = localName;

        if (ANNOTATION_TAG.equals(localName)) {
            currentAnnotation = new Annotation();
            currentEventIds = new ArrayList<>();
        }

        super.startElement(uri, localName, qName, atts);
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        String val = new String (ch, start, length);

        if (TITLE_TAG.equals(lastTag))
            currentAnnotation.setTitle(val);
        else if (DESCRIPTION_TAG.equals(lastTag))
            currentAnnotation.setSubject(val);
        else if (EVENTID_TAG.equals(lastTag))
            lastEventId = val;
        else if (STREAMID_TAG.equals(lastTag))
            lastStreamId = val;

        super.characters(ch, start, length);
    }


    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {

        if (EVENT_TAG.equals(localName)){
            if (lastStreamId == null){
                log (Severity.ERROR, "StreamId must be a long integer, but none provided ", null);
            }else if (lastEventId == null){
                log (Severity.ERROR, "EventId must be a long integer, but none provided ", null);
            }else {
                long streamId = 0;
                try {
                    streamId = Long.parseLong(lastStreamId);
                } catch (NumberFormatException ex){
                    log (Severity.ERROR, "StreamId must be a long integer.  Got " + lastStreamId, null);
                }
                if (streamId > 0) {
                    try {
                        long eventId = Long.parseLong(lastEventId);

                        currentEventIds.add(new EventId(streamId, eventId));
                    } catch (NumberFormatException ex) {
                        log(Severity.ERROR, "EventId must be a long integer.  Got " + lastEventId, null);
                    }
                }
            }
        }else if (ANNOTATION_TAG.equals(localName) && currentAnnotation != null){
            CreateEntryRequest request = new CreateEntryRequest(currentAnnotation, Annotation.COMMENT, null, currentEventIds );

            try {
                annotationCreator.createEntry(new CreateEntryRequest(currentAnnotation, Annotation.COMMENT, null, currentEventIds));

            } catch (final RuntimeException e) {
                log(Severity.ERROR, "Unable to create annotation " + currentAnnotation.getSubject() , e);
            }
            currentAnnotation = null;
            currentEventIds = null;
        }

        super.endElement(uri, localName, qName);
    }


    private void log(final Severity severity, final String message, final Exception e) {
        errorReceiverProxy.log(severity, locationFactory.create(locator), getElementId(), message, e);
        switch (severity){
            case FATAL_ERROR:
                LOGGER.error(message, e);
                break;
            case ERROR:
                LOGGER.error(message, e);
                break;
            case WARNING:
                LOGGER.warn(message, e);
                break;
            case INFO:
                LOGGER.info(message,e);
                break;
        }
    }
}