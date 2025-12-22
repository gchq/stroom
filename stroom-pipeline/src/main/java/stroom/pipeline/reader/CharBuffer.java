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

class CharBuffer implements CharSequence {
    private static final int DEFAULT_INITIAL_SIZE = 1000;

    int offset;
    int length;
    char[] buffer;

    /**
     * Create a buffer with the default initial size and capacity.
     */
    CharBuffer() {
        this(DEFAULT_INITIAL_SIZE);
    }

    /**
     * Create a buffer with a specified initial size.
     *
     * @param initialSize The initial size of the buffer.
     */
    CharBuffer(final int initialSize) {
        if (initialSize <= 0) {
            throw new IllegalStateException("Initial size must be greater than 0");
        }

        // Create the buffer.
        buffer = new char[initialSize];
    }

    CharBuffer(final char[] buffer, final int off, final int len) {
        this.buffer = buffer;
        this.offset = off;
        this.length = len;
    }

    @Override
    public String toString() {
        return new String(buffer, offset, length);
    }

    @Override
    public char charAt(final int index) {
        return buffer[offset + index];
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public CharBuffer subSequence(final int off, final int len) {
        return new CharBuffer(buffer, offset + off, len);
    }

    public void move(final int increment) {
        offset += increment;
        length -= increment;
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof CharBuffer) {
            final CharBuffer buffer = (CharBuffer) other;
            final int len1 = length;
            final int len2 = buffer.length;

            if (len1 != len2) {
                return false;
            }
            for (int i = 0; i < len1; i++) {
                if (charAt(i) != buffer.charAt(i)) {
                    return false;
                }
            }
            return true;
        } else if (other instanceof CharSequence) {
            return length() == ((CharSequence) other).length() && toString().equals(other.toString());
        }
        return false;
    }

    @Override
    public int hashCode() {
        // Same algorithm as String#hashCode(), but not cached as this class is
        // mutable.
        int h = 0;
        for (int i = offset; i < length; i++) {
            h = 31 * h + buffer[i];
        }
        return h;
    }
}
