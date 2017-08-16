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

package stroom.streamstore.server.fs;

import stroom.io.SeekableInputStream;
import stroom.io.StreamCloser;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.zip.GZIPInputStream;

/**
 * @see BlockGZIPConstants
 */
public abstract class BlockGZIPInput extends InputStream implements SeekableInputStream {
    // Use to help track non-closed streams
    private final StreamCloser streamCloser = new StreamCloser();
    /**
     * Pointer to the current GZIPstream
     */
    protected GZIPInputStream currentStream;
    /**
     * We read data into a buffer (rather than hit the RA file)
     */
    protected BlockBufferedInputStream currentRawStreamBuffer;
    /**
     * Size of our read buffer (can be set by caller)
     */
    protected Integer rawBufferSize = null;
    /**
     * Header info
     */
    protected int blockSize;
    protected int blockCount = 0;
    protected long idxStart;
    protected long dataLength;
    protected long eof;
    /**
     * When using seek etc we check the index.
     */
    protected boolean checkedIndex = false;
    /**
     * Used a a buffer to read longs into
     */
    protected byte[] longRawBuffer = new byte[BlockGZIPConstants.LONG_BYTES];
    protected LongBuffer longBuffer = ByteBuffer.wrap(longRawBuffer).asLongBuffer();
    protected byte[] magicMarkerRawBufffer = new byte[BlockGZIPConstants.MAGIC_MARKER.length];
    protected byte[] headerMarkerRawBuffer = new byte[BlockGZIPConstants.BLOCK_GZIP_V1_IDENTIFIER.length];
    protected long position = 0;
    protected long lastMarkPosition = 0;
    /**
     * Used for debug purposes
     */
    protected long currentBlockRawGzipSize = 0;

    /**
     * Constructor to open a Block GZIP File.
     */
    public BlockGZIPInput() throws IOException {
    }

    /**
     * Constructor to open a Block GZIP File with a internal buffer size.
     */
    public BlockGZIPInput(final int rawBufferSize) throws IOException {
        this.rawBufferSize = rawBufferSize;
    }

    /**
     * @return for our inner classes
     */
    BufferedInputStream getCurrentRawStreamBuffer() {
        return currentRawStreamBuffer;
    }

    /**
     * @return for our inner classes
     */
    int getCurrentBlockRawGzipSize() {
        return (int) currentBlockRawGzipSize;
    }

    /**
     * Load the file.
     */
    protected void init() throws IOException {
        // Create a buffer for the reading
        currentRawStreamBuffer = createBufferedInputStream(true);

        // Check Header Marker
        readHeaderMarker();

        // Read Header
        blockSize = (int) readLong();
        dataLength = readLong();
        idxStart = readLong();
        eof = readLong();

        // Make sure the stream is closed.
        streamCloser.add(currentRawStreamBuffer);
    }

    protected abstract InputStream getRawStream();

    /**
     * @param recycle can we reuse the last buffer?
     * @return create a buffer for accessing the RA file
     */
    protected BlockBufferedInputStream createBufferedInputStream(final boolean recycle) {
        // Avoid allocating a new buffer
        if (recycle && currentRawStreamBuffer != null) {
            currentRawStreamBuffer.recycle(getRawStream());
            return currentRawStreamBuffer;
        }
        // Use a defined buffer size?
        if (rawBufferSize != null) {
            return new BlockBufferedInputStream(getRawStream(), rawBufferSize.intValue());
        }
        return new BlockBufferedInputStream(getRawStream());
    }

    /**
     * Do fill a buffer to an exact size.
     */
    protected void fillBuffer(final InputStream stream, final byte[] buffer, final int offset, final int len)
            throws IOException {
        final int realLen = stream.read(buffer, offset, len);

        if (realLen == -1) {
            throw new IOException("Unable to fill buffer");
        }
        if (realLen != len) {
            // Try Again
            fillBuffer(stream, buffer, offset + realLen, len - realLen);
        }
    }

    /**
     * Do fill a buffer to an exact size.
     */
    protected void fillFromRawStreamBuffer(final byte[] buffer) throws IOException {
        fillBuffer(currentRawStreamBuffer, buffer, 0, buffer.length);
    }

    /**
     * Read a long from the stream
     */
    protected long readLong() throws IOException {
        longBuffer.rewind();
        fillFromRawStreamBuffer(longRawBuffer);
        return longBuffer.get();
    }

    /**
     * Check the contents of a buffer are what is expected.
     */
    protected boolean checkEqualBuffer(final byte[] lhs, final byte[] rhs) {
        if (lhs.length != rhs.length) {
            return false;
        }
        for (int i = 0; i < lhs.length; i++) {
            if (lhs[i] != rhs[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Read a marker from the stream.
     */
    protected void readMagicMarker() throws IOException {
        fillFromRawStreamBuffer(magicMarkerRawBufffer);
        if (!checkEqualBuffer(BlockGZIPConstants.MAGIC_MARKER, magicMarkerRawBufffer)) {
            throw new IOException("Failed to find block sync point " + blockCount);
        }
    }

    /**
     * Read a header marker from the stream.
     */
    protected void readHeaderMarker() throws IOException {
        fillFromRawStreamBuffer(headerMarkerRawBuffer);
        if (!checkEqualBuffer(BlockGZIPConstants.BLOCK_GZIP_V1_IDENTIFIER, headerMarkerRawBuffer)) {
            throw new IOException("Does not look like a Block GZIP V1 Stream");
        }
    }

    /**
     * Read 1 byte of uncompressed data.
     */
    @Override
    public int read() throws IOException {
        // Hit the logical EOF?
        if (position >= dataLength) {
            return -1;
        }

        // Need to open a GZIP block?
        if (currentStream == null) {
            startGzipBlock();
        }

        // Call the GZIP stream
        final int rtn = currentStream.read();

        // Read a byte?
        if (rtn != -1) {
            position++;
        }

        // Need to end the block?
        if ((position % blockSize) == 0) {
            endGzipBlock();
        }
        return rtn;
    }

    /**
     * End the BGZIP block
     */
    protected void endGzipBlock() throws IOException {
        // We know we have hit the end of the BGZIP stream but we MUST
        // read another byte so that GZIP reads to the end.
        if (currentStream.read() != -1) {
            throw new IOException("Gzip Had More To Come!");
        }
        currentBlockRawGzipSize = -1;

        currentStream = null;
    }

    /**
     * Start a BGZIP block
     */
    protected void startGzipBlock() throws IOException {
        blockCount++;
        readMagicMarker();
        currentBlockRawGzipSize = readLong();

        currentStream = new GZIPInputStream(new GzipInputStreamAdaptor());
        streamCloser.add(currentStream);
    }

    /**
     * Read X bytes of uncompressed data
     */
    @Override
    public int read(final byte[] bytes) throws IOException {
        return read(bytes, 0, bytes.length);
    }

    /**
     * Read some uncompressed data from our stream.
     */
    @Override
    public int read(final byte[] bytes, final int off, final int tryLen) throws IOException {
        int realLen = tryLen;

        // Are we going to read past the length of the file?
        if ((position + tryLen) > dataLength) {
            // Trim the length
            realLen = (int) (dataLength - position);
        }
        // Have we already past the EOF ?
        if (position >= dataLength) {
            return -1;
        }

        // How many bytes can we read from this block.
        final int bytesLeftInBlock = (blockSize - (int) (position % blockSize));

        // Start a BGZIP block
        if (currentStream == null) {
            startGzipBlock();
        }

        // You need to take care the calling read() may not read the full length
        // of the buffer So we have to ensure we increment our position by
        // realLen.

        // Only ever read from one block (make the caller do another read())
        // So don't read more bytes than we have left
        realLen = Math.min(bytesLeftInBlock, realLen);

        // Read the GZIP strip
        realLen = currentStream.read(bytes, off, realLen);
        if (realLen != -1) {
            position += realLen;
            // Hit the end
            if ((position % blockSize) == 0) {
                endGzipBlock();
            }
        }
        return realLen;
    }

    /**
     * Here we provide random access into a stream.
     */
    @Override
    public long skip(final long n) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * Mark this stream so we can go back to this position.
     */
    @Override
    public void mark(final int readlimit) {
        lastMarkPosition = position;
    }

    /**
     * Move back to last mark position.
     */
    @Override
    public void reset() throws IOException {
        seek(lastMarkPosition);
    }

    @Override
    public void close() throws IOException {
        try {
            streamCloser.close();
        } catch (final IOException e) {
            throw e;
        } finally {
            super.close();
        }
    }

    /**
     * @return the full size of this stream
     */
    @Override
    public long getSize() {
        return dataLength;
    }

    /**
     * Seek to a position in this stream.
     */
    @Override
    public void seek(final long pos) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * Where we are at in the stream.
     */
    @Override
    public long getPosition() {
        return position;
    }

    public int getBlockCount() {
        return blockCount;
    }

    /**
     * Adaptor to create a stream over the raw buffer and ensures that we don't
     * read more than we are allowed to (for the gzip stream)
     */
    class GzipInputStreamAdaptor extends InputStream {
        private int bytesRead;

        // Start a new adaptor.
        public GzipInputStreamAdaptor() {
            this.bytesRead = 0;
        }

        @Override
        public int read() throws IOException {
            if (bytesRead < getCurrentBlockRawGzipSize()) {
                bytesRead++;
                return getCurrentRawStreamBuffer().read();
            }
            return -1;
        }

        @Override
        public int read(final byte[] b, final int off, final int tryLen) throws IOException {
            // Make sure we don't max
            final long maxSize = getCurrentBlockRawGzipSize();
            int realLen = tryLen;
            if (tryLen + bytesRead > maxSize) {
                // Trim the length
                realLen = (int) (maxSize - bytesRead);
                // Nothing more to read
                if (realLen == 0) {
                    return -1;
                }
            }
            // Do the read
            final int realBytesRead = getCurrentRawStreamBuffer().read(b, off, realLen);
            // We may end up reading a smaller amount
            bytesRead += realBytesRead;
            return realBytesRead;
        }
    }
}
