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

import stroom.util.io.BasicStreamCloser;
import stroom.util.io.FileUtil;
import stroom.util.io.SeekableOutputStream;
import stroom.util.io.StreamCloser;

import jakarta.validation.constraints.NotNull;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * @see BlockGZIPConstants
 */
class BlockGZIPOutputFile extends OutputStream implements SeekableOutputStream {

    // We have in built locking while open
    private final Path finalFile;
    private final Path lockFile;

    // The file we write to
    private final FileChannel raFile;

    // The main buffer used (typically holds 2 longs and the the GZIP output).
    // We use 'big' buffer (that holds the whole block) as we go back and write
    // the block size at the end of processing.
    private final BlockByteArrayOutputStream mainBuffer;
    // Our index buffer we append on at the end.
    private final BlockByteArrayOutputStream indexBuffer;
    // Use to help track non-closed streams
    private final StreamCloser streamCloser = new BasicStreamCloser();
    // The stream - we hold a buffer onto it as well
    private BufferedOutputStream currentStreamBuffer;
    private GzipCompressorOutputStream currentStreamGzip;
    // The block size we are using
    private final int blockSize;
    // The current 'logical' uncompressed data item we have written
    private long position = 0;
    // The current block number we are on
    private long blockCount = 0;
    // ((blockCount+1) * blockSize)
    private long currentBlockEndPos = 0;
    private boolean closed;

    /**
     * @see BlockGZIPConstants
     */
    BlockGZIPOutputFile(final Path file) throws IOException {
        this(file, BlockGZIPConstants.DEFAULT_BLOCK_SIZE);
    }

    /**
     * @see BlockGZIPConstants
     */
    BlockGZIPOutputFile(final Path file, final int blockSize) throws IOException {
        this.blockSize = blockSize;
        this.mainBuffer = new BlockByteArrayOutputStream();
        this.indexBuffer = new BlockByteArrayOutputStream();

        // Mark the start of the index with a magic marker
        indexBuffer.write(BlockGZIPConstants.MAGIC_MARKER);

        this.finalFile = file;
        this.lockFile = file.getParent().resolve(file.getFileName().toString() + BlockGZIPConstants.LOCK_EXTENSION);

        FileUtil.deleteFile(finalFile);
        FileUtil.deleteFile(lockFile);

        this.raFile = FileChannel.open(
                lockFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE);
        try {
            // Write a marker
            mainBuffer.write(BlockGZIPConstants.BLOCK_GZIP_V1_IDENTIFIER);
            // At the start of the block file write the block size an empty place
            // for the index offset and the marker
            // we
            mainBuffer.writeLong(blockSize);
            // Uncompressed Data Length
            mainBuffer.writeLong(0);
            // Index POS
            mainBuffer.writeLong(0);
            // End POS
            mainBuffer.writeLong(0);

            flushMainBuffer();

            // Make sure the streams are closed.
            streamCloser.add(mainBuffer).add(indexBuffer).add(raFile);

        } catch (final IOException e) {
            streamCloser.close();
            raFile.close();
            throw e;
        }
    }

    /**
     * Write the buffer to the file and reset it.
     */
    private void flushMainBuffer() throws IOException {
        raFile.write(ByteBuffer.wrap(mainBuffer.getRawBuffer(), 0, mainBuffer.size()));
        mainBuffer.reset();
    }

    /**
     * @return Our current position (in uncompressed bytes)
     */
    @Override
    public long getPosition() {
        return position;
    }

    long getBlockCount() {
        return blockCount;
    }

    /**
     * Start a new burst of GZIP Block Content (with a marker).
     */
    private void endGzipBlock() throws IOException {
        blockCount++;

        currentStreamBuffer.flush();
        currentStreamGzip.flush();
        currentStreamGzip.finish();

        // Block Compressed size is size of stream less magic marker less block
        // size header (2 longs)
        final long rawBlockSize = mainBuffer.size() - BlockGZIPConstants.LONG_BYTES - BlockGZIPConstants.LONG_BYTES;
        mainBuffer.overwriteLongAtOffset(BlockGZIPConstants.LONG_BYTES, rawBlockSize);

        flushMainBuffer();

        currentStreamBuffer = null;
        currentStreamGzip = null;
    }

    private void startGzipBlock() throws IOException {
        // At what point to we start a new block
        currentBlockEndPos = (blockCount + 1) * blockSize;

        // Record the start Pos
        final long currentRawBlockStartPos = raFile.position();

        // Record the index
        indexBuffer.writeLong(currentRawBlockStartPos);

        // Marker
        mainBuffer.write(BlockGZIPConstants.MAGIC_MARKER);

        // Write some bytes for the long we will do later
        mainBuffer.writeLong(0);

        // Connect a new GZIP stream
        currentStreamGzip = new GzipCompressorOutputStream(mainBuffer);
        currentStreamBuffer = new BufferedOutputStream(currentStreamGzip, FileSystemUtil.STREAM_BUFFER_SIZE);
    }

    @Override
    public void write(final int b) throws IOException {
        if (currentStreamBuffer == null) {
            startGzipBlock();
        }
        // Write a single byte
        currentStreamBuffer.write(b);
        position++;

        // Have we moved onto the next block?
        if (position == currentBlockEndPos) {
            endGzipBlock();
        }
    }

    @Override
    public void write(@NotNull final byte[] b) throws IOException {
        // Delegate
        write(b, 0, b.length);
    }

    @Override
    public void write(@NotNull final byte[] bytes, final int offset, final int length) throws IOException {
        if (currentStreamBuffer == null) {
            startGzipBlock();
        }

        // Find out how many bytes are left to write in the current block
        final int bytesLeftInBlock = (int) (currentBlockEndPos - position);

        // These bytes will fit in this block
        if (length <= bytesLeftInBlock) {
            currentStreamBuffer.write(bytes, offset, length);
            position += length;

            if (length == bytesLeftInBlock) {
                endGzipBlock();
            }

        } else {
            // We need to split this up - write the first half
            currentStreamBuffer.write(bytes, offset, bytesLeftInBlock);
            position += bytesLeftInBlock;
            endGzipBlock();
            // Now have ago again with the reminder
            write(bytes, offset + bytesLeftInBlock, length - bytesLeftInBlock);
        }

    }

    @Override
    public void close() throws IOException {
        try {
            if (!closed) {
                closed = true;

                if (currentStreamBuffer != null) {
                    // End the data stream
                    endGzipBlock();
                }

                // Record where we are going to start writing the index
                final long idxStart = raFile.position();

                // Append the Index
                raFile.write(ByteBuffer.wrap(indexBuffer.getRawBuffer(), 0, indexBuffer.size()));

                // Now Record the EOF
                final long eof = raFile.size();

                // Seek back to the start to write the above stats.
                // Write the Index Post back in the header
                raFile.position(BlockGZIPConstants.BLOCK_GZIP_V1_IDENTIFIER.length + BlockGZIPConstants.LONG_BYTES);
                // Write the uncompressed stream size

                mainBuffer.reset();
                // Size of Uncompressed Data
                mainBuffer.writeLong(position);
                // And the Index Start POS
                mainBuffer.writeLong(idxStart);
                // And the End File Pos
                mainBuffer.writeLong(eof);

                flushMainBuffer();

                raFile.close();

                try {
                    Files.move(lockFile, finalFile);
                } catch (final IOException e) {
                    throw new IOException("Failed to rename lock file " + lockFile, e);
                }
            }
        } finally {
            try {
                streamCloser.close();
            } finally {
                super.close();
            }
        }
    }

    @Override
    public void flush() throws IOException {
        // We ignore flush to the file as the stream store does not allow it for
        // performance reasons.

        // We only flush to our buffer (not all the way to the file)
        if (currentStreamBuffer != null) {
            currentStreamBuffer.flush();
        }
    }

    @Override
    public long getSize() {
        return getPosition();
    }

    @Override
    public void seek(final long pos) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return "BGZIP@" + finalFile + "@" + position;
    }
}
