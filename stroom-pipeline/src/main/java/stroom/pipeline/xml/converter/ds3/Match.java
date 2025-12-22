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

public interface Match {
    /**
     * Returns the start index of the match.
     *
     * @return The index of the first character matched
     * @throws IllegalStateException If no match has yet been attempted, or if the previous match
     *                               operation failed
     */
    int start();

    /**
     * Returns the start index of the subsequence captured by the given group
     * during this match.
     * <p>
     * <p>
     * <a href="Pattern.html#cg">Capturing groups</a> are indexed from left to
     * right, starting at one. Group zero denotes the entire pattern, so the
     * expression <i>m.</i><tt>start(0)</tt> is equivalent to <i>m.</i>
     * <tt>start()</tt>.
     * </p>
     *
     * @param group The index of a capturing group in this matcher's pattern
     * @return The index of the first character captured by the group, or
     * <tt>-1</tt> if the match was successful but the group itself did
     * not match anything
     * @throws IllegalStateException     If no match has yet been attempted, or if the previous match
     *                                   operation failed
     * @throws IndexOutOfBoundsException If there is no capturing group in the pattern with the given
     *                                   index
     */
    int start(int group);

    /**
     * Returns the offset after the last character matched.
     * </p>
     *
     * @return @return The offset after the last character matched
     * @throws IllegalStateException If no match has yet been attempted, or if the previous match
     *                               operation failed
     */
    int end();

    /**
     * Returns the offset after the last character of the subsequence captured
     * by the given group during this match.
     * <p>
     * <p>
     * <a href="Pattern.html#cg">Capturing groups</a> are indexed from left to
     * right, starting at one. Group zero denotes the entire pattern, so the
     * expression <i>m.</i><tt>end(0)</tt> is equivalent to <i>m.</i>
     * <tt>end()</tt>.
     * </p>
     *
     * @param group The index of a capturing group in this matcher's pattern
     * @return The offset after the last character captured by the group, or
     * <tt>-1</tt> if the match was successful but the group itself did
     * not match anything
     * @throws IllegalStateException     If no match has yet been attempted, or if the previous match
     *                                   operation failed
     * @throws IndexOutOfBoundsException If there is no capturing group in the pattern with the given
     *                                   index
     */
    int end(int group);

    Buffer filter(Buffer buffer, int group);
}
