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

package stroom.streamstore.fs.serializable;

import stroom.streamstore.api.StreamTarget;
import stroom.streamstore.fs.StreamTypeNames;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Class used to write multiple streams to a single stream separated by segments
 * by a segment marker (boundary).
 */
public class RANestedOutputStream extends NestedOutputStream {
    private final RASegmentOutputStream segmentOutputStream;
    private int segmentCount = 0;
    private boolean currentEntryClosed = true;
    private boolean closed = false;

    /**
     * Create a default segment output stream for a given target (creates a
     * child stream for you).
     *
     * @param streamTarget to write the data to.
     */
    public RANestedOutputStream(final StreamTarget streamTarget) {
        segmentOutputStream = new RASegmentOutputStream(streamTarget.getOutputStream(),
                streamTarget.addChildStream(StreamTypeNames.BOUNDARY_INDEX).getOutputStream());
    }

    public RANestedOutputStream(final OutputStream dataFile, final OutputStream boundaryFile) {
        segmentOutputStream = new RASegmentOutputStream(dataFile, boundaryFile);
    }

    public int getNestCount() {
        return segmentCount;
    }

    @Override
    public void doClose() throws IOException {
        flush();
        segmentOutputStream.close();
        closed = true;
    }

    public void closeEntry() throws IOException {
        flush();
        checkNotClosed();
        if (currentEntryClosed) {
            throw new IOException("Current nested stream is not open");
        }
        currentEntryClosed = true;
    }

    public void putNextEntry() throws IOException {
        checkNotClosed();
        if (!currentEntryClosed) {
            throw new IOException("Current nested stream is not closed");
        }
        segmentCount++;
        if (segmentCount >= 2) {
            // Start writing segments on the 2nd stream to be added
            segmentOutputStream.addSegment();
        }
        currentEntryClosed = false;
    }

    public final void checkNotClosed() throws IOException {
        if (closed) {
            throw new IOException("Stream has been closed and no more entries allowed");
        }
    }

    @Override
    public void flush() throws IOException {
        segmentOutputStream.flush();
    }

    @Override
    public void write(byte[] b) throws IOException {
        segmentOutputStream.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        segmentOutputStream.write(b, off, len);
    }

    @Override
    public void write(int b) throws IOException {
        segmentOutputStream.write(b);
    }

    @Override
    public String toString() {
        return "RANestedOutputStream" + "\nsegmentCount = " + segmentCount + "\n" + segmentOutputStream;

    }

}
