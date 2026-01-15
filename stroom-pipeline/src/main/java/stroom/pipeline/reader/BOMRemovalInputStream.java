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

package stroom.pipeline.reader;

import stroom.util.io.WrappedInputStream;

import java.io.IOException;
import java.io.InputStream;

public class BOMRemovalInputStream extends WrappedInputStream {
    private static final String UTF_8 = "UTF-8";
    private static final String UTF_16LE = "UTF-16LE";
    private static final String UTF_16BE = "UTF-16BE";
    private static final String UTF_32LE = "UTF-32LE";
    private static final String UTF_32BE = "UTF-32BE";

    private static final byte[] UTF_8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] UTF_16LE_BOM = {(byte) 0xFF, (byte) 0xFE};
    private static final byte[] UTF_16BE_BOM = {(byte) 0xFE, (byte) 0xFF};
    private static final byte[] UTF_32LE_BOM = {(byte) 0xFF, (byte) 0xFE, (byte) 0x0, (byte) 0x0};
    private static final byte[] UTF_32BE_BOM = {(byte) 0x0, (byte) 0x0, (byte) 0xFE, (byte) 0xFF};

    private final byte[] bom;
    private int pos = 0;

    public BOMRemovalInputStream(final InputStream inputStream, final String encoding) {
        super(inputStream);

        if (encoding != null) {
            if (encoding.equalsIgnoreCase(UTF_8)) {
                bom = UTF_8_BOM;
            } else if (encoding.equalsIgnoreCase(UTF_16LE)) {
                bom = UTF_16LE_BOM;
            } else if (encoding.equalsIgnoreCase(UTF_16BE)) {
                bom = UTF_16BE_BOM;
            } else if (encoding.equalsIgnoreCase(UTF_32LE)) {
                bom = UTF_32LE_BOM;
            } else if (encoding.equalsIgnoreCase(UTF_32BE)) {
                bom = UTF_32BE_BOM;
            } else {
                bom = null;
            }
        } else {
            bom = null;
        }
    }

    @Override
    public int read() throws IOException {
        if (bom != null && pos < bom.length) {
            int b = super.read();

            // If we are at the end of the stream then get out of here.
            if (b == -1) {
                return b;
            }

            while (pos < bom.length) {
                if ((byte) b == bom[pos]) {
                    b = super.read();
                    pos++;
                } else {
                    pos++;
                    return b;
                }
            }

            pos++;
            return b;
        } else {
            return super.read();
        }
    }

    @Override
    public int read(final byte[] buffer) throws IOException {
        return read(buffer, 0, buffer.length);
    }

    @Override
    public int read(final byte[] buffer, final int off, final int len) throws IOException {
        if (bom != null && pos < bom.length) {
            final byte[] buf = new byte[buffer.length];
            int lenRead = super.read(buf, off, len);

            // If we are at the end of the stream then get out of here.
            if (lenRead == -1) {
                return lenRead;
            }

            int strip = 0;
            int i = 0;
            int p = pos;
            pos += lenRead;

            while (p < bom.length && i < lenRead) {
                if (buf[off + p] == bom[p]) {
                    strip++;
                }

                p++;
                i++;
            }

            lenRead = lenRead - strip;
            System.arraycopy(buf, off + strip, buffer, off, lenRead);

            // Empty Stream
            if (lenRead == 0) {
                lenRead = -1;
            }

            return lenRead;

        } else {
            // Don't care about pos here as it is already bigger than
            // bom.length.
            return super.read(buffer, off, len);
        }
    }

    @Override
    public synchronized void reset() throws IOException {
        pos = 0;
        super.reset();
    }

    @Override
    public long skip(final long n) throws IOException {
        pos += n;
        return super.skip(n);
    }

    /**
     * Removes the byte order mark from the stream, if it exists and returns the
     * encoding name.
     */
    protected String consumeBOM(final InputStream stream, final String encoding) throws IOException {
        final byte[] b = new byte[3];
        int count = 0;
        stream.mark(3);
        if (encoding.equals("UTF-8")) {
            count = stream.read(b, 0, 3);
            if (count == 3) {
                final int b0 = b[0] & 0xFF;
                final int b1 = b[1] & 0xFF;
                final int b2 = b[2] & 0xFF;
                if (b0 != 0xEF || b1 != 0xBB || b2 != 0xBF) {
                    // First three bytes are not BOM, so reset.
                    stream.reset();
                }
            } else {
                stream.reset();
            }
        } else if (encoding.startsWith("UTF-16")) {
            count = stream.read(b, 0, 2);
            if (count == 2) {
                final int b0 = b[0] & 0xFF;
                final int b1 = b[1] & 0xFF;
                if (b0 == 0xFE && b1 == 0xFF) {
                    return "UTF-16BE";
                } else if (b0 == 0xFF && b1 == 0xFE) {
                    return "UTF-16LE";
                }
            }
            // First two bytes are not BOM, so reset.
            stream.reset();
        }
        // We could do UTF-32, but since the getEncodingName() doesn't support
        // that
        // we won't support it here.
        // To implement UTF-32, look for: 00 00 FE FF for big-endian
        // or FF FE 00 00 for little-endian
        return encoding;
    }

}
