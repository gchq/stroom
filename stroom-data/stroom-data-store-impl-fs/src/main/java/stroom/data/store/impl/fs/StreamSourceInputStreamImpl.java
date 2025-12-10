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

import stroom.data.store.api.SizeAwareInputStream;

import java.io.IOException;
import java.io.InputStream;

class StreamSourceInputStreamImpl extends SizeAwareInputStream {
    private final InputStream inputStream;
    private final long size;

    StreamSourceInputStreamImpl(final InputStream inputStream, final long size) {
        this.inputStream = inputStream;
        this.size = size;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public int available() throws IOException {
        return inputStream.available();
    }

    @Override
    public void close() throws IOException {
        // Ignore.
    }

    @Override
    public synchronized void mark(final int readlimit) {
        inputStream.mark(readlimit);
    }

    @Override
    public boolean markSupported() {
        return inputStream.markSupported();
    }

    @Override
    public int read() throws IOException {
        return inputStream.read();
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        return inputStream.read(b, off, len);
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public int read(final byte[] b) throws IOException {
        return inputStream.read(b);
    }

    @Override
    public synchronized void reset() throws IOException {
        inputStream.reset();
    }

    @Override
    public long skip(final long n) throws IOException {
        return inputStream.skip(n);
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public final boolean equals(final Object obj) {
        return inputStream.equals(obj);
    }

    @Override
    public final int hashCode() {
        return inputStream.hashCode();
    }

    @Override
    public String toString() {
        return inputStream.toString();
    }
}
