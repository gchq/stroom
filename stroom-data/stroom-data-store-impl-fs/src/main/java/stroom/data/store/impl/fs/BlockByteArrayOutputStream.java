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

package stroom.data.store.impl.fs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;

/**
 * Utility class that wraps a ByteArrayOutputStream but allows access to the raw
 * byte array. Also provides a methods to write long's and also over write a
 * long in part of the buffer.
 */
class BlockByteArrayOutputStream extends ByteArrayOutputStream {
    /**
     * Used for the long conversion
     */
    private byte[] longRawBuffer = new byte[BlockGZIPConstants.LONG_BYTES];
    private LongBuffer longBuffer = ByteBuffer.wrap(longRawBuffer).asLongBuffer();

    /**
     * @param initialSize used if you have a good idea how big you want us to be
     */
    BlockByteArrayOutputStream(final int initialSize) {
        super(initialSize);
    }

    BlockByteArrayOutputStream() {
    }

    byte[] getRawBuffer() {
        return buf;
    }

    /**
     * Helper to append a long.
     *
     * @param value long to write
     */
    void writeLong(final long value) throws IOException {
        longBuffer.rewind();
        longBuffer.put(value);
        this.write(longRawBuffer);
    }

    /**
     * Helper to over write a long The buffer must be as big as the offset.
     *
     * @param offset to use
     * @param value  long to write
     */
    void overwriteLongAtOffset(final int offset, final long value) {
        longBuffer.rewind();
        longBuffer.put(value);
        System.arraycopy(longRawBuffer, 0, this.getRawBuffer(), offset, BlockGZIPConstants.LONG_BYTES);
    }
}
