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

package stroom.util;

import java.io.Serial;
import java.io.Serializable;

public class CharBuffer implements CharSequence, Serializable {

    private static final char SPACE = ' ';

    @Serial
    private static final long serialVersionUID = -3021087453916074085L;

    private static final int DEFAULT_SIZE = 16;
    protected int start;
    protected int end;
    protected char[] buffer;

    /**
     * Create a buffer with an initial size of 16.
     */
    public CharBuffer() {
        buffer = new char[DEFAULT_SIZE];
    }

    /**
     * Create a buffer with a specified initial size.
     *
     * @param initialSize The initial size of the buffer.
     */
    public CharBuffer(final int initialSize) {
        buffer = new char[initialSize];
    }

    public CharBuffer append(final char c) {
        // Double the buffer size if needed.
        if (end >= buffer.length) {
            final char[] tmp = new char[buffer.length * 2];
            System.arraycopy(buffer, 0, tmp, 0, buffer.length);
            buffer = tmp;
        }

        buffer[end++] = c;

        return this;
    }

    public CharBuffer append(final char[] ch, final int start, final int len) {
        // Grow the buffer if we need to.
        int required = end + len;
        if (required > buffer.length) {
            final int multiple = (required / buffer.length) + 1;
            required = buffer.length * multiple;
            final char[] tmp = new char[required];
            System.arraycopy(buffer, 0, tmp, 0, buffer.length);
            buffer = tmp;
        }

        System.arraycopy(ch, start, buffer, end, len);
        end += len;

        return this;
    }

    public CharBuffer append(final String string) {
        final char[] chars = string.toCharArray();
        append(chars, 0, chars.length);

        return this;
    }

    public CharBuffer append(final Object obj) {
        return append(String.valueOf(obj));
    }

    /**
     * Clear the buffer.
     */
    public void clear() {
        start = 0;
        end = 0;
    }

    /**
     * Trims off the specified char from the start of the buffer.
     */
    public CharBuffer trimCharStart(final char c) {
        while ((start < end) && (buffer[start] == c)) {
            start++;
        }
        return this;
    }

    /**
     * Trims off the specified char from the end of the buffer.
     */
    public CharBuffer trimCharEnd(final char c) {
        while ((start < end) && (buffer[end - 1] == c)) {
            end--;
        }
        return this;
    }

    /**
     * Trims off the specified char from the start end end of the buffer.
     */
    public CharBuffer trimChar(final char c) {
        return trimCharStart(c).trimCharEnd(c);
    }

    /**
     * Trims off whitespace from the start of the buffer.
     */
    public CharBuffer trimWhitespaceStart() {
        while ((start < end) && (buffer[start] <= SPACE)) {
            start++;
        }
        return this;
    }

    /**
     * Trims off whitespace from the end of the buffer.
     */
    public CharBuffer trimWhitespaceEnd() {
        while ((start < end) && (buffer[end - 1] <= SPACE)) {
            end--;
        }
        return this;
    }

    /**
     * Trims off whitespace from the start end end of the buffer.
     */
    public CharBuffer trimWhitespace() {
        return trimWhitespaceStart().trimWhitespaceEnd();
    }

    /**
     * Take the specified number of characters off the end of the buffer.
     */
    public void trimEnd(final int len) {
        end -= len;
    }

    /**
     * Take the specified number of characters off the start of the buffer.
     */
    public void trimStart(final int len) {
        start += len;
    }

    @Override
    public String toString() {
        return new String(buffer, start, end - start);
    }

    public char[] toCharArray() {
        final int len = end - start;
        final char[] tmp = new char[len];
        System.arraycopy(buffer, start, tmp, 0, len);
        return tmp;
    }

    /**
     * True if the length of the buffer is 0.
     */
    public boolean isEmpty() {
        return end == start;
    }

    /**
     * True if the length of the buffer is 0 or that all content is whitespace.
     */
    public boolean isBlank() {
        int pos = start;
        while ((pos < end) && (buffer[pos] <= SPACE)) {
            pos++;
        }
        return pos == end;
    }

    /**
     * Gets the character at the specified index.
     */
    @Override
    public char charAt(final int index) {
        return buffer[start + index];
    }

    @Override
    public int length() {
        return end - start;
    }

    /**
     * Sets the length of the string to a certain size. This will effectively
     * trim the string if the length specified is less than the current length.
     * If the length is greater than the current length then more characters
     * will be included from the underlying buffer.
     */
    public void setLength(final int length) {
        end = start + length;

        if (end > buffer.length) {
            throw new ArrayIndexOutOfBoundsException();
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else if (o == null) {
            return false;
        }

        if (o instanceof final CharBuffer cb) {
            final int len1 = end - start;
            final int len2 = cb.end - cb.start;

            if (len1 != len2) {
                return false;
            }
            for (int i = start, j = cb.start; i < len1; ) {
                if (buffer[i++] != cb.buffer[j++]) {
                    return false;
                }
            }
            return true;
        } else if (o instanceof final CharSequence charSequence) {
            return (end - start) == charSequence.length() && toString().equals(charSequence.toString());
        }

        return false;
    }

    @Override
    public int hashCode() {
        // Same algorithm as String#hashCode(), but not cached as this class is
        // mutable.
        int h = 0;
        for (int i = start; i < end; i++) {
            h = 31 * h + buffer[i];
        }
        return h;
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        final CharBuffer sub = new CharBuffer(end - start);
        sub.append(buffer, start, end - start);
        return sub;
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
}
