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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// Replaces a range of whole lines in a text with zero or more new lines.
/// Line indexes are zero based; `fromLine` is inclusive and `toLine` exclusive, so
/// `fromLine == toLine` inserts the new lines before line `fromLine`, and no new lines deletes the range.
public final class LineReplacement {

    private final int fromLine;
    private final int toLine;
    private final List<String> newLines;

    /// @param fromLine The zero based index of the first line to replace (inclusive).
    /// @param toLine   The zero based index of the line after the last one to replace (exclusive).
    /// @param newLines The lines to put in place of the range, without line terminators.
    public LineReplacement(final int fromLine, final int toLine, final List<String> newLines) {
        if (fromLine < 0 || toLine < fromLine) {
            throw new IllegalArgumentException("Invalid line range " + fromLine + " to " + toLine);
        }
        this.fromLine = fromLine;
        this.toLine = toLine;
        this.newLines = newLines == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(newLines));
    }

    /// @return The zero based index of the first line to replace (inclusive).
    public int getFromLine() {
        return fromLine;
    }

    /// @return The zero based index of the line after the last one to replace (exclusive).
    public int getToLine() {
        return toLine;
    }

    /// @return The lines to put in place of the range, without line terminators.
    public List<String> getNewLines() {
        return newLines;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final LineReplacement that = (LineReplacement) o;
        return fromLine == that.fromLine
               && toLine == that.toLine
               && newLines.equals(that.newLines);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromLine, toLine, newLines);
    }

    @Override
    public String toString() {
        return "[" + fromLine + "," + toLine + ") -> " + newLines;
    }
}
