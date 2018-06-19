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

package stroom.data.store.impl.fs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Proxy class to aid AOP timings.
 */
public class InputStreamProxy extends InputStream {
    private static final Logger LOGGER = LoggerFactory.getLogger(InputStreamProxy.class);

    private static final int LOG_AT_BYTES = 1000000;
    private long nextLogAtBytes = LOG_AT_BYTES;
    private long readBytes = 0;

    private InputStream delegate;

    public void setDelegate(final InputStream delegate) {
        this.delegate = delegate;
    }

    @Override
    public int available() throws IOException {
        return delegate.available();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public void mark(final int arg0) {
        delegate.mark(arg0);
    }

    @Override
    public boolean markSupported() {
        return delegate.markSupported();
    }

    @Override
    public int read() throws IOException {
        readBytes++;
        logIfRequired();
        return delegate.read();
    }

    @Override
    public int read(final byte[] arg0) throws IOException {
        int read = delegate.read(arg0);
        readBytes += read;
        logIfRequired();
        return read;
    }

    @Override
    public int read(final byte[] arg0, final int arg1, final int arg2) throws IOException {
        int read = delegate.read(arg0, arg1, arg2);
        readBytes += read;
        logIfRequired();
        return read;
    }

    @Override
    public void reset() throws IOException {
        delegate.reset();
    }

    @Override
    public long skip(final long arg0) throws IOException {
        return delegate.skip(arg0);
    }

    private void logIfRequired() {
        if (readBytes > nextLogAtBytes) {
            LOGGER.info("Read " + readBytes);
            nextLogAtBytes = readBytes + LOG_AT_BYTES;
        }
    }
}
