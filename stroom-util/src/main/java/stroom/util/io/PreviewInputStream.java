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

public class PreviewInputStream extends InputStream {

    private final InputStream inputStream;
    private byte[] buffer;
    private int bufOffset;
    private int bufLength;

    public PreviewInputStream(final InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public int read() throws IOException {
        final int b;
        if (buffer == null) {
            b = inputStream.read();
        } else {
            if (bufOffset < bufLength) {
                // the byte is a signed int so convert to unsigned to meet the contract for read()
                b = buffer[bufOffset++] & 0xff;
            } else {
                buffer = null;
                b = inputStream.read();
            }
        }
        return b;
    }

    public int read(final byte[] b, final int off, final int len) throws IOException {
        int length = len;

        if (buffer == null) {
            // There is no buffer so read from the input stream.
            length = inputStream.read(b, off, length);

        } else {
            // Calculate the number of bytes left in the buffer.
            final int bytesLeft = bufLength - bufOffset;

            if (bytesLeft <= 0) {
                buffer = null;

                // If there are no bytes left in the buffer then read from the
                // input stream.
                length = inputStream.read(b, off, length);

            } else {
                // Get data from the buffer.
                if (length > bytesLeft) {
                    length = bytesLeft;
                }

                System.arraycopy(buffer, bufOffset, b, off, length);

                bufOffset += length;
            }
        }

        return length;
    }

    public long skip(final long len) throws IOException {
        long length = len;
        if (buffer == null) {
            // There is no buffer so skip the input stream.
            length = inputStream.skip(length);
        } else {
            // Calculate the number of bytes left in the buffer.
            final int bytesLeft = bufLength - bufOffset;

            if (bytesLeft <= 0) {
                buffer = null;

                // If there are no bytes left in the buffer then skip the input
                // stream.
                length = inputStream.skip(length);
            } else {
                bufOffset += length;
                final long rem = length - bytesLeft;
                if (rem > 0) {
                    buffer = null;
                    length = inputStream.skip(rem) + bytesLeft;
                }
            }
        }

        return length;
    }

    public int available() throws IOException {
        final int bytesLeft = bufLength - bufOffset;
        if (bytesLeft <= 0) {
            return inputStream.available();
        }
        return bytesLeft;
    }

    public void mark(final int howMuch) {
        throw new UnsupportedOperationException("Mark is not supported");
    }

    public boolean markSupported() {
        return false;
    }

    public void close() throws IOException {
        if (inputStream != null) {
            inputStream.close();
        }
        buffer = null;
    }

    public String previewAsString(final int limit, final String encoding) throws IOException {
        fill(limit);
        return new String(buffer, encoding);
    }

    private void fill(final int limit) throws IOException {
        int off = 0;
        int len = 0;
        int rem = limit;
        final byte[] b = new byte[limit];
        while ((len = read(b, off, rem)) != -1 && rem > 0) {
            off += len;
            rem -= len;
        }

        if (off == limit) {
            buffer = b;
        } else {
            final byte[] tmp = new byte[off];
            System.arraycopy(b, 0, tmp, 0, tmp.length);
            buffer = tmp;
        }

        bufOffset = 0;
        bufLength = buffer.length;
    }
}
