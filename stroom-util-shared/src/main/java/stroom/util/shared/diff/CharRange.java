/*
 * Copyright 2026 Crown Copyright
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

package stroom.util.shared.diff;

import java.util.Objects;

/// A range of characters within a single line, zero based, with an inclusive start and exclusive end.
public final class CharRange {

    private final int start;
    private final int end;

    /// @param start The zero based index of the first character in the range.
    /// @param end   The zero based index of the character after the last one in the range.
    public CharRange(final int start, final int end) {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid range " + start + " to " + end);
        }
        this.start = start;
        this.end = end;
    }

    /// @return The zero based index of the first character in the range.
    public int getStart() {
        return start;
    }

    /// @return The zero based index of the character after the last one in the range.
    public int getEnd() {
        return end;
    }

    /// @return The number of characters in the range.
    public int getLength() {
        return end - start;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CharRange that = (CharRange) o;
        return start == that.start && end == that.end;
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public String toString() {
        return "[" + start + "," + end + ")";
    }
}
