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

package stroom.data.store.impl;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class LocationAwareInputStream extends FilterInputStream {

    private long firstOffsetInLastRead = -1;
    private long lastOffsetInLastRead = -1;
    private long lastReadSize = -1;
    private long mark = -1;

    /**
     * Creates a <code>FilterInputStream</code>
     * by assigning the  argument <code>in</code>
     * to the field <code>this.in</code> so as
     * to remember it for later use.
     *
     * @param in the underlying input stream, or <code>null</code> if
     *           this instance is to be created without an underlying stream.
     */
    public LocationAwareInputStream(final InputStream in) {
        super(in);
    }

    @Override
    public int read() throws IOException {
        final int result = in.read();
        if (result != -1) {
            lastReadSize = 1;
            firstOffsetInLastRead++;
            lastOffsetInLastRead = firstOffsetInLastRead;
        }
        return result;
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        final int result = in.read(b, off, len);
        if (result != -1) {
            lastReadSize = result;
            firstOffsetInLastRead++;
            lastOffsetInLastRead += result;
        }
        return result;
    }

    @Override
    public long skip(final long n) throws IOException {
        final long result = in.skip(n);
        lastReadSize = -1;
        firstOffsetInLastRead += result;
        lastOffsetInLastRead = firstOffsetInLastRead;
        return result;
    }

    @Override
    public void mark(final int readlimit) {
        in.mark(readlimit);
        mark = firstOffsetInLastRead;
        // it's okay to mark even if mark isn't supported, as reset won't work
    }

    @Override
    public void reset() throws IOException {
        if (!in.markSupported()) {
            throw new IOException("Mark not supported");
        }
        if (mark == -1) {
            throw new IOException("Mark not set");
        }

        in.reset();
        firstOffsetInLastRead = mark;
    }

    public long getLastReadSize() {
        return lastReadSize;
    }

    /**
     * Inclusive
     */
    public long getFirstOffsetInLastRead() {
        return firstOffsetInLastRead;
    }

    /**
     * Inclusive
     */
    public long getLastOffsetInLastRead() {
        return lastOffsetInLastRead;
    }
}
