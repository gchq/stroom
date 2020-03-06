/*
 * Copyright 2017 Crown Copyright
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

package stroom.kafka.pipeline;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import stroom.docref.DocRef;
import stroom.kafkaConfig.shared.KafkaConfigDoc;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.factory.PipelinePropertyDocRef;
import stroom.pipeline.filter.AbstractXMLFilter;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Random;


@ConfigurableElement(
        type = "StandardKafkaProducer",
        category = PipelineElementType.Category.DESTINATION,
        roles = {PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.VISABILITY_SIMPLE},
        icon = ElementIcons.KAFKA)
class StandardKafkaProducer extends AbstractXMLFilter {

    private static class KafkaMessageState{
        public Integer partition = null;
        public String topic = null;
        public String key = null;
        public ArrayList<String> headerNames = new ArrayList();
        public ArrayList<String> headerVals = new ArrayList();
        public Long timestamp = null;
        public String messageValue = null;
        public boolean inHeader = false;
        public String lastElement = null;

        public boolean isInvalid(){
            return whyInvalid() != null;
        }

        public String whyInvalid (){
            if (topic == null)
                return "Topic is not defined.";
            if (key == null && messageValue == null)
                return "Neither the key or the value of the message are defined.";
            if (headerNames.size() != headerVals.size())
                return "Incomplete header information.";

            return null;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(StandardKafkaProducer.class);

    private static final String RECORDS_ELEMENT_LOCAL_NAME = "kafkaRecords";
    private static final String RECORD_ELEMENT_LOCAL_NAME = "kafkaRecord";
    private static final String HEADER_ELEMENT_LOCAL_NAME = "header";
    private static final String KEY_ELEMENT_LOCAL_NAME = "key";
    private static final String VALUE_ELEMENT_LOCAL_NAME = "value";
    private static final String TOPIC_ATTRIBUTE_LOCAL_NAME = "topic";
    private static final String TIMESTAMP_ATTRIBUTE_LOCAL_NAME = "timestamp";
    private static final String PARTITION_ATTRIBUTE_LOCAL_NAME = "partition";

    private final ErrorReceiverProxy errorReceiverProxy;
    private final LocationFactoryProxy locationFactory;
    private final KafkaProducerFactory stroomKafkaProducerFactory;
    private final KafkaConfigStore configStore;

    private Locator locator = null;

    private DocRef configRef = null;
    private KafkaProducer<String, String> kafkaProducer = null;

    @Inject
    StandardKafkaProducer(final ErrorReceiverProxy errorReceiverProxy,
                          final LocationFactoryProxy locationFactory,
                          final KafkaProducerFactory stroomKafkaProducerFactory,
                          final KafkaConfigStore configStore) {
        this.errorReceiverProxy = errorReceiverProxy;
        this.locationFactory = locationFactory;
        this.stroomKafkaProducerFactory = stroomKafkaProducerFactory;
        this.configStore = configStore;
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

    /**
     * Initialise
     */
    @Override
    public void startProcessing() {
        try {
            if (configRef == null) {
                log(Severity.FATAL_ERROR, "KafkaConfig has not been set", null);
                throw new LoggedException("KafkaConfig has not been set");
            }

            //Initialise connection to Kafka broker
            KafkaConfigDoc config = configStore.readDocument(configRef);

            Optional <KafkaProducer<String,String>> optional = stroomKafkaProducerFactory.createProducer(configRef);
            if (!optional.isPresent())
            {
                log(Severity.FATAL_ERROR, "Unable to create Kafka Producer using config " + config.getData(), null);
                throw new LoggedException("Unable to create Kafka Producer using config " + configRef);
            }
            kafkaProducer = optional.get();
            super.startProcessing();
        } catch(KafkaException ex) {
            log(Severity.FATAL_ERROR, "Unable to create Kafka Producer using config " + configRef.getUuid(), ex);
        }
    }

    private static Long createTimestamp(String isoFormat){
        try {
            Instant instant = Instant.parse(isoFormat);
            return instant.toEpochMilli();
        }catch (DateTimeParseException ex){
            return null;
        }
    }

    private KafkaMessageState state = null;

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {

        if (RECORD_ELEMENT_LOCAL_NAME.equals(localName)) {
            state = new KafkaMessageState();

            for (int a = 0; a < atts.getLength(); a++){
                String attName = atts.getLocalName(a);

                if (TOPIC_ATTRIBUTE_LOCAL_NAME.equals(attName)){
                    state.topic = atts.getValue(a);
                } else if (TIMESTAMP_ATTRIBUTE_LOCAL_NAME.equals(attName)){
                    Long timestamp = createTimestamp (atts.getValue(a));
                    if (timestamp == null){
                        log (Severity.ERROR, "Kafka timestamp must be in ISO 8601 format.  Got " + atts.getValue(a), null);
                    }else{
                        state.timestamp = timestamp;
                    }
                } else if (PARTITION_ATTRIBUTE_LOCAL_NAME.equals(attName)){
                    try {
                        state.partition = Integer.parseInt(atts.getValue(a));
                    }catch (NumberFormatException ex){
                        log (Severity.ERROR, "Kafka partition must be an integer.  Got " + atts.getValue(a), null);
                    }
                }
            }
        }

        if (HEADER_ELEMENT_LOCAL_NAME.equals(localName)){
            state.inHeader = true;
        }

        if (state != null){
            state.lastElement = localName;
        }



        super.startElement(uri, localName, qName, atts);
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        String val = new String (ch, start, length);
        String element = state.lastElement;
        if (KEY_ELEMENT_LOCAL_NAME.equals(element)) {
            if (state.inHeader){
                state.headerNames.add(val);
            } else {
                state.key = val;
            }
        } else if (VALUE_ELEMENT_LOCAL_NAME.equals (element)) {
            if (state.inHeader){
                state.headerVals.add(val);
            }
            else {
                state.messageValue = val;
            }
        }

        super.characters(ch, start, length);
    }


    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if (state != null)
            state.lastElement = null;

        if (HEADER_ELEMENT_LOCAL_NAME.equals(localName)){
            state.inHeader = false;
        } else if (RECORD_ELEMENT_LOCAL_NAME.equals(localName)) {
            createKafkaMessage (state);
            state = null;
        }

        super.endElement(uri, localName, qName);
    }

    private void createKafkaMessage(KafkaMessageState state) {

        if (state.isInvalid()) {
            log(Severity.ERROR,"Badly formed Kafka message " + state.whyInvalid(), null);
        } else {
            ProducerRecord<String, String> record = new ProducerRecord<>(state.topic, state.partition,
                    state.timestamp, state.key, state.messageValue);
            Headers headers = record.headers();
            for (int h=0; h < state.headerNames.size(); h++){
                headers.add(state.headerNames.get(h), state.headerVals.get(h).getBytes(StandardCharsets.UTF_8));
            }
            StringBuffer buffer = new StringBuffer();
            buffer.append("Writing to Kafka topic: " + state.topic);
            buffer.append(" Timestamp: " + state.timestamp);
            buffer.append(" Key: " + state.key);
            for (int i = 0; i < state.headerNames.size(); i++) {
                buffer.append(" Header " + state.headerNames.get(i) + " = " + state.headerVals.get(i));
            }
            buffer.append(" Value: " + state.messageValue);

//            log (Severity.INFO, buffer.toString(), null);
            kafkaProducer.send(record, new Callback() {
                public void onCompletion(RecordMetadata metadata, Exception e) {
                    if(e != null) {
                        log (Severity.ERROR, "Unable to send record to Kafka", e);
                    } else {
//                        log (Severity.INFO, "Successfully sent record to Kafka", null);
                    }
                }});
        }
    }


    @PipelineProperty(description = "Kafka configuration details relating to where and how to send Kafka messages.", displayPriority = 1)
    @PipelinePropertyDocRef(types = KafkaConfigDoc.DOCUMENT_TYPE)
    public void setKafkaConfig(final DocRef configRef) {
        this.configRef = configRef;
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