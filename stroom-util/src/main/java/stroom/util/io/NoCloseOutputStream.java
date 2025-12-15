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

package stroom.util.io;


import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * An {@link OutputStream} that does not pass the {@link OutputStream#close()} call
 * down to the delegate {@link OutputStream}.
 */
public class NoCloseOutputStream extends FilterOutputStream {

    private final OutputStream outputStream;

    /**
     * Creates an output stream filter built on top of the specified
     * underlying output stream.
     *
     * @param outputStream the underlying output stream to be assigned to
     *                     the field {@code this.out} for later use, or
     *                     {@code null} if this instance is to be
     *                     created without an underlying stream.
     */
    public NoCloseOutputStream(final OutputStream outputStream) {
        super(outputStream);
        this.outputStream = outputStream;
    }

    @Override
    public void write(final int b) throws IOException {
        outputStream.write(b);
    }

    @Override
    public void write(final byte[] b) throws IOException {
        outputStream.write(b);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        outputStream.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        outputStream.flush();
    }

    @Override
    public void close() throws IOException {
        // Ignore
    }
}
