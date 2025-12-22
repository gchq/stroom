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

import stroom.util.io.FileUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * @see BlockGZIPConstants
 */
class BlockGZIPInputFile extends BlockGZIPInput {

    // File being read
    private final FileChannel raFile;

    // File pointer
    private final Path file;

    /**
     * Constructor to open a Block GZIP File.
     */
    BlockGZIPInputFile(final Path bgz) throws IOException {
        this.raFile = FileChannel.open(bgz, StandardOpenOption.READ);
        try {
            this.file = bgz;
            raFile.position(0);
            init();

        } catch (final IOException e) {
            raFile.close();
            throw e;
        }
    }

    /**
     * Constructor to open a Block GZIP File with a internal buffer size.
     */
    BlockGZIPInputFile(final Path bgz, final int rawBufferSize) throws IOException {
        super(rawBufferSize);
        this.raFile = FileChannel.open(bgz, StandardOpenOption.READ);
        try {
            this.file = bgz;

            raFile.position(0);
            init();

        } catch (final IOException e) {
            raFile.close();
            throw e;
        }
    }

    static void main(final String[] args) throws IOException {
        final BlockGZIPInputFile is = new BlockGZIPInputFile(Paths.get(args[0]));

        final byte[] buffer = new byte[1024];
        int len;

        while ((len = is.read(buffer)) != -1) {
            System.out.write(buffer, 0, len);
        }

        System.out.flush();
    }

    /**
     * Here we provide random access into a stream.
     */
    @Override
    public long skip(final long n) throws IOException {
        // The first seek we do we check the index
        if (!checkedIndex) {
            // Record the current position in case we don't switch blocks
            final long originalPos = raFile.position();
            final BlockBufferedInputStream originalRawBuffer = currentRawStreamBuffer;
            raFile.position(idxStart);
            // Must create a new buffer as we switch back the old one
            currentRawStreamBuffer = createBufferedInputStream(false);
            readMagicMarker();
            checkedIndex = true;
            // Move Back
            raFile.position(originalPos);
            // Switch back the old one
            currentRawStreamBuffer = originalRawBuffer;
        }

        final long currentBlockNumber = position / blockSize;

        // Find the new offset in the file.
        position = position + n;

        if (position > dataLength) {
            throw new IOException("Seek past EOF");
        }
        if (position < 0) {
            throw new IOException("Seek past begining of file");
        }

        // Then figure out block and offset
        final long newBlockNumber = position / blockSize;
        final long newBlockOffset = position % blockSize;

        // Moving block?
        if ((currentBlockNumber != newBlockNumber)) {
            // Read our index
            raFile.position(idxStart
                    + BlockGZIPConstants.LONG_BYTES
                    + (newBlockNumber * BlockGZIPConstants.LONG_BYTES));
            currentRawStreamBuffer = createBufferedInputStream(true);
            final long seekPos = readLong();
            raFile.position(seekPos);
            currentRawStreamBuffer = createBufferedInputStream(true);
            startGzipBlock();
            currentStream.skip(newBlockOffset);
        } else {
            // Start a BGZIP block
            if (currentStream == null) {
                startGzipBlock();
            }

            // Still in the same block
            currentStream.skip(n);
        }

        // We always can do the full skip
        return n;
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
            raFile.close();
        } finally {
            super.close();
        }
    }

    @Override
    public String toString() {
        return "BGZIP " + file + " blockSize=" + blockSize + " fileSize=" + eof;
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
        final long newPosition = Math.min(pos, dataLength);
        final long offset = newPosition - position;
        // If +ve then we can skip.
        if (offset > 0) {
            skip(offset);
        }
        // Going back
        if (offset < 0) {
            // Force a reload

            // Then figure out block and offset
            final long blockNumber = newPosition / blockSize;
            final long blockOffset = newPosition % blockSize;

            // Read our index
            raFile.position(idxStart + BlockGZIPConstants.LONG_BYTES + (blockNumber * BlockGZIPConstants.LONG_BYTES));
            currentRawStreamBuffer = createBufferedInputStream(true);
            final long seekPos = readLong();
            raFile.position(seekPos);
            currentRawStreamBuffer = createBufferedInputStream(true);
            startGzipBlock();
            currentStream.skip(blockOffset);

            position = newPosition;
        }
    }

    /**
     * Where we are at in the stream.
     */
    @Override
    public long getPosition() {
        return position;
    }

    @Override
    protected InputStream getRawStream() {
        return Channels.newInputStream(raFile);
    }

    @Override
    void invalid(final String message) throws IOException {
        throw new IOException(message + " \"" + FileUtil.getCanonicalPath(file) + "\"");
    }
}
