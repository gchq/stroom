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

package stroom.streamstore.store.impl.fs;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;

/**
 * Class used to check a Block GZIP file.
 */
public class BlockGZIPFileVerifier {
    // File being read
    private final RandomAccessFile raFile;
    private final InputStream stream;

    // Used a a buffer to read longs into
    private byte[] longRawBuffer = new byte[BlockGZIPConstants.LONG_BYTES];
    private LongBuffer longBuffer = ByteBuffer.wrap(longRawBuffer).asLongBuffer();
    private byte[] magicMarkerRawBufffer = new byte[BlockGZIPConstants.MAGIC_MARKER.length];
    private byte[] headerMarkerRawBuffer = new byte[BlockGZIPConstants.BLOCK_GZIP_V1_IDENTIFIER.length];

    private int blockSize;
    private long idxStart;
    private long dataLength;
    private long eof;

    /**
     * Constructor to open a Block GZIP File.
     */
    public BlockGZIPFileVerifier(final Path bgz) throws IOException {
        raFile = new RandomAccessFile(bgz.toFile(), BlockGZIPConstants.READ_ONLY);
        stream = new RAInputStreamAdaptor();
    }

    public static void main(String[] args) throws IOException {
        new BlockGZIPFileVerifier(Paths.get(args[0])).verify();
    }

    RandomAccessFile getRaFile() {
        return raFile;
    }

    /**
     * Test the file.
     */
    public void verify() throws IOException {
        raFile.seek(0);

        readHeaderMarker();

        // Read Header
        blockSize = (int) readLong();
        dataLength = readLong();
        idxStart = readLong();
        eof = readLong();

        long numberOfBlocks = dataLength / blockSize;

        System.out.println("Header Info");
        System.out.println("===========");
        System.out.println("blockSize=" + blockSize);
        System.out.println("dataLength=" + dataLength);
        System.out.println("idxStart=" + idxStart);
        System.out.println("eof=" + eof);
        System.out.println("numberOfBlocks=" + numberOfBlocks);

        System.out.println("Block Markers");
        System.out.println("=============");

        ArrayList<Long> blockStarts = new ArrayList<>();
        for (int i = 0; i <= numberOfBlocks; i++) {
            long pos = raFile.getChannel().position();
            readMagicMarker(pos);
            long gzipSize = readLong();
            System.out.println("Block " + i + " Starts at " + pos + " and raw data is " + gzipSize);
            raFile.skipBytes((int) gzipSize);
            blockStarts.add(pos);
        }

        System.out.println("Index Markers");
        System.out.println("=============");

        long pos = raFile.getChannel().position();
        if (pos != idxStart) {
            throw new IOException("Corrupt Index");
        }

        readMagicMarker(pos);
        for (int i = 0; i <= numberOfBlocks; i++) {
            long indexPos = readLong();
            long realPos = blockStarts.get(i);

            if (indexPos != realPos) {
                throw new IOException("Corrupt Index");
            }
            System.out.println("Block " + i + " Index " + realPos + " is OK");
        }

        System.out.println("GZIP Content");
        System.out.println("=============");

        BlockByteArrayOutputStream buffer = new BlockByteArrayOutputStream();

        for (int i = 0; i <= numberOfBlocks; i++) {
            readMagicMarker(blockStarts.get(i));
            int gzipSize = (int) readLong();

            buffer.reset();

            fillBuffer(stream, buffer, gzipSize);

            System.out.println("Checking Block " + i + " GZIP Format");

            ByteArrayInputStream is = new ByteArrayInputStream(buffer.getRawBuffer(), 0, buffer.size());
            GZIPInputStream gzip = new GZIPInputStream(is);
            int byteRead = 0;
            while ((byteRead = gzip.read()) != -1) {
                // Do something to get around check style
                byteRead += byteRead;
            }

        }
    }

    private void fillBuffer(final InputStream stream, final BlockByteArrayOutputStream buffer, final int len)
            throws IOException {
        int byteRead = 0;
        int bytesRead = 0;
        while ((byteRead = stream.read()) != -1) {
            bytesRead++;
            buffer.write(byteRead);
            if (bytesRead >= len) {
                return;
            }
        }
    }

    private void fillBuffer(final InputStream stream, final byte[] buffer, final int offset, final int len)
            throws IOException {
        int realLen = stream.read(buffer, offset, len);

        if (realLen == -1) {
            throw new IOException("Unable to fill buffer");
        }
        if (realLen != len) {
            // Try Again
            fillBuffer(stream, buffer, offset + realLen, len - realLen);
        }
    }

    private long readLong() throws IOException {
        longBuffer.rewind();
        fillBuffer(stream, longRawBuffer, 0, longRawBuffer.length);
        return longBuffer.get();
    }

    private boolean checkEqualBuffer(final byte[] lhs, final byte[] rhs) {
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

    private void readMagicMarker(long pos) throws IOException {
        raFile.seek(pos);

        fillBuffer(stream, magicMarkerRawBufffer, 0, magicMarkerRawBufffer.length);
        if (!checkEqualBuffer(BlockGZIPConstants.MAGIC_MARKER, magicMarkerRawBufffer)) {
            byte[] rawBuffer = new byte[magicMarkerRawBufffer.length + 200];

            getRaFile().seek(Math.max(0, pos - 10));
            int bufSize = getRaFile().read(rawBuffer, 0, rawBuffer.length);

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Failed to find block sync point at ");
            stringBuilder.append(pos);
            stringBuilder.append(" eof is ");
            stringBuilder.append(getRaFile().length());
            stringBuilder.append(". bytes starting at ");
            stringBuilder.append(pos - 10);
            stringBuilder.append(" are ");
            for (int i = 0; i < bufSize; i++) {
                stringBuilder.append(rawBuffer[i]);
                stringBuilder.append(" ");
            }

            throw new IOException(stringBuilder.toString());
        }
    }

    private void readHeaderMarker() throws IOException {
        fillBuffer(stream, headerMarkerRawBuffer, 0, headerMarkerRawBuffer.length);
        if (!checkEqualBuffer(BlockGZIPConstants.BLOCK_GZIP_V1_IDENTIFIER, headerMarkerRawBuffer)) {
            throw new IOException("Does not look like a Block GZIP V1 Stream");
        }
    }

    /**
     * Class to interface a stream to a random access file.
     */
    class RAInputStreamAdaptor extends InputStream {
        @Override
        public int read() throws IOException {
            return getRaFile().read();
        }

        @Override
        public int read(final byte[] b) throws IOException {
            return getRaFile().read(b);
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            return getRaFile().read(b, off, len);
        }
    }

}
