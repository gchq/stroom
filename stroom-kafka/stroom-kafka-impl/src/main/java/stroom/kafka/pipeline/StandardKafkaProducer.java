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

package stroom.kafka.pipeline;

import stroom.docref.DocRef;
import stroom.kafka.api.KafkaProducerFactory;
import stroom.kafka.api.SharedKafkaProducer;
import stroom.kafka.shared.KafkaConfigDoc;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.factory.PipelinePropertyDocRef;
import stroom.pipeline.filter.AbstractXMLFilter;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.svg.shared.SvgImage;
import stroom.util.CharBuffer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;
import stroom.util.xml.XMLUtil;

import jakarta.inject.Inject;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.header.Headers;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

/**
 * Pipeline filter element that expects XML documents that conform to kafka-records:1 format and creates corresponding
 * Kafka messages for these.
 */
@ConfigurableElement(
        type = "StandardKafkaProducer",
        category = PipelineElementType.Category.DESTINATION,
        description = """
                Consumes XML documents in the `kafka-records:2` namespace.
                For each `<kafkaRecord>` element converts it into a Kafka message that is passed to the \
                Kafka producer defined by the `kafkaConfig` property""",
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.VISABILITY_SIMPLE},
        icon = SvgImage.PIPELINE_KAFKA)
class StandardKafkaProducer extends AbstractXMLFilter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StandardKafkaProducer.class);

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
    private final Queue<Future<RecordMetadata>> kafkaMetaFutures;
    private final CharBuffer content = new CharBuffer();

    private Locator locator = null;
    private DocRef configRef = null;
    private SharedKafkaProducer sharedKafkaProducer = null;
    private KafkaProducer<String, byte[]> kafkaProducer = null;
    private KafkaMessageState state = null;
    private boolean flushOnSend = true;

    private TransformerHandler xmlValueHandler;
    private ByteArrayOutputStream outputStream;
    private int xmlValueDepth = -1;

    @Inject
    StandardKafkaProducer(final ErrorReceiverProxy errorReceiverProxy,
                          final LocationFactoryProxy locationFactory,
                          final KafkaProducerFactory stroomKafkaProducerFactory) {
        this.errorReceiverProxy = errorReceiverProxy;
        this.locationFactory = locationFactory;
        this.stroomKafkaProducerFactory = stroomKafkaProducerFactory;
        this.kafkaMetaFutures = new ArrayDeque<>();
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
                throw LoggedException.create("KafkaConfig has not been set");
            }

            sharedKafkaProducer = stroomKafkaProducerFactory.getSharedProducer(configRef);

            kafkaProducer = sharedKafkaProducer.getKafkaProducer().orElseThrow(() -> {
                log(Severity.FATAL_ERROR, "No Kafka produce exists for config " + configRef, null);
                return LoggedException.create("Unable to create Kafka Producer using config " + configRef);
            });
        } catch (final KafkaException ex) {
            log(Severity.FATAL_ERROR, "Unable to create Kafka Producer using config " + configRef.getUuid(), ex);
        } finally {
            super.startProcessing();
        }
    }


    @Override
    public void endProcessing() {
        if (flushOnSend) {
            // Ensure all msgs buffered by kafka has been sent. As the producer is
            // shared this means waiting for other msgs from other streams however the
            // buffer is likely small so should not be a major issue.
            LOGGER.logDurationIfDebugEnabled(
                    () -> kafkaProducer.flush(),
                    "KafkaProducer flush");

            // If flushOnSend was set we will have futures in the queue
            // so call get on each one to ensure they have all completed.
            // Not certain that we need to check the futures after the flush as I think
            // the flush will block till everything has sent.
            LOGGER.logDurationIfDebugEnabled(
                    () -> {
                        Future<RecordMetadata> future;
                        while ((future = kafkaMetaFutures.poll()) != null) {
                            try {
                                future.get();
                            } catch (final InterruptedException e) {
                                // Continue to interrupt this thread.
                                Thread.currentThread().interrupt();

                                throw ProcessException.create("Thread interrupted");
                            } catch (final ExecutionException e) {
                                log(Severity.ERROR, "Error sending message to Kafka", e);
                            }
                        }
                    },
                    "Wait for futures to complete");
        }

        // Vital this happens or we leak resources
        stroomKafkaProducerFactory.returnSharedKafkaProducer(sharedKafkaProducer);
        super.endProcessing();
    }

    private static Long createTimestamp(final String isoFormat) {
        try {
            final Instant instant = Instant.parse(isoFormat);
            return instant.toEpochMilli();
        } catch (final DateTimeParseException ex) {
            return null;
        }
    }


    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {

        if (state != null && VALUE_ELEMENT_LOCAL_NAME.equals(state.lastElement) && !state.inHeader) {
            //This is an XML value element
            if (xmlValueDepth == -1) {
                final ErrorListener errorListener = new ErrorListener() {
                    @Override
                    public void warning(final TransformerException exception) {
                        errorReceiverProxy.log(Severity.WARNING, locationFactory.create(locator), getElementId(),
                                "Kafka XML value parse error", exception);
                    }

                    @Override
                    public void error(final TransformerException exception) {
                        errorReceiverProxy.log(Severity.ERROR, locationFactory.create(locator), getElementId(),
                                "Kafka XML value parse error", exception);
                    }

                    @Override
                    public void fatalError(final TransformerException exception) {
                        errorReceiverProxy.log(Severity.FATAL_ERROR, locationFactory.create(locator), getElementId(),
                                "Kafka XML value parse error", exception);
                    }
                };

                try {
                    this.xmlValueHandler = XMLUtil.createTransformerHandler(errorListener, false);
                    outputStream = new ByteArrayOutputStream();
                    xmlValueHandler.setResult(new StreamResult(outputStream));
                    xmlValueHandler.startDocument();
                    xmlValueDepth = 0;
                } catch (final TransformerConfigurationException e) {
                    errorReceiverProxy.log(Severity.FATAL_ERROR,
                            locationFactory.create(e.getLocator().getLineNumber(), e.getLocator().getColumnNumber()),
                            getElementId(), e.getMessage(), e);
                    log(Severity.ERROR, "Unable to create XML value handler ", null);
                }
            }
            xmlValueHandler.startElement(uri, localName, qName, atts);
            xmlValueDepth++;
        } else {
            if (RECORD_ELEMENT_LOCAL_NAME.equals(localName)) {
                state = new KafkaMessageState();

                for (int i = 0; i < atts.getLength(); i++) {
                    final String attName = atts.getLocalName(i);

                    if (TOPIC_ATTRIBUTE_LOCAL_NAME.equals(attName)) {
                        state.topic = atts.getValue(i);
                    } else if (TIMESTAMP_ATTRIBUTE_LOCAL_NAME.equals(attName)) {
                        final Long timestamp = createTimestamp(atts.getValue(i));
                        if (timestamp == null) {
                            log(Severity.ERROR,
                                    "Kafka timestamp must be in ISO 8601 format.  Got " + atts.getValue(i),
                                    null);
                        } else {
                            state.timestamp = timestamp;
                        }
                    } else if (PARTITION_ATTRIBUTE_LOCAL_NAME.equals(attName)) {
                        try {
                            state.partition = Integer.parseInt(atts.getValue(i));
                        } catch (final NumberFormatException ex) {
                            log(Severity.ERROR, "Kafka partition must be an integer.  Got " + atts.getValue(i), null);
                        }
                    }
                }
            }

            if (state != null) {
                if (HEADER_ELEMENT_LOCAL_NAME.equals(localName)) {
                    state.inHeader = true;
                }
                state.lastElement = localName;
            }
        }

        content.clear();
        super.startElement(uri, localName, qName, atts);
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if (state != null) {
            if (KEY_ELEMENT_LOCAL_NAME.equals(localName)) {
                if (state.inHeader) {
                    state.headerNames.add(content.toString());
                } else {
                    state.key = content.toString();
                }
            } else if (VALUE_ELEMENT_LOCAL_NAME.equals(localName)) {
                if (state.inHeader) {
                    state.headerVals.add(content.toString());
                } else if (xmlValueDepth < 0) {
                    state.messageValue = content.toString().getBytes(StandardCharsets.UTF_8);
                }
            }
        }

        if (xmlValueDepth == 0) {
            if (VALUE_ELEMENT_LOCAL_NAME.equals(localName)) {
                // Create the val from the contents of XML handler buffer
                xmlValueHandler.endDocument();
                state.messageValue = outputStream.toByteArray();
                xmlValueDepth = -1;
            } else {
                throw new SAXException("Unexpected tag " + localName + " in kafka message value.");
            }
        } else if (xmlValueDepth > 0) {
            // Closing an XML value element
            xmlValueHandler.endElement(uri, localName, qName);
            xmlValueDepth--;

        } else if (state != null) {
            state.lastElement = null;
            if (HEADER_ELEMENT_LOCAL_NAME.equals(localName)) {
                state.inHeader = false;
            } else if (RECORD_ELEMENT_LOCAL_NAME.equals(localName)) {
                createKafkaMessage(state);
                state = null;
            }
        }

        content.clear();
        super.endElement(uri, localName, qName);
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        if (state != null && VALUE_ELEMENT_LOCAL_NAME.equals(state.lastElement)) {
            if (!state.inHeader && xmlValueDepth >= 0) {
                xmlValueHandler.characters(ch, start, length);
            }
        }

        content.append(ch, start, length);
        super.characters(ch, start, length);
    }

    private void createKafkaMessage(final KafkaMessageState state) {

        if (state.isInvalid()) {
            log(Severity.ERROR, "Badly formed Kafka message " + state.whyInvalid(), null);
        } else {
            // We need to serialise to byte[] with the same encoding as kafka's
            // StringSerializer which also uses. By default kafka will use UTF8
            final ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                    state.topic,
                    state.partition,
                    state.timestamp,
                    state.key,
                    state.messageValue);

            final Headers headers = record.headers();
            for (int i = 0; i < state.headerNames.size(); i++) {
                headers.add(
                        state.headerNames.get(i),
                        state.headerVals.get(i).getBytes(StandardCharsets.UTF_8));
            }
//            logState(state);
            final Future<RecordMetadata> sendFuture = kafkaProducer.send(record);
            if (flushOnSend) {
                //keep hold of the future so we can wait for it at the end of processing
                kafkaMetaFutures.add(sendFuture);
            }
        }
    }

    private void logState(final KafkaMessageState state) {
        final StringBuilder stringBuilder = new StringBuilder()
                .append("Writing to Kafka topic: ")
                .append(state.topic)
                .append(" Timestamp: ")
                .append(state.timestamp)
                .append(" Key: ")
                .append(state.key);
        for (int i = 0; i < state.headerNames.size(); i++) {
            stringBuilder
                    .append(" Header ")
                    .append(state.headerNames.get(i))
                    .append(" = ")
                    .append(state.headerVals.get(i));
        }
        stringBuilder
                .append(" Value: ")
                .append(Arrays.toString(state.messageValue));

        log(Severity.INFO, stringBuilder.toString(), null);
    }

    @PipelineProperty(
            description = "Kafka configuration details relating to where and how to send Kafka messages.",
            displayPriority = 1)
    @PipelinePropertyDocRef(types = KafkaConfigDoc.TYPE)
    public void setKafkaConfig(final DocRef configRef) {
        this.configRef = configRef;
    }

    @SuppressWarnings("unused")
    @PipelineProperty(
            description = "At the end of the stream, wait for acknowledgement from the Kafka broker for all " +
                          "the messages sent. This ensures errors are caught in the pipeline process.",
            defaultValue = "true",
            displayPriority = 2)
    public void setFlushOnSend(final boolean flushOnSend) {
        this.flushOnSend = flushOnSend;
    }

    private void log(final Severity severity, final String message, final Exception e) {
        errorReceiverProxy.log(severity, locationFactory.create(locator), getElementId(), message, e);
        switch (severity) {
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
                LOGGER.info(message, e);
                break;
        }
    }

    private static class KafkaMessageState {

        public Integer partition = null;
        public String topic = null;
        public String key = null;
        public List<String> headerNames = new ArrayList<>();
        public List<String> headerVals = new ArrayList<>();
        public Long timestamp = null;
        public byte[] messageValue = null;
        public boolean inHeader = false;
        public String lastElement = null;

        public boolean isInvalid() {
            return whyInvalid() != null;
        }

        public String whyInvalid() {
            if (topic == null) {
                return "Topic is not defined.";
            }
            if (key == null && messageValue == null) {
                return "Neither the key or the value of the message are defined.";
            }
            if (headerNames.size() != headerVals.size()) {
                return "Incomplete header information.";
            }

            return null;
        }
    }

}
