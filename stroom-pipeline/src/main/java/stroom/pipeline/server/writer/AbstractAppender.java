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
    @Resource
    private ErrorReceiverProxy errorReceiverProxy;

    private OutputStream outputStream;
    private SegmentOutputStream segmentOutputStream;
    private byte[] header;
    private byte[] footer;

    @Resource
    private PathCreator pathCreator;

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
        return this;
    }

    @Override
    public void returnDestination(final Destination destination) throws IOException {
        // We assume that the parent will only return a destination after
        // writing an entire segment so add a segment
        // marker here.
        insertSegmentMarker();
    }

    @Override
    public final OutputStream getOutputStream() throws IOException {
        return getOutputStream(null, null);
    }

    @Override
    public final OutputStream getOutputStream(final byte[] header, final byte[] footer) throws IOException {
        this.header = header;
        this.footer = footer;

        if (outputStream == null) {
            outputStream = createOutputStream();
            if (outputStream != null && outputStream instanceof SegmentOutputStream) {
                segmentOutputStream = (SegmentOutputStream) outputStream;
            }

            writeHeader();
        }

        return outputStream;
    }

    protected final boolean writeHeader() throws IOException {
        final boolean writtenHeader = write(header);

        if (writtenHeader) {
            // Insert a segment marker after the header.
            insertSegmentMarker();
        }

        return writtenHeader;
    }

    protected final boolean writeFooter() throws IOException {
        return write(footer);
    }

    protected final boolean write(final byte[] bytes) throws IOException {
        if (outputStream != null && bytes != null && bytes.length > 0) {
            outputStream.write(bytes, 0, bytes.length);

            return true;
        }

        return false;
    }

    private final void insertSegmentMarker() throws IOException {
        if (segmentOutputStream != null) {
            segmentOutputStream.addSegment(segmentOutputStream.getPosition());
        }
    }

    protected abstract OutputStream createOutputStream() throws IOException;

    protected void error(final String message, final Exception e) {
        errorReceiverProxy.log(Severity.ERROR, null, getElementId(), message, e);
    }
}
