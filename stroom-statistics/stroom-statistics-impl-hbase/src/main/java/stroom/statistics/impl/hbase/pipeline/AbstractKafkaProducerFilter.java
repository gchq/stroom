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

package stroom.statistics.impl.hbase.pipeline;

import stroom.docref.DocRef;
import stroom.kafka.api.KafkaProducerFactory;
import stroom.kafka.api.SharedKafkaProducer;
import stroom.kafka.shared.KafkaConfigDoc;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.factory.PipelinePropertyDocRef;
import stroom.pipeline.filter.AbstractSamplingFilter;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;

import com.google.common.base.Strings;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public abstract class AbstractKafkaProducerFilter extends AbstractSamplingFilter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractKafkaProducerFilter.class);

    private boolean flushOnSend;
    private DocRef kafkaConfigRef;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final LocationFactoryProxy locationFactory;
    private final KafkaProducerFactory kafkaProducerFactory;
    private final Queue<Future<RecordMetadata>> kafkaMetaFutures;

    private KafkaProducer<String, byte[]> kafkaProducer;
    private SharedKafkaProducer sharedKafkaProducer;

    private Locator locator;

    protected AbstractKafkaProducerFilter(final ErrorReceiverProxy errorReceiverProxy,
                                          final LocationFactoryProxy locationFactory,
                                          final KafkaProducerFactory kafkaProducerFactory) {

        super(errorReceiverProxy, locationFactory);
        this.errorReceiverProxy = errorReceiverProxy;
        this.locationFactory = locationFactory;
        this.kafkaProducerFactory = kafkaProducerFactory;
        this.flushOnSend = true;
        this.kafkaMetaFutures = new ArrayDeque<>();
    }

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();
        sendOutputToKafka();
    }

    @Override
    public void setDocumentLocator(final Locator locator) {
        super.setDocumentLocator(locator);
        this.locator = locator;
    }

    @Override
    public void startProcessing() {
        super.startProcessing();

        if (kafkaConfigRef == null) {
            throw ProcessException.create("No Kafka config has been specified");
        }

        if (Strings.isNullOrEmpty(getTopic())) {
            final String msg = "A Kafka topic has not been set";
            log(Severity.FATAL_ERROR, msg, null);
            throw LoggedException.create(msg);
        }
        if (Strings.isNullOrEmpty(getRecordKey())) {
            final String msg = "A Kafka record key has not been set";
            log(Severity.FATAL_ERROR, msg, null);
            throw LoggedException.create(msg);
        }

        try {
            sharedKafkaProducer = kafkaProducerFactory.getSharedProducer(kafkaConfigRef);
        } catch (final RuntimeException e) {
            final String msg = "Error initialising kafka producer - " + e.getMessage();
            log(Severity.FATAL_ERROR, msg, e);
            throw LoggedException.create(msg);
        }

        kafkaProducer = sharedKafkaProducer.getKafkaProducer().orElseThrow(() -> {
            final String msg = "No Kafka producer connector is available, check Stroom's configuration";
            log(Severity.FATAL_ERROR, msg, null);
            throw LoggedException.create(msg);
        });
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
        kafkaProducerFactory.returnSharedKafkaProducer(sharedKafkaProducer);
        super.endProcessing();
    }

    @SuppressWarnings("unused")
    @PipelineProperty(
            description = "At the end of the stream, wait for acknowledgement from the Kafka broker for all " +
                    "the messages sent. This ensures errors are caught in the pipeline process.",
            defaultValue = "true",
            displayPriority = 3)
    public void setFlushOnSend(final boolean flushOnSend) {
        this.flushOnSend = flushOnSend;
    }

    @PipelineProperty(
            description = "The Kafka config to use.",
            displayPriority = 1)
    @PipelinePropertyDocRef(types = KafkaConfigDoc.TYPE)
    public void setKafkaConfig(final DocRef kafkaConfigRef) {
        this.kafkaConfigRef = kafkaConfigRef;
    }

    void sendOutputToKafka() {

        // We need to serialise to byte[] with the same encoding as kafka's
        // StringSerializer which also uses. By default kafka will use UTF8
        final ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                getTopic(),
                getRecordKey(),
                getOutput().getBytes(StreamUtil.DEFAULT_CHARSET));

        try {
            final Future<RecordMetadata> sendFuture = kafkaProducer.send(record);
            if (flushOnSend) {
                //keep hold of the future so we can wait for it at the end of processing
                kafkaMetaFutures.add(sendFuture);
            }
        } catch (final RuntimeException e) {
            error(e);
        }
    }

    public abstract String getTopic();

    public abstract String getRecordKey();

    protected void error(final Exception e) {
        if (locator != null) {
            errorReceiverProxy.log(Severity.ERROR,
                    locationFactory.create(locator.getLineNumber(), locator.getColumnNumber()), getElementId(),
                    e.getMessage(), e);
        } else {
            errorReceiverProxy.log(Severity.ERROR, null, getElementId(), e.getMessage(), e);
        }
    }

    protected void log(final Severity severity, final String message, final Exception e) {
        errorReceiverProxy.log(severity, locationFactory.create(locator), getElementId(), message, e);
    }
}
