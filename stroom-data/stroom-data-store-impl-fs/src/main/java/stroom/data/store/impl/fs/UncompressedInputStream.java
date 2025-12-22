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
import stroom.util.io.SeekableInputStream;
import stroom.util.io.StreamCloser;

import jakarta.validation.constraints.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * A stream that interfaces with a random access file.
 * <p>
 * if lazy it is assumed that a missing file means a blank stream.
 */
class UncompressedInputStream extends InputStream implements SeekableInputStream {
    private final FileChannel raFile;
    private final BlockBufferedInputStream streamAdaptor;
    private long position;
    private long lastMarkPosition;

    // Use to help track non-closed streams
    private final StreamCloser streamCloser = new BasicStreamCloser();

    UncompressedInputStream(final Path file, final boolean lazy) throws IOException {
        FileChannel fileChannel = null;
        BlockBufferedInputStream blockBufferedInputStream = null;

        if (!lazy || Files.isRegularFile(file)) {
            fileChannel = FileChannel.open(file, StandardOpenOption.READ);
            blockBufferedInputStream = new BlockBufferedInputStream(Channels.newInputStream(fileChannel));
            streamCloser.add(fileChannel).add(blockBufferedInputStream);
        }

        raFile = fileChannel;
        streamAdaptor = blockBufferedInputStream;
    }

    /**
     * @return byte or -1
     */
    @Override
    public int read() throws IOException {
        if (streamAdaptor == null) {
            // LAZY
            return -1;
        } else {
            final int rtn = streamAdaptor.read();
            if (rtn != -1) {
                position++;
            }
            return rtn;
        }
    }

    /**
     * @param b to fill
     */
    @Override
    public int read(@NotNull final byte[] b) throws IOException {
        if (streamAdaptor == null) {
            // LAZY
            return -1;
        } else {
            final int read = streamAdaptor.read(b);
            if (read != -1) {
                position += read;
            }
            return read;
        }
    }

    /**
     * @param b   to fill
     * @param off offset
     * @param len length
     */
    @Override
    public int read(@NotNull final byte[] b, final int off, final int len) throws IOException {
        if (streamAdaptor == null) {
            // LAZY
            return -1;
        } else {
            final int read = streamAdaptor.read(b, off, len);
            if (read != -1) {
                position += read;
            }
            return read;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            streamCloser.close();
        } finally {
            super.close();
        }
    }

    @Override
    public long getPosition() {
        return position;
    }

    @Override
    public long getSize() throws IOException {
        if (raFile == null) {
            // LAZY empty
            return 0;
        } else {
            return raFile.size();
        }
    }

    @Override
    public void seek(final long pos) throws IOException {
        position = pos;
        if (raFile != null) {
            raFile.position(pos);
            streamAdaptor.recycle(Channels.newInputStream(raFile));
        }
    }

    @Override
    public void mark(final int readlimit) {
        lastMarkPosition = position;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public void reset() throws IOException {
        seek(lastMarkPosition);
    }

    /**
     * @param n bytes to skip
     * @return how many we skipped
     */
    @Override
    public long skip(final long n) throws IOException {
        seek(position + n);
        return n;
    }

}
