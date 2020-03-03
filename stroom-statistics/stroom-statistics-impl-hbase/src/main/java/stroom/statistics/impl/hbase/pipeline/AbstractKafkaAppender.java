/*
 * Copyright 2016 Crown Copyright
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

import com.google.common.base.Preconditions;
import org.apache.kafka.clients.producer.ProducerRecord;
import stroom.docref.DocRef;
import stroom.kafka.pipeline.KafkaProducerFactory;
import stroom.kafkaConfig.shared.KafkaConfigDoc;
import stroom.pipeline.destination.Destination;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.factory.PipelineFactoryException;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.factory.PipelinePropertyDocRef;
import stroom.pipeline.writer.AbstractDestinationProvider;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Severity;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public abstract class AbstractKafkaAppender extends AbstractDestinationProvider implements Destination {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractKafkaAppender.class);

    private final ErrorReceiverProxy errorReceiverProxy;
    private org.apache.kafka.clients.producer.KafkaProducer kafkaProducer;
    private final KafkaProducerFactory stroomKafkaProducerFactory;

    private final ByteArrayOutputStream byteArrayOutputStream;

    private boolean flushOnSend = false;
    private DocRef kafkaConfigRef;
    private long maxRecordCount;
    private long recordCount = 0;
    private byte[] header;
    private byte[] footer;

    protected AbstractKafkaAppender(final ErrorReceiverProxy errorReceiverProxy,
                                    final KafkaProducerFactory stroomKafkaProducerFactory) {
        this.errorReceiverProxy = errorReceiverProxy;
        this.stroomKafkaProducerFactory = stroomKafkaProducerFactory;
        this.byteArrayOutputStream = new ByteArrayOutputStream();
    }

    @Override
    public void startProcessing() {
        if (kafkaConfigRef == null) {
            throw new ProcessException("No Kafka config has been specified");
        }

        try {
            this.kafkaProducer = stroomKafkaProducerFactory.createProducer(kafkaConfigRef).orElse(null);
        } catch (final RuntimeException e) {
            String msg = "Error initialising kafka producer - " + e.getMessage();
            log(Severity.FATAL_ERROR, msg, e);
            throw new LoggedException(msg);
        }

        if (kafkaProducer == null) {
            String msg = "No Kafka producer connector is available, check Stroom's configuration";
            log(Severity.FATAL_ERROR, msg, null);
            throw new LoggedException(msg);
        }
        super.startProcessing();
    }

    @Override
    public void endProcessing() {
        closeAndResetStream();

        super.endProcessing();
    }

    @Override
    public final OutputStream getByteArrayOutputStream() {
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
            byte[] streamCopy = byteArrayOutputStream.toByteArray();

            sendMessage(streamCopy);
        }

        //clear the stream ready for the next borrow call
        byteArrayOutputStream.reset();

    }

    private void sendMessage(final byte[] messageValue) {

        final ProducerRecord<String, String> record = new ProducerRecord(getTopic(),
                getRecordKey(), messageValue);

        try {
            kafkaProducer.send(record);
            if (flushOnSend) {
                throw new UnsupportedOperationException("Flush on send is not available in this version of Stroom");
            }
        } catch (RuntimeException e) {
            error(e);
        }

    }

    @SuppressWarnings("unused")
    @PipelineProperty(description = "The Kafka config to use.", displayPriority = 1)
    @PipelinePropertyDocRef(types = KafkaConfigDoc.DOCUMENT_TYPE)
    public void setKafkaConfig(final DocRef kafkaConfigRef) {
        this.kafkaConfigRef = kafkaConfigRef;
    }

    @SuppressWarnings("unused")
    @PipelineProperty(
            description = "Wait for acknowledgement from the Kafka broker for all of the messages sent." +
                    "This is slower but catches errors in the pipeline process",
            defaultValue = "false",
            displayPriority = 4)
    public void setFlushOnSend(final boolean flushOnSend) {
        this.flushOnSend = flushOnSend;
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

    protected void error(final Exception e) {
        errorReceiverProxy.log(Severity.ERROR, null, getElementId(), e.getMessage(), e);
    }

    protected void log(final Severity severity, final String message, final Exception e) {
        errorReceiverProxy.log(severity, null, getElementId(), message, e);
    }

    public abstract String getTopic();

    public abstract String getRecordKey();

}
