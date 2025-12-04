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
import stroom.util.io.SeekableOutputStream;
import stroom.util.io.StreamCloser;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Output stream that writes a lock file with the data and then renames to the
 * final file once closed.
 * <p>
 * If lazy is set on it also does not create the file until some data is written
 * (thus the file does not exist if no data was written).
 */
class LockingFileOutputStream extends OutputStream implements SeekableOutputStream {
    private final Path finalFile;
    private final Path lockFile;
    private OutputStream outputStream;
    private long bytesWritten = 0;
    private boolean closed = false;

    // Use to help track non-closed streams
    private StreamCloser streamCloser = new BasicStreamCloser();

    LockingFileOutputStream(final Path file, final boolean lazy) throws IOException {
        this.finalFile = file;
        Files.deleteIfExists(file);
        lockFile = Paths.get(file.toAbsolutePath().normalize().toString() + BlockGZIPConstants.LOCK_EXTENSION);
        Files.deleteIfExists(lockFile);
        if (!lazy) {
            getOutputStream();
        }
    }

    private OutputStream getOutputStream() throws IOException {
        if (outputStream == null) {
            if (closed) {
                throw new IOException("stream closed");
            }
            outputStream = new BufferedOutputStream(Files.newOutputStream(lockFile),
                    FileSystemUtil.STREAM_BUFFER_SIZE);
            streamCloser.add(outputStream);
        }
        return outputStream;
    }

    @Override
    public void close() throws IOException {
        closed = true;

        try {
            streamCloser.close();
        } finally {
            super.close();

            if (outputStream != null) {
                Files.move(lockFile, finalFile);
                outputStream = null;
            }
        }
    }

    @Override
    public void write(final int b) throws IOException {
        getOutputStream().write(b);
        bytesWritten++;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public void write(final byte[] b) throws IOException {
        getOutputStream().write(b);
        bytesWritten += b.length;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        getOutputStream().write(b, off, len);
        bytesWritten += len;
    }

    @Override
    public String toString() {
        return "BGZIP@" + finalFile + "@" + bytesWritten;
    }

    @Override
    public long getPosition() {
        return bytesWritten;
    }

    @Override
    public long getSize() {
        return bytesWritten;
    }

    @Override
    public void seek(final long pos) {
        throw new UnsupportedOperationException();
    }
}
