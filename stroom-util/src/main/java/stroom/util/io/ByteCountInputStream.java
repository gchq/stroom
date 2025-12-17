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
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;

public class ByteCountInputStream extends WrappedInputStream {

    private final AtomicLong count = new AtomicLong();

    public ByteCountInputStream(final InputStream inputStream) {
        super(inputStream);
    }

    public static ByteCountInputStream wrap(final InputStream inputStream) {
        return new ByteCountInputStream(inputStream);
    }

    @Override
    public int read() throws IOException {
        final int r = super.read();
        if (r >= 0) {
            count.incrementAndGet();
        }
        return r;
    }

    @Override
    public int read(final byte[] b) throws IOException {
        final int r = super.read(b);
        if (r >= 0) {
            count.addAndGet(r);
        }
        return r;
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        final int r = super.read(b, off, len);
        if (r >= 0) {
            count.addAndGet(r);
        }
        return r;
    }

    public long getCount() {
        return count.get();
    }
}
