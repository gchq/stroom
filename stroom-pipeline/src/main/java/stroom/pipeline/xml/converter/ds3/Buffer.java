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

public interface Buffer extends CharSequence {
    /**
     * Clear the buffer.
     */
    void clear();

    /**
     * Trims off whitespace from the start of the buffer.
     */
    Buffer trimStart();

    /**
     * Trims off whitespace from the end of the buffer.
     */
    Buffer trimEnd();

    /**
     * Trims off whitespace from the start and end of the buffer.
     */
    Buffer trim();

    /**
     * True if the length of the buffer is 0.
     */
    boolean isEmpty();

    /**
     * True if the length of the buffer is 0 or that all content is whitespace.
     */
    boolean isBlank();

    /**
     * Get a buffer that is a subsequence of this buffer. This method does not
     * copy the underlying character array but just records new offsets and
     * lengths.
     */
    @Override
    Buffer subSequence(int off, int len);

    /**
     * This method returns a reverse buffer that will return chars backwards for
     * the purpose of expression matching.
     */
    Buffer reverse();

    /**
     * Doesn't actually copy the buffer but instead returns a new CharBuffer
     * that uses the existing buffer with the same start and end positions.
     */
    Buffer unsafeCopy();

    /**
     * Actually copy the buffer with the current start and end positions. The
     * new copy will not be affected by any changes made to the original.
     */
    Buffer copy();

    /**
     * Copies the buffer into a new char array and returns it.
     */
    char[] toCharArray();

    /**
     * Moves the offset forward by the specified amount and reduces the
     * remaining length by the same amount.
     */
    void move(int increment);

    /**
     * Removes a portion of the buffer.
     */
    void remove(int start, int end);
}
