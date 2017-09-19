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

package stroom.pipeline.server.writer;

import com.google.common.base.Preconditions;
import stroom.connectors.kafka.StroomKafkaProducer;
import stroom.connectors.kafka.StroomKafkaProducerFactoryService;
import stroom.connectors.kafka.StroomKafkaProducerRecord;
import stroom.pipeline.destination.Destination;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.factory.PipelineFactoryException;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Severity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public abstract class AbstractKafkaAppender extends AbstractDestinationProvider implements Destination {

    private final ErrorReceiverProxy errorReceiverProxy;
    private final StroomKafkaProducer stroomKafkaProducer;

    private final ByteArrayOutputStream byteArrayOutputStream;

    private boolean flushOnSend = false;
    private long maxRecordCount;
    private long recordCount = 0;
    private byte[] header;
    private byte[] footer;

    protected AbstractKafkaAppender(final ErrorReceiverProxy errorReceiverProxy,
                                    final StroomKafkaProducerFactoryService stroomKafkaProducerFactoryService) {
        this.errorReceiverProxy = errorReceiverProxy;
        this.stroomKafkaProducer = stroomKafkaProducerFactoryService.getProducer(exception ->
                errorReceiverProxy.log(
                        Severity.ERROR,
                        null,
                        null,
                        "Called function on Fake Kafka proxy!",
                        exception)
        );
        this.byteArrayOutputStream = new ByteArrayOutputStream();
    }

    @Override
    public void endProcessing() {
        closeAndResetStream();
        try {
            //TODO this producer is shared by many threads so a flush here will flush all unsent messages from
            //all threads so we may have to wait longer than needed.
            //A better approach would be for the StroomKafkaProducer to expose the Future or an abstraction
            //of it so we can call get on all the futures at the end of processing.
            stroomKafkaProducer.flush();
        } catch (Exception e) {
            error(e);
        }
        super.endProcessing();
    }

    @Override
    public final OutputStream getByteArrayOutputStream() throws IOException {
        return getOutputStream(null, null);
    }

    @Override
    public final OutputStream getOutputStream(final byte[] header, final byte[] footer) throws IOException {
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
    public Destination borrowDestination() throws IOException {
        return this;
    }

    @Override
    public void returnDestination(final Destination destination) throws IOException {
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
        String topic = getTopic();
        String recordKey = getRecordKey();

        final StroomKafkaProducerRecord<String, byte[]> newRecord =
                new StroomKafkaProducerRecord.Builder<String, byte[]>()
                        .topic(topic)
                        .key(recordKey)
                        .value(messageValue)
                        .build();
        try {
            stroomKafkaProducer.send(newRecord, flushOnSend, this::error);
        } catch (Exception e) {
            error(e);
        }
    }

    @SuppressWarnings("unused")
    @PipelineProperty(
            description = "Flush the producer each time a message is sent. " +
                    "Flushing on each message is slower but catches errors sooner.",
            defaultValue = "false")
    public void setFlushOnSend(final boolean flushOnSend) {
        this.flushOnSend = flushOnSend;
    }

    @SuppressWarnings("unused")
    @PipelineProperty(
            description = "Choose the maximum number of records or events that a message will contain",
            defaultValue = "1")
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
