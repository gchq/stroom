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

import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;

public class WrappedReader extends Reader {

    private final Reader reader;

    public WrappedReader(final Reader reader) {
        this.reader = reader;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    @Override
    public void mark(final int readAheadLimit) throws IOException {
        reader.mark(readAheadLimit);
    }

    @Override
    public boolean markSupported() {
        return reader.markSupported();
    }

    @Override
    public int read() throws IOException {
        return reader.read();
    }

    @Override
    public int read(final char[] cbuf, final int off, final int len) throws IOException {
        return reader.read(cbuf, off, len);
    }

    @Override
    public int read(final char[] cbuf) throws IOException {
        return reader.read(cbuf);
    }

    @Override
    public int read(final CharBuffer target) throws IOException {
        return reader.read(target);
    }

    @Override
    public boolean ready() throws IOException {
        return reader.ready();
    }

    @Override
    public void reset() throws IOException {
        reader.reset();
    }

    @Override
    public long skip(final long n) throws IOException {
        return reader.skip(n);
    }

    @Override
    public boolean equals(final Object obj) {
        return reader.equals(obj);
    }

    @Override
    public int hashCode() {
        return reader.hashCode();
    }

    @Override
    public String toString() {
        return reader.toString();
    }
}
