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

public class CharBuffer implements Buffer {
    private static final char SPACE = ' ';

    private static final int DEFAULT_INITIAL_SIZE = 1000;

    int offset;
    int length;
    char[] buffer;

    /**
     * Create a buffer with the default initial size and capacity.
     */
    public CharBuffer() {
        this(DEFAULT_INITIAL_SIZE);
    }

    /**
     * Create a buffer with a specified initial size.
     *
     * @param initialSize The initial size of the buffer.
     */
    public CharBuffer(final int initialSize) {
        if (initialSize <= 0) {
            throw new IllegalStateException("Initial size must be greater than 0");
        }

        // Create the buffer.
        buffer = new char[initialSize];
    }

    public CharBuffer(final char[] buffer, final int off, final int len) {
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
        final int initialStart = offset;
        final int initialEnd = offset + length - 1;

        int start = initialStart;
        final int end = initialEnd;

        while (start <= end && buffer[start] <= SPACE) {
            start++;
        }

        if (start == initialStart) {
            return this;
        }

        return subSequence(start - initialStart, end - start + 1);
    }

    @Override
    public Buffer trimEnd() {
        final int initialStart = offset;
        final int initialEnd = offset + length - 1;

        final int start = initialStart;
        int end = initialEnd;

        while (end >= start && buffer[end] <= SPACE) {
            end--;
        }

        if (end == initialEnd) {
            return this;
        }

        return subSequence(start - initialStart, end - start + 1);
    }

    @Override
    public Buffer trim() {
        final int initialStart = offset;
        final int initialEnd = offset + length - 1;

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

        return subSequence(start - initialStart, end - start + 1);
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

        int start = offset;
        final int end = offset + length - 1;

        while (start <= end && buffer[start] <= SPACE) {
            start++;
        }

        return start > end;
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
    public Buffer subSequence(final int off, final int len) {
        return new CharBuffer(buffer, offset + off, len);
    }

    @Override
    public Buffer reverse() {
        return new ReverseBuffer(buffer, offset + length - 1, length);
    }

    @Override
    public Buffer unsafeCopy() {
        return new CharBuffer(buffer, offset, length);
    }

    @Override
    public Buffer copy() {
        final char[] chars = toCharArray();
        return new CharBuffer(chars, 0, chars.length);
    }

    @Override
    public char[] toCharArray() {
        final char[] chars = new char[length];
        System.arraycopy(buffer, offset, chars, 0, length);
        return chars;
    }

    @Override
    public void move(final int increment) {
        offset += increment;
        length -= increment;
    }

    @Override
    public void remove(final int start, final int end) {
        final int removeLength = end - start;
        assert removeLength > 0;
        final char[] chars = new char[length - removeLength];
        System.arraycopy(buffer, offset, chars, 0, start);
        System.arraycopy(buffer, offset + end, chars, start, length - end);
        buffer = chars;
        length = chars.length;
        offset = 0;
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
