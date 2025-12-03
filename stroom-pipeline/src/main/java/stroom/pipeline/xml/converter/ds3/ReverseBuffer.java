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

package stroom.pipeline.xml.converter.ds3;

public class ReverseBuffer implements Buffer {
    private static final char SPACE = ' ';

    private static final int DEFAULT_INITIAL_SIZE = 1000;

    int offset;
    int length;
    char[] buffer;

    /**
     * Create a buffer with the default initial size and capacity.
     */
    public ReverseBuffer() {
        this(DEFAULT_INITIAL_SIZE);
    }

    /**
     * Create a buffer with a specified initial size.
     *
     * @param initialSize The initial size of the buffer.
     */
    public ReverseBuffer(final int initialSize) {
        if (initialSize <= 0) {
            throw new IllegalStateException("Initial size must be greater than 0");
        }

        // Create the buffer.
        buffer = new char[initialSize];
    }

    public ReverseBuffer(final char[] buffer, final int off, final int len) {
        this.buffer = buffer;
        this.offset = off;
        this.length = len;
    }

    @Override
    public void clear() {
        length = 0;
    }

    @Override
    public Buffer trimStart() {
        final int initialStart = offset - length + 1;
        final int initialEnd = offset;

        int start = initialStart;
        final int end = initialEnd;

        while (start <= end && buffer[start] <= SPACE) {
            start++;
        }

        if (start == initialStart) {
            return this;
        }

        return subSequence(initialEnd - end, end - start + 1);
    }

    @Override
    public Buffer trimEnd() {
        final int initialStart = offset - length + 1;
        final int initialEnd = offset;

        final int start = initialStart;
        int end = initialEnd;

        while (end >= start && buffer[end] <= SPACE) {
            end--;
        }

        if (end == initialEnd) {
            return this;
        }

        return subSequence(initialEnd - end, end - start + 1);
    }

    @Override
    public Buffer trim() {
        final int initialStart = offset - length + 1;
        final int initialEnd = offset;

        int start = initialStart;
        int end = initialEnd;

        while (start <= end && buffer[start] <= SPACE) {
            start++;
        }
        while (end >= start && buffer[end] <= SPACE) {
            end--;
        }

        if (start == initialStart && end == initialEnd) {
            return this;
        }

        return subSequence(initialEnd - end, end - start + 1);
    }

    @Override
    public boolean isEmpty() {
        return length == 0;
    }

    @Override
    public boolean isBlank() {
        if (isEmpty()) {
            return true;
        }

        int start = offset - length + 1;
        final int end = offset;

        while (start <= end && buffer[start] <= SPACE) {
            start++;
        }

        return start > end;
    }

    @Override
    public String toString() {
        return new String(buffer, offset - length + 1, length);
    }

    @Override
    public char charAt(final int index) {
        return buffer[offset - index];
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public Buffer subSequence(final int off, final int len) {
        return new CharBuffer(buffer, offset - off - len + 1, len);
    }

    @Override
    public Buffer reverse() {
        return new ReverseBuffer(buffer, offset, length);
    }

    @Override
    public Buffer unsafeCopy() {
        return new ReverseBuffer(buffer, offset, length);
    }

    @Override
    public Buffer copy() {
        final char[] chars = toCharArray();
        return new ReverseBuffer(chars, chars.length - 1, chars.length);
    }

    @Override
    public char[] toCharArray() {
        final char[] chars = new char[length];
        System.arraycopy(buffer, length - offset - 1, chars, 0, length);
        return chars;
    }

    @Override
    public void move(final int increment) {
        offset -= increment;
        length -= increment;
    }

    @Override
    public void remove(final int start, final int end) {
        final int removeLength = end - start;
        assert removeLength > 0;
        final char[] chars = new char[length - removeLength];
        System.arraycopy(buffer, offset, chars, 0, end);
        System.arraycopy(buffer, offset + start, chars, end, length - start);
        buffer = chars;
        length = chars.length;
        offset = length - 1;
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof ReverseBuffer) {
            final ReverseBuffer buffer = (ReverseBuffer) other;
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
