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

package stroom.pipeline.writer;

import stroom.pipeline.destination.Destination;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

public abstract class AbstractAppender extends AbstractDestinationProvider implements Destination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractAppender.class);

    private final ErrorReceiverProxy errorReceiverProxy;

    private Output output;
    private byte[] header;
    private byte[] footer;
    private boolean writtenHeader;
    private String size;
    private Long sizeBytes = null;
    boolean splitAggregatedStreams;
    boolean splitRecords;

    public AbstractAppender(final ErrorReceiverProxy errorReceiverProxy) {
        this.errorReceiverProxy = errorReceiverProxy;
    }

    @Override
    public void endProcessing() {
        writeFooter(true);
        super.endProcessing();
    }

    @Override
    public void endStream() {
        if (splitAggregatedStreams) {
            writeFooter(false);
        }
        super.endStream();
    }

    @Override
    public Destination borrowDestination() throws IOException {
        return this;
    }

    @Override
    public void returnDestination(final Destination destination) throws IOException {
        // We assume that the parent will write an entire segment when it borrows a destination so add a segment marker
        // here after a segment is written.

        // Writing a segment marker here ensures there is always a marker written before the footer regardless or
        // whether a footer is actually written. We do this because we always make an allowance for a footer for data
        // display purposes.
        insertSegmentMarker();

        if (splitRecords) {
            if (getCurrentOutputSize() > 0) {
                writeFooter(false);
            }
        } else {
            final Long sizeBytes = getSizeBytes();
            if (sizeBytes > 0 && getCurrentOutputSize() >= sizeBytes) {
                writeFooter(true);
            }
        }
    }

    @Override
    public final OutputStream getOutputStream() throws IOException {
        return getOutputStream(null, null);
    }

    @Override
    public OutputStream getOutputStream(final byte[] header, final byte[] footer) throws IOException {
        this.header = header;
        this.footer = footer;

        if (output == null) {
            LOGGER.debug("Creating output");
            output = createOutput();
        }

        // If we haven't written yet then create the output stream and
        // write a header if we have one.
        writeHeader();

        return output.getOutputStream();
    }

    Output getOutput() {
        return output;
    }

    /**
     * Method to allow subclasses to insert segment markers between records.
     */
    private void insertSegmentMarker() throws IOException {
        LOGGER.debug("insertSegmentMarker - output: {}", output);
        if (output != null) {
            output.insertSegmentMarker();
        }
    }

    void writeHeader() throws IOException {
        LOGGER.debug("Writing footer - output: {}, writtenHeader: {}, header: \n{}",
                output, writtenHeader, header);
        if (!writtenHeader) {
            if (output != null) {
                // If we are writing to a zip then start a new zip entry before writing the header.
                if (output.isZip()) {
                    output.startZipEntry();
                }

                if (header != null && header.length > 0) {
                    try {
                        // Write the header.
                        output.write(header);
                    } catch (final IOException e) {
                        error(e.getMessage(), e);
                    }
                }

                // Insert a segment marker before we write the next record regardless of whether the header has actually
                // been written. This is because we always make an allowance for the existence of a header in a
                // segmented stream when viewing data.
                insertSegmentMarker();
            }

            writtenHeader = true;
        }
    }

    void writeFooter(final boolean roll) {
        LOGGER.debug("Writing footer - roll: {}, output: {}, writtenHeader: {}, footer: \n{}",
                roll, output, writtenHeader, footer);
        if (output != null) {
            if (writtenHeader) {
                try {
                    if (footer != null && footer.length > 0) {
                        // Write the footer.
                        output.write(footer);
                    }
                } catch (final IOException e) {
                    error(e.getMessage(), e);
                } finally {
                    writtenHeader = false;
                }
            }

            try {
                if (output.isZip()) {
                    output.endZipEntry();
                    if (roll) {
                        closeCurrentOutputStream();
                    }
                } else {
                    closeCurrentOutputStream();
                }
            } catch (final IOException e) {
                error(e.getMessage(), e);
                throw new UncheckedIOException(e);
            } catch (final RuntimeException e) {
                error(e.getMessage(), e);
                throw e;
            }
        }
    }

    private void closeCurrentOutputStream() {
        try {
            LOGGER.debug("Closing output: {}", output);
            output.close();
        } catch (final IOException e) {
            error(e.getMessage(), e);
        } finally {
            output = null;
        }
    }

    protected abstract Output createOutput() throws IOException;

    protected void error(final String message) {
        error(message, null);
    }

    protected void error(final String message, final Exception e) {
        if (errorReceiverProxy != null) {
            errorReceiverProxy.log(Severity.ERROR, null, getElementId(), message, e);
        } else {
            LOGGER.error(message, e);
        }
    }

    protected void fatal(final String message) {
        fatal(message, null);
    }

    protected void fatal(final String message, final Exception e) {
        if (errorReceiverProxy != null) {
            errorReceiverProxy.log(Severity.FATAL_ERROR, null, getElementId(), message, e);
        } else {
            LOGGER.error(message, e);
        }
        throw ProcessException.create(message);
    }

    long getCurrentOutputSize() {
        return output == null
                ? 0L
                : output.getCurrentOutputSize();
    }

    private Long getSizeBytes() {
        if (sizeBytes == null) {
            sizeBytes = -1L;

            // Set the maximum number of bytes to write before creating a new stream.
            if (!NullSafe.isBlankString(size)) {
                try {
                    sizeBytes = ModelStringUtil.parseIECByteSizeString(size);
                } catch (final RuntimeException e) {
                    errorReceiverProxy.log(Severity.ERROR, null, getElementId(),
                            "Unable to parse size: " + size, null);
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

    protected void setSplitRecords(final boolean splitRecords) {
        this.splitRecords = splitRecords;
    }
}
