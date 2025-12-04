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

import java.io.IOException;
import java.io.Reader;

class BufferedReader extends CharBuffer {
    private final int initialSize;
    private final int capacity;
    private final int halfCapacity;

    private Reader reader;
    private boolean eof;

    BufferedReader(final Reader reader, final int initialSize, final int capacity) {
        super(initialSize);
        this.reader = reader;

        if (initialSize < 8) {
            throw new IllegalStateException("The initial size must be greater than or equal to 8");
        }
        if (capacity < initialSize) {
            throw new IllegalStateException("Capacity must be greater than or equal to " + initialSize);
        }

        this.capacity = capacity;
        this.initialSize = initialSize;
        this.halfCapacity = capacity / 2;
    }

    void fillBuffer() throws IOException {
        // Only fill the buffer if we haven't reached the end of the file.
        if (!eof) {
            int i = 0;

            // Only fill the buffer if the length of remaining characters is
            // less than half the capacity.
            if (length <= halfCapacity) {
                // If we processed more than half of the capacity then we need
                // to shift the buffer up so that the offset becomes 0. This
                // will give us
                // room to fill more of the buffer.
                if (offset >= halfCapacity) {
                    System.arraycopy(buffer, offset, buffer, 0, length);
                    offset = 0;
                }

                // Now fill any remaining capacity.
                int maxLength = capacity - offset;
                while (maxLength > length && (i = reader.read()) != -1) {
                    final char c = (char) i;

                    // If we have filled the buffer then double the size of
                    // it up to the maximum capacity.
                    if (buffer.length == offset + length) {
                        int newLen = buffer.length * 2;
                        if (newLen == 0) {
                            newLen = initialSize;
                        } else if (newLen > capacity) {
                            newLen = capacity;
                        }
                        final char[] tmp = new char[newLen];
                        System.arraycopy(buffer, offset, tmp, 0, length);
                        offset = 0;
                        buffer = tmp;

                        // Now the offset has been reset to 0 we should set
                        // the max length to be the maximum capacity.
                        maxLength = capacity;
                    }

                    buffer[offset + length] = c;
                    length++;
                }
            }

            // Determine if we reached the end of the file.
            eof = i == -1;
        }
    }

    public boolean isEof() {
        return eof;
    }
}
