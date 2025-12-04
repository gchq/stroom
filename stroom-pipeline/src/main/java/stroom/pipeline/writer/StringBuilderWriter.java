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

package stroom.pipeline.writer;

import java.io.IOException;
import java.io.Writer;

public class StringBuilderWriter extends Writer {
    private final StringBuilder sb;

    /**
     * Create a new string writer using the default initial string-buffer size.
     */
    public StringBuilderWriter() {
        sb = new StringBuilder();
        lock = sb;
    }

    /**
     * Create a new string writer using the specified initial string-buffer
     * size.
     *
     * @param initialSize The number of <tt>char</tt> values that will fit into this
     *                    buffer before it is automatically expanded
     * @throws IllegalArgumentException If <tt>initialSize</tt> is negative
     */
    public StringBuilderWriter(final int initialSize) {
        if (initialSize < 0) {
            throw new IllegalArgumentException("Negative buffer size");
        }
        sb = new StringBuilder(initialSize);
        lock = sb;
    }

    /**
     * Write a single character.
     */
    @Override
    public void write(final int c) {
        sb.append((char) c);
    }

    /**
     * Write a portion of an array of characters.
     *
     * @param cbuf Array of characters
     * @param off  Offset from which to start writing characters
     * @param len  Number of characters to write
     */
    @Override
    public void write(final char[] cbuf, final int off, final int len) {
        if ((off < 0) || (off > cbuf.length) || (len < 0) || ((off + len) > cbuf.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }
        sb.append(cbuf, off, len);
    }

    /**
     * Write a string.
     */
    @Override
    public void write(final String str) {
        sb.append(str);
    }

    /**
     * Write a portion of a string.
     *
     * @param str String to be written
     * @param off Offset from which to start writing characters
     * @param len Number of characters to write
     */
    @Override
    public void write(final String str, final int off, final int len) {
        sb.append(str.substring(off, off + len));
    }

    /**
     * Appends the specified character sequence to this writer.
     * <p>
     * <p>
     * An invocation of this method of the form <tt>out.append(csq)</tt> behaves
     * in exactly the same way as the invocation
     * <p>
     * <pre>
     * out.write(csq.toString())
     * </pre>
     * <p>
     * <p>
     * Depending on the specification of <tt>toString</tt> for the character
     * sequence <tt>csq</tt>, the entire sequence may not be appended. For
     * instance, invoking the <tt>toString</tt> method of a character buffer
     * will return a subsequence whose content depends upon the buffer's
     * position and limit.
     *
     * @param csq The character sequence to append. If <tt>csq</tt> is
     *            <tt>null</tt>, then the four characters <tt>"null"</tt> are
     *            appended to this writer.
     * @return This writer
     * @since 1.5
     */
    @Override
    public StringBuilderWriter append(final CharSequence csq) {
        if (csq == null) {
            write("null");
        } else {
            write(csq.toString());
        }
        return this;
    }

    /**
     * Appends a subsequence of the specified character sequence to this writer.
     * <p>
     * <p>
     * An invocation of this method of the form <tt>out.append(csq, start,
     * end)</tt> when <tt>csq</tt> is not <tt>null</tt>, behaves in exactly the
     * same way as the invocation
     * <p>
     * <pre>
     * out.write(csq.subSequence(start, end).toString())
     * </pre>
     *
     * @param csq   The character sequence from which a subsequence will be
     *              appended. If <tt>csq</tt> is <tt>null</tt>, then characters
     *              will be appended as if <tt>csq</tt> contained the four
     *              characters <tt>"null"</tt>.
     * @param start The index of the first character in the subsequence
     * @param end   The index of the character following the last character in the
     *              subsequence
     * @return This writer
     * @throws IndexOutOfBoundsException If <tt>start</tt> or <tt>end</tt> are negative,
     *                                   <tt>start</tt> is greater than <tt>end</tt>, or <tt>end</tt>
     *                                   is greater than <tt>csq.length()</tt>
     * @since 1.5
     */
    @Override
    public StringBuilderWriter append(final CharSequence csq, final int start, final int end) {
        final CharSequence cs = (csq == null ? "null" : csq);
        write(cs.subSequence(start, end).toString());
        return this;
    }

    /**
     * Appends the specified character to this writer.
     * <p>
     * <p>
     * An invocation of this method of the form <tt>out.append(c)</tt> behaves
     * in exactly the same way as the invocation
     * <p>
     * <pre>
     * out.write(c)
     * </pre>
     *
     * @param c The 16-bit character to append
     * @return This writer
     * @since 1.5
     */
    @Override
    public StringBuilderWriter append(final char c) {
        write(c);
        return this;
    }

    /**
     * Return the buffer's current value as a string.
     */
    @Override
    public String toString() {
        return sb.toString();
    }

    /**
     * Return the string buffer itself.
     *
     * @return StringBuffer holding the current buffer value.
     */
    public StringBuilder getBuilder() {
        return sb;
    }

    /**
     * Flush the stream.
     */
    @Override
    public void flush() {
    }

    /**
     * Closing a <tt>StringWriter</tt> has no effect. The methods in this class
     * can be called after the stream has been closed without generating an
     * <tt>IOException</tt>.
     */
    @Override
    public void close() throws IOException {
    }
}
