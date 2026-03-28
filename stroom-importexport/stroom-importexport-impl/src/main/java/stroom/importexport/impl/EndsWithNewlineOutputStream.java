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

package stroom.importexport.impl;

import org.jspecify.annotations.NonNull;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Output stream to ensure that a file ends with a newline to confirm with POSIX.
 * Adds a newline if necessary.
 */
public class EndsWithNewlineOutputStream extends FilterOutputStream {

    /** Record of last character written */
    private int lastByte = '\0';

    /**
     * Constructor.
     * @param out The output stream that must end with a newline.
     */
    public EndsWithNewlineOutputStream(final OutputStream out) {
        super(out);
    }

    /**
     * Intercepts writes through the filter and stores the last character.
     */
    @Override
    public void write(final int b) throws IOException {
        lastByte = b;
        super.write(b);
    }

    /**
     * Intercepts all byte[] writes through the filter and stores the last character.
     */
    @Override
    public void write(final byte @NonNull [] buf,
                      final int off,
                      final int len) throws IOException {

        if (buf == null) {
            throw new NullPointerException("b is null");
        }
        if (off < 0) {
            throw new IndexOutOfBoundsException("Offset is negative");
        }
        if (len < 0) {
            throw new IndexOutOfBoundsException("len is negative");
        }
        if (off + len > buf.length) {
            throw new IndexOutOfBoundsException("Offset + length is greater than buffer length");
        }

        // Index defined by OutputStream.write(byte[], int, int) documentation
        lastByte = buf[off + len - 1];
        super.out.write(buf, off, len);
    }

    /**
     * Ensures that the file ends with a newline, then closes the stream.
     */
    @Override
    public void close() throws IOException {
        super.flush();
        if (lastByte != '\n') {
            super.out.write('\n');
        }
        super.close();
    }

}
