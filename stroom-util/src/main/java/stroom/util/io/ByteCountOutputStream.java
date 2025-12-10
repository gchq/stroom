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
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicLong;

public class ByteCountOutputStream extends WrappedOutputStream {

    private final AtomicLong count = new AtomicLong();
    private boolean hasBytesWritten = false;

    public ByteCountOutputStream(final OutputStream outputStream) {
        super(outputStream);
    }

    @Override
    public void write(final int b) throws IOException {
        count.incrementAndGet();
        super.write(b);
    }

    @Override
    public void write(final byte[] b) throws IOException {
        count.addAndGet(b.length);
        super.write(b);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        count.addAndGet(len);

        // If any bytes are to be written, set the `hasBytesWritten` flag to `true`.
        // This allows the caller to determine whether the stream header has already been written.
        if (len > 0) {
            hasBytesWritten = true;
        }

        super.write(b, off, len);
    }

    public long getCount() {
        return count.get();
    }

    public boolean getHasBytesWritten() {
        return hasBytesWritten;
    }
}
