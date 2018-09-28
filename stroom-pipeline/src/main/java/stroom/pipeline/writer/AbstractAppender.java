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

package stroom.pipeline.writer;

import stroom.pipeline.destination.Destination;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Severity;

import java.io.IOException;
import java.io.OutputStream;

public abstract class AbstractAppender extends AbstractDestinationProvider implements Destination {
    private final ErrorReceiverProxy errorReceiverProxy;

    private OutputStream outputStream;
    private byte[] footer;
    private String size;
    private Long sizeBytes = null;
    private boolean splitAggregatedStreams;

    AbstractAppender(final ErrorReceiverProxy errorReceiverProxy) {
        this.errorReceiverProxy = errorReceiverProxy;
    }

    @Override
    public void endProcessing() {
        closeCurrentOutputStream();
        super.endProcessing();
    }

    @Override
    public void endStream() {
        if (splitAggregatedStreams) {
            closeCurrentOutputStream();
        }
        super.endStream();
    }

    @Override
    public Destination borrowDestination() throws IOException {
        return this;
    }

    @Override
    public void returnDestination(final Destination destination) throws IOException {
        final Long sizeBytes = getSizeBytes();
        if (sizeBytes > 0 && sizeBytes <= getCurrentOutputSize()) {
            closeCurrentOutputStream();
        }
    }

    private void closeCurrentOutputStream() {
        if (outputStream != null) {
            try {
                writeFooter();
            } catch (final IOException e) {
                error(e.getMessage(), e);
            }

            try {
                outputStream.close();
            } catch (final IOException e) {
                error(e.getMessage(), e);
            }

            outputStream = null;
        }
    }

    @Override
    public final OutputStream getByteArrayOutputStream() throws IOException {
        return getOutputStream(null, null);
    }

    @Override
    public OutputStream getOutputStream(final byte[] header, final byte[] footer) throws IOException {
        this.footer = footer;

        if (outputStream == null) {
            outputStream = createOutputStream();

            // If we haven't written yet then create the output stream and
            // write a header if we have one.
            if (header != null && header.length > 0) {
                // Write the header.
                write(header);
            }
        }

        return outputStream;
    }

    private void writeFooter() throws IOException {
        if (footer != null && footer.length > 0) {
            // Write the footer.
            write(footer);
        }
    }

    private void write(final byte[] bytes) throws IOException {
        outputStream.write(bytes, 0, bytes.length);
    }

    protected abstract OutputStream createOutputStream() throws IOException;

    protected void error(final String message, final Exception e) {
        errorReceiverProxy.log(Severity.ERROR, null, getElementId(), message, e);
    }

    abstract long getCurrentOutputSize();

    private Long getSizeBytes() {
        if (sizeBytes == null) {
            sizeBytes = -1L;

            // Set the maximum number of bytes to write before creating a new stream.
            if (size != null && size.trim().length() > 0) {
                try {
                    sizeBytes = ModelStringUtil.parseIECByteSizeString(size);
                } catch (final RuntimeException e) {
                    errorReceiverProxy.log(Severity.ERROR, null, getElementId(), "Unable to parse size: " + size, null);
                }
            }
        }
        return sizeBytes;
    }

    protected void setRollSize(final String size) {
        this.size = size;
    }

    protected void setSplitAggregatedStreams(final boolean splitAggregatedStreams) {
        this.splitAggregatedStreams = splitAggregatedStreams;
    }
}
