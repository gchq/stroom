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
import stroom.streamstore.shared.StreamType;
import stroom.util.io.CloseableUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;

/**
 * This class implements the <code>SegmentOutputStream</code> interface and
 * produces output that will be readable with a <code>SegmentInputStream</code>
 * that opens files for random access.
 */
public class RASegmentOutputStream extends OutputStream implements SegmentOutputStream {
    private static final int LONG_BYTES = 8;

    private byte[] buffer;
    private LongBuffer longBuffer;

    private OutputStream dataOutputStream;
    private OutputStream indexOutputStream;
    private long position;
    private long lastBoundary;

    /**
     * Create a default segment output stream for a given target (creates a
     * child stream for you).
     *
     * @param streamTarget to write the data to.
     */
    public RASegmentOutputStream(final StreamTarget streamTarget) {
        this(streamTarget.getOutputStream(), streamTarget.addChildStream(StreamType.SEGMENT_INDEX).getOutputStream());
    }

    public RASegmentOutputStream(final OutputStream dataFile, final OutputStream indexFile) {
        dataOutputStream = dataFile;
        indexOutputStream = indexFile;

        buffer = new byte[LONG_BYTES];
        longBuffer = ByteBuffer.wrap(buffer).asLongBuffer();
    }

    /**
     * Adds a segment boundary to the output stream. All bytes written between
     * the start of the output or the last boundary will be considered a segment
     * by the <code>RASegmentInputStream</code>.
     */
    @Override
    public void addSegment() throws IOException {
        addSegment(this.position);
    }

    /**
     * Adds a segment boundary to the output stream at a given byte position.
     * All bytes written between the start of the output or the last boundary
     * will be considered a segment by the <code>SegmentInputStream</code>.
     *
     * @param position The byte position of the end of the segment.
     */
    @Override
    public void addSegment(final long position) throws IOException {
        assert position >= lastBoundary;

        if (position < lastBoundary) {
            throw new IOException("The boundary position cannot be less than the previous boundary position.");
        }
        lastBoundary = position;

        longBuffer.rewind();
        longBuffer.put(position);
        indexOutputStream.write(buffer);
    }

    /**
     * Gets the current byte position in the output stream.
     */
    @Override
    public long getPosition() {
        return position;
    }

    /**
     * Closes the data and index output streams.
     */
    @Override
    public void close() throws IOException {
        CloseableUtil.close(dataOutputStream, indexOutputStream);
    }

    /**
     * Flushes the data and index output streams.
     */
    @Override
    public void flush() throws IOException {
        try {
            dataOutputStream.flush();
        } finally {
            indexOutputStream.flush();
        }
    }

    /**
     * Writes <code>len</code> bytes from the specified byte array starting at
     * offset <code>off</code> to the data output stream.
     *
     * @param b   the data.
     * @param off the start offset in the data.
     * @param len the number of bytes to write.
     * @throws IOException if an I/O error occurs. In particular, an
     *                     <code>IOException</code> is thrown if the output stream is
     *                     closed.
     */
    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        dataOutputStream.write(b, off, len);
        position += len;
    }

    /**
     * Writes <code>b.length</code> bytes from the specified byte array to the
     * data output stream.
     */
    @Override
    public void write(final byte[] b) throws IOException {
        dataOutputStream.write(b);
        position += b.length;
    }

    /**
     * Writes the specified byte to the data output stream.
     *
     * @param b the <code>byte</code>.
     * @throws IOException if an I/O error occurs. In particular, an
     *                     <code>IOException</code> may be thrown if the output
     *                     stream has been closed.
     */
    @Override
    public void write(final int b) throws IOException {
        dataOutputStream.write(b);
        position++;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("RASegmentOutputStream");
        builder.append("\ndata = ");
        builder.append(dataOutputStream);
        builder.append("\nindex = ");
        builder.append(indexOutputStream);
        builder.append("\nposition = ");
        builder.append(position);
        builder.append("\nlastBoundary = ");
        builder.append(lastBoundary);
        return builder.toString();
    }
}
