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

package stroom.data.store.impl.fs;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Class used to write multiple streams to a single stream separated by segments
 * by a segment marker (boundary).
 */
class RANestedOutputStream extends OutputStream {
    private final RASegmentOutputStream segmentOutputStream;
    private int segmentCount = 0;
    private boolean currentEntryClosed = true;
    private boolean closed = false;

    RANestedOutputStream(final OutputStream dataFile, final SupplierWithIO<OutputStream> indexOutputStreamSupplier) {
        segmentOutputStream = new RASegmentOutputStream(dataFile, indexOutputStreamSupplier);
    }

    int getNestCount() {
        return segmentCount;
    }

    void closeEntry() throws IOException {
        flush();
        checkNotClosed();
        if (currentEntryClosed) {
            throw new IOException("Current nested stream is not open");
        }
        currentEntryClosed = true;
    }

    void putNextEntry() throws IOException {
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

    private void checkNotClosed() throws IOException {
        if (closed) {
            throw new IOException("Stream has been closed and no more entries allowed");
        }
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public void write(final byte[] b) throws IOException {
        segmentOutputStream.write(b);
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        segmentOutputStream.write(b, off, len);
    }

    @Override
    public void write(final int b) throws IOException {
        segmentOutputStream.write(b);
    }

    @Override
    public void flush() throws IOException {
        segmentOutputStream.flush();
    }

    @Override
    public void close() throws IOException {
        flush();
        segmentOutputStream.close();
        closed = true;
    }

    @Override
    public String toString() {
        return "RANestedOutputStream" + "\nsegmentCount = " + segmentCount + "\n" + segmentOutputStream;
    }
}
