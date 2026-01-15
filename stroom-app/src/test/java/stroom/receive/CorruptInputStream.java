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

package stroom.receive;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Used for tests to simulate an error during IO.
 */
public class CorruptInputStream extends FilterInputStream {

    private final int errorAtByte;
    private int count;

    public CorruptInputStream(final InputStream in, final int errorAtByte) {
        super(in);
        this.errorAtByte = errorAtByte;
        this.count = 0;
    }

    /**
     * read some bytes.
     */
    @Override
    public int read() throws IOException {
        final int read = super.read();
        if (read >= 0) {
            count++;
            if (count >= errorAtByte) {
                throw new IOException("Expected IO Junit Error at byte " + count);
            }
        }

        return read;
    }

    /**
     * read some bytes.
     */
    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        final int read = read();
        if (read >= 0) {
            b[off] = (byte) read;
            return 1;
        }
        return -1;
    }
}
