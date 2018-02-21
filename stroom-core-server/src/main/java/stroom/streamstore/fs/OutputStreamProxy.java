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

package stroom.streamstore.fs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Proxy class to aid AOP timings.
 */
public class OutputStreamProxy extends OutputStream {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutputStreamProxy.class);

    private static final int LOG_AT_BYTES = 1000000;
    private long nextLogAtBytes = LOG_AT_BYTES;
    private long wroteBytes = 0;

    private OutputStream delegate;

    @Override
    public void write(final byte[] arg0) throws IOException {
        wroteBytes += arg0.length;
        logIfRequired();
        delegate.write(arg0);
    }

    @Override
    public void write(final byte[] arg0, final int arg1, final int len) throws IOException {
        wroteBytes += len;
        logIfRequired();
        delegate.write(arg0, arg1, len);
    }

    @Override
    public void write(final int arg0) throws IOException {
        wroteBytes++;
        logIfRequired();
        delegate.write(arg0);
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    public void setDelegate(final OutputStream delegate) {
        this.delegate = delegate;
    }

    private void logIfRequired() {
        if (wroteBytes > nextLogAtBytes) {
            LOGGER.info("Wrote " + wroteBytes);
            nextLogAtBytes = wroteBytes + LOG_AT_BYTES;
        }
    }
}
