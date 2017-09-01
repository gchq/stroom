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

import stroom.io.SeekableOutputStream;
import stroom.io.StreamCloser;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Output stream that writes a lock file with the data and then renames to the
 * final file once closed.
 * <p>
 * If lazy is set on it also does not create the file until some data is written
 * (thus the file does not exist if no data was written).
 */
public class LockingFileOutputStream extends OutputStream implements SeekableOutputStream {
    private final File finalFile;
    private final File lockFile;
    private OutputStream fileOutputStream;
    private long bytesWritten = 0;
    private boolean closed = false;

    // Use to help track non-closed streams
    private StreamCloser streamCloser = new StreamCloser();

    public LockingFileOutputStream(File file, boolean lazy) throws IOException {
        this.finalFile = file;
        if (finalFile.isFile()) {
            file.delete();
        }
        lockFile = new File(file.getAbsolutePath() + BlockGZIPConstants.LOCK_EXTENSION);
        if (lockFile.isFile()) {
            lockFile.delete();
        }
        if (!lazy) {
            getFileOutputStream();
        }
    }

    private OutputStream getFileOutputStream() throws IOException {
        if (fileOutputStream == null) {
            if (closed) {
                throw new IOException("stream closed");
            }
            fileOutputStream = new BufferedOutputStream(new FileOutputStream(lockFile),
                    FileSystemUtil.STREAM_BUFFER_SIZE);
            streamCloser.add(fileOutputStream);
        }
        return fileOutputStream;
    }

    @Override
    public void close() throws IOException {
        closed = true;

        try {
            streamCloser.close();
        } catch (final IOException e) {
            throw e;
        } finally {
            super.close();

            if (fileOutputStream != null) {
                lockFile.renameTo(finalFile);
                fileOutputStream = null;
            }
        }
    }

    @Override
    public void write(int b) throws IOException {
        getFileOutputStream().write(b);
        bytesWritten++;
    }

    @Override
    public void write(byte[] b) throws IOException {
        getFileOutputStream().write(b);
        bytesWritten += b.length;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        getFileOutputStream().write(b, off, len);
        bytesWritten += len;
    }

    @Override
    public String toString() {
        return "BGZIP@" + finalFile + "@" + bytesWritten;
    }

    @Override
    public long getPosition() throws IOException {
        return bytesWritten;
    }

    @Override
    public long getSize() throws IOException {
        return bytesWritten;
    }

    @Override
    public void seek(long pos) throws IOException {
        throw new UnsupportedOperationException();
    }
}
