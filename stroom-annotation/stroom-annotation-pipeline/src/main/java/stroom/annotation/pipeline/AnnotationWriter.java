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

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import stroom.annotation.api.AnnotationCreator;
import stroom.annotation.impl.AnnotationService;
import stroom.annotation.shared.*;
import stroom.event.logging.api.DocumentEventLog;
import stroom.hadoopcommonshaded.org.apache.zookeeper.proto.CreateRequest;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.filter.AbstractXMLFilter;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.util.shared.Severity;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

/**
 * This class processes XML documents that conform to a particular schema that hasn't been formalised/published yet.
 * Example follows:

 <?xml version="1.1" encoding="UTF-8"?>
 <Annotations xmlns="annotation" xmlns:stroom="stroom" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
     <Annotation>
     <CreateTime>2020-01-23 11:11:11.111Z</CreateTime>
     <Title>Something important happened</Title>
     <Description>The important thing detector noticed a HOLE in object BUCKET1 </Description>
     <AssociateEvents>
       <AssociatedEvent>
         <StreamId>1234</StreamId>
         <EventId>56</EventId>
       </AssociatedEvent>
       <AssociatedEvent>
         <StreamId>9876</StreamId>
         <EventId>54</EventId>
       </AssociatedEvent>
     </AssociateEvents>
     <Data Name="object" Value="BUCKET1" />
     <Data Name="importance" Value="Rather Insignificant" />
   </Annotation>
 </Annotations>


 */


@ConfigurableElement(
        type = "AnnotationWriter",
        category = PipelineElementType.Category.DESTINATION,
        roles = {PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.VISABILITY_SIMPLE},
        icon = ElementIcons.TEXT)
class AnnotationWriter extends AbstractXMLFilter {
    private static final String ANNOTATION_TAG = "Annotation";
    private static final String TITLE_TAG = "Title";
    private static final String DESCRIPTION_TAG = "Description";
    private static final String EVENTID_TAG = "EventId";
    private static final String STREAMID_TAG = "StreamId";
    private static final String EVENT_TAG = "AssociatedEvent";

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
        System.out.println ("AnnotationWriter endelement");

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