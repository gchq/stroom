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

package stroom.util.xml;

import java.io.Serializable;

public class RingBuffer implements CharSequence, Serializable {

    private static final long serialVersionUID = -3021087453916074085L;

    private static final int DEFAULT_SIZE = 16;
    private static final char SPACE = ' ';

    private int offset;
    private int length;
    private final char[] buffer;

    /**
     * Create a buffer with an initial size of 16.
     */
    public RingBuffer() {
        buffer = new char[DEFAULT_SIZE];
    }

    /**
     * Create a buffer with a specified initial size.
     *
     * @param initialSize The initial size of the buffer.
     */
    public RingBuffer(final int initialSize) {
        buffer = new char[initialSize];
    }

    public static RingBuffer fromString(final String string) {
        final char[] chars = string.toCharArray();
        return new RingBuffer(chars, 0, chars.length);
    }

    private RingBuffer(final char[] buffer, final int off, final int len) {
        this.buffer = buffer;
        this.offset = off;
        this.length = len;
    }

    public RingBuffer append(final char c) {
        int off = offset + length;
        off = off % buffer.length;
        buffer[off] = c;

        if (length == buffer.length) {
            offset++;
            offset = offset % buffer.length;
        } else {
            length++;
        }

        return this;
    }

    public RingBuffer append(final char[] ch, int start, int len) {
        // Calculate the current offset for adding data.
        int off = offset + length;
        off = off % buffer.length;

        // len cannot be bigger than the buffer. If it is then limit the array
        // we are pending to only add the last set of characters that will fit
        // into the buffer.
        if (len > buffer.length) {
            start += len - buffer.length;
            len = buffer.length;
        }

        // Now we know how many characters we are adding check to see if the
        // offset needs to move. This is only required if the buffer will
        // overflow when adding the array.
        if (length + len > buffer.length) {
            offset += length + len - buffer.length;
            offset = offset % buffer.length;
        }

        // Now write to the end of the buffer separately if we need to.
        if (off + len > buffer.length) {
            final int rem = buffer.length - off;
            System.arraycopy(ch, start, buffer, off, rem);
            length += rem;
            start += rem;
            len -= rem;
            off = 0;
        }

        // Now write anything else to the start.
        System.arraycopy(ch, start, buffer, off, len);
        length += len;

        // Limit the length.
        if (length > buffer.length) {
            length = buffer.length;
        }

        return this;
    }

    public RingBuffer append(final String string) {
        final char[] chars = string.toCharArray();
        append(chars, 0, chars.length);

        return this;
    }

    public RingBuffer append(final Object obj) {
        return append(String.valueOf(obj));
    }

    public void clear() {
        length = 0;
    }

    public RingBuffer trimStart() {
        // Move offset forward.
        while (buffer[offset] <= SPACE && length > 0) {
            offset++;
            length--;

            if (offset >= buffer.length) {
                offset = 0;
            }
        }

        return this;
    }

    public RingBuffer trimEnd() {
        // Move length backward.
        if (length > 0) {
            // Find the end offset.
            int off = offset + length - 1;
            off = off % buffer.length;

            while (buffer[off] <= SPACE && length > 0) {
                if (off == 0) {
                    off = buffer.length;
                }

                off--;
                length--;
            }
        }

        return this;
    }

    public RingBuffer trim() {
        trimStart();
        trimEnd();
        return this;
    }

    public void removeEnd(final int len) {
        if (len < 0) {
            throw new IllegalArgumentException("You cannot remove a negative number of characters");
        }
        setLength(length - len);
    }

    public void removeStart(final int len) {
        if (len < 0) {
            throw new IllegalArgumentException("You cannot remove a negative number of characters");
        }
        setLength(length - len);
        offset += len;
        offset = offset % buffer.length;
    }

    /**
     * Sets the length of the string to a certain size. This will effectively
     * trim the string if the length specified is less than the current length.
     * If the length is greater than the current length then more characters
     * will be included from the underlying buffer.
     */
    public void setLength(final int len) {
        if (len < 0 || len > buffer.length) {
            throw new IndexOutOfBoundsException();
        }

        length = len;
    }

    /**
     * True if the length of the buffer is 0.
     */
    public boolean isEmpty() {
        return length == 0;
    }

    /**
     * True if the length of the buffer is 0 or that all content is whitespace.
     */
    public boolean isBlank() {
        if (isEmpty()) {
            return true;
        }

        int off = offset;
        int len = length;
        while (len > 0 && buffer[off] <= SPACE) {
            off++;
            len--;

            if (off >= buffer.length) {
                off = 0;
            }
        }

        return len == 0;
    }

    @Override
    public String toString() {
        if (offset + length <= buffer.length) {
            return new String(buffer, offset, length);
        } else {
            final char[] chars = toCharArray();
            return new String(chars, 0, chars.length);
        }
    }

    /**
     * Gets the character at the specified index.
     */
    @Override
    public char charAt(final int index) {
        if (index < 0 || index >= buffer.length) {
            throw new IndexOutOfBoundsException();
        }

        int off = offset + index;
        off = off % buffer.length;

        return buffer[off];
    }

    @Override
    public int length() {
        return length;
    }

    public int capacity() {
        return buffer.length;
    }

    @Override
    public CharSequence subSequence(final int off, final int len) {
        if (len > buffer.length || off < 0 || off >= buffer.length) {
            throw new IndexOutOfBoundsException();
        }

        int o = offset + off;
        o = o % buffer.length;
        return new RingBuffer(buffer, o, len);
    }

    /**
     * Doesn't actually copy the buffer but instead returns a new CharBuffer
     * that uses the existing buffer with the same start and end positions.
     */
    public RingBuffer unsafeCopy() {
        return new RingBuffer(buffer, offset, length);
    }

    /**
     * Actually copy the buffer with the current start and end positions. The
     * new copy will not be affected by any changes made to the original.
     */
    public RingBuffer copy() {
        final char[] chars = toCharArray();
        return new RingBuffer(chars, 0, chars.length);
    }

    public char[] toCharArray() {
        final char[] chars = new char[length];
        int off = offset;
        int len = length;
        int pos = 0;
        while (len > 0) {
            if (off + len > buffer.length) {
                final int copied = buffer.length - off;
                System.arraycopy(buffer, off, chars, pos, copied);
                len -= copied;
                pos += copied;
                off = 0;
            } else {
                System.arraycopy(buffer, off, chars, pos, len);
                len = 0;
            }
        }

        return chars;
    }

    /**
     * Adds the specified number of characters to the end of the buffer.
     */
    public void pad(final int num, final char c) {
        if (num > 0) {
            for (int i = 0; i < num; i++) {
                append(c);
            }
        }
    }

    /**
     * Moves the offset forward by the specified amount and reduces the
     * remaining length by the same amount.
     */
    public void move(final int increment) {
        offset += increment;
        length -= increment;

        if (length < 0) {
            // Make sure the length can't ever be less than 0.
            offset += length;
            length = 0;
        } else if (length > buffer.length) {
            // Make sure the length can't ever be greater than the buffer
            // length.
            offset -= buffer.length - length;
            length = buffer.length;
        }

        offset = offset % buffer.length;
        if (offset < 0) {
            offset = buffer.length + offset;
        }
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof RingBuffer) {
            final RingBuffer buffer = (RingBuffer) other;
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
        for (int i = 0; i < length; i++) {
            h = 31 * h + charAt(i);
        }
        return h;
    }
}
