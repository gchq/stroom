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

import java.io.IOException;
import java.io.OutputStream;

import javax.annotation.Resource;

import stroom.pipeline.destination.Destination;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.streamstore.server.fs.serializable.SegmentOutputStream;
import stroom.util.shared.Severity;

public abstract class AbstractAppender extends AbstractDestinationProvider implements Destination {
    private final ErrorReceiverProxy errorReceiverProxy;

    private OutputStream outputStream;
    private SegmentOutputStream segmentOutputStream;
    private byte[] footer;

    AbstractAppender(final ErrorReceiverProxy errorReceiverProxy) {
        this.errorReceiverProxy = errorReceiverProxy;
    }

    @Override
    public void endProcessing() {
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
        }

        super.endProcessing();
    }

    @Override
    public Destination borrowDestination() throws IOException {
        // We assume that the parent will write an entire segment when it borrows a destination so add a segment marker
        // here before a segment is written.
        insertSegmentMarker();

        return this;
    }

    @Override
    public void returnDestination(final Destination destination) throws IOException {
    }

    @Override
    public final OutputStream getOutputStream() throws IOException {
        return getOutputStream(null, null);
    }

    @Override
    public final OutputStream getOutputStream(final byte[] header, final byte[] footer) throws IOException {
        this.footer = footer;

        if (outputStream == null) {
            outputStream = createOutputStream();
            if (outputStream != null && outputStream instanceof SegmentOutputStream) {
                segmentOutputStream = (SegmentOutputStream) outputStream;
            }

            // If we haven't written yet then create the output stream and
            // write a header if we have one.
            if (header != null && header.length > 0) {
                // Write the header.
                write(header);
            }

            // Insert a segment marker before we write the next record.
            insertSegmentMarker();
        }

        return outputStream;
    }

    private void writeFooter() throws IOException {
        if (footer != null && footer.length > 0) {
            // Insert a segment marker before the footer.
            insertSegmentMarker();

            // Write the footer.
            write(footer);
        }
    }

    private void write(final byte[] bytes) throws IOException {
        outputStream.write(bytes, 0, bytes.length);
    }

    private void insertSegmentMarker() throws IOException {
        // Add a segment marker to the output stream if we are segmenting.
        if (segmentOutputStream != null && segmentOutputStream.getPosition() > 0) {
            segmentOutputStream.addSegment();
        }
    }

    protected abstract OutputStream createOutputStream() throws IOException;

    protected void error(final String message, final Exception e) {
        errorReceiverProxy.log(Severity.ERROR, null, getElementId(), message, e);
    }
}
