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
import stroom.pipeline.destination.Destination;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.factory.PipelineFactoryException;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.factory.PipelinePropertyDocRef;
import stroom.pipeline.writer.AbstractDestinationProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Severity;

import com.google.common.base.Preconditions;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public abstract class AbstractKafkaAppender extends AbstractDestinationProvider implements Destination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractKafkaAppender.class);

    private final ErrorReceiverProxy errorReceiverProxy;
    private SharedKafkaProducer sharedKafkaProducer;
    private KafkaProducer<String, byte[]> kafkaProducer;
    private final KafkaProducerFactory kafkaProducerFactory;

    private final ByteArrayOutputStream byteArrayOutputStream;
    private final Queue<Future<RecordMetadata>> kafkaMetaFutures;

    private boolean flushOnSend = true;
    private DocRef kafkaConfigRef;
    private long maxRecordCount;
    private long recordCount = 0;
    private byte[] header;
    private byte[] footer;

    protected AbstractKafkaAppender(final ErrorReceiverProxy errorReceiverProxy,
                                    final KafkaProducerFactory kafkaProducerFactory) {
        this.errorReceiverProxy = errorReceiverProxy;
        this.kafkaProducerFactory = kafkaProducerFactory;
        this.byteArrayOutputStream = new ByteArrayOutputStream();
        this.kafkaMetaFutures = new ArrayDeque<>();
    }

    @Override
    public void startProcessing() {
        if (kafkaConfigRef == null) {
            throw ProcessException.create("No Kafka config has been specified");
        }

        try {
            this.sharedKafkaProducer = kafkaProducerFactory.getSharedProducer(kafkaConfigRef);
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
        super.startProcessing();
    }

    @Override
    public void endProcessing() {
        closeAndResetStream();

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

    @Override
    public final OutputStream getOutputStream() {
        return getOutputStream(null, null);
    }

    @Override
    public final OutputStream getOutputStream(final byte[] header, final byte[] footer) {
        this.header = header;
        this.footer = footer;

        if (byteArrayOutputStream.size() == 0) {
            writeHeader();
        }

        return byteArrayOutputStream;
    }

    private boolean writeHeader() {
        return write(header);
    }

    private boolean writeFooter() {
        return write(footer);
    }

    private final boolean write(final byte[] bytes) {
        if (bytes != null && bytes.length > 0) {
            byteArrayOutputStream.write(bytes, 0, bytes.length);
            return true;
        }
        return false;
    }

    @Override
    public Destination borrowDestination() {
        return this;
    }

    @Override
    public void returnDestination(final Destination destination) {
        Preconditions.checkArgument(
                destination == this,
                "Returned destination is a different object to this");

        if (++recordCount >= maxRecordCount) {
            closeAndResetStream();
        }
    }

    private void closeAndResetStream() {
        //no point in sending just a header and footer with no body
        if (byteArrayOutputStream.size() > 0 && byteArrayOutputStream.size() > header.length) {
            writeFooter();
            final byte[] streamCopy = byteArrayOutputStream.toByteArray();

            sendMessage(streamCopy);
        }

        //clear the stream ready for the next borrow call
        byteArrayOutputStream.reset();
    }

    private void sendMessage(final byte[] messageValue) {

        final ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                getTopic(), getRecordKey(), messageValue);

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

    @SuppressWarnings("unused")
    @PipelineProperty(
            description = "The Kafka config to use.",
            displayPriority = 1)
    @PipelinePropertyDocRef(types = KafkaConfigDoc.TYPE)
    public void setKafkaConfig(final DocRef kafkaConfigRef) {
        this.kafkaConfigRef = kafkaConfigRef;
    }


    @SuppressWarnings("unused")
    @PipelineProperty(
            description = "Choose the maximum number of records or events that a message will contain",
            defaultValue = "1",
            displayPriority = 3)
    public void setMaxRecordCount(final String maxRecordCount) {
        if (maxRecordCount != null && maxRecordCount.trim().length() > 0) {
            try {
                final Long value = ModelStringUtil.parseIECByteSizeString(maxRecordCount);
                if (value == null) {
                    throw new PipelineFactoryException("Incorrect value for max record count: " + maxRecordCount);
                }

                this.maxRecordCount = value;
            } catch (final NumberFormatException e) {
                throw new PipelineFactoryException("Incorrect value for max record count: " + maxRecordCount);
            }
        }
    }

    @SuppressWarnings("unused")
    @PipelineProperty(
            description = "At the end of the stream, wait for acknowledgement from the Kafka broker for all " +
                    "the messages sent. This ensures errors are caught in the pipeline process.",
            defaultValue = "true",
            displayPriority = 4)
    public void setFlushOnSend(final boolean flushOnSend) {
        this.flushOnSend = flushOnSend;
    }

    protected void error(final Exception e) {
        errorReceiverProxy.log(Severity.ERROR, null, getElementId(), e.getMessage(), e);
    }

    protected void log(final Severity severity, final String message, final Exception e) {
        errorReceiverProxy.log(severity, null, getElementId(), message, e);
    }

    public abstract String getTopic();

    public abstract String getRecordKey();

}
