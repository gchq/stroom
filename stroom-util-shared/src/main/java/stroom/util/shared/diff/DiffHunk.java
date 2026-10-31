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

import java.util.Collections;
import java.util.List;

/// A group of nearby differences with some lines of unchanged context around them, as in a unified
/// diff. Line indexes are zero based.
public final class DiffHunk {

    private final int oldStart;
    private final int oldCount;
    private final int newStart;
    private final int newCount;
    private final List<DiffLine> lines;

    /// @param oldStart The zero based index of the first old line covered by the hunk.
    /// @param oldCount The number of old lines covered by the hunk.
    /// @param newStart The zero based index of the first new line covered by the hunk.
    /// @param newCount The number of new lines covered by the hunk.
    /// @param lines    The rows of the hunk, context included, in order.
    public DiffHunk(final int oldStart,
                    final int oldCount,
                    final int newStart,
                    final int newCount,
                    final List<DiffLine> lines) {
        this.oldStart = oldStart;
        this.oldCount = oldCount;
        this.newStart = newStart;
        this.newCount = newCount;
        this.lines = Collections.unmodifiableList(lines);
    }

    /// @return The zero based index of the first old line covered by the hunk.
    public int getOldStart() {
        return oldStart;
    }

    /// @return The number of old lines covered by the hunk.
    public int getOldCount() {
        return oldCount;
    }

    /// @return The zero based index of the first new line covered by the hunk.
    public int getNewStart() {
        return newStart;
    }

    /// @return The number of new lines covered by the hunk.
    public int getNewCount() {
        return newCount;
    }

    /// @return The rows of the hunk, context included, in order.
    public List<DiffLine> getLines() {
        return lines;
    }

    /// @return The number of lines added (inserted or changed to) in this hunk.
    public int getAddedLineCount() {
        int count = 0;
        for (final DiffLine line : lines) {
            if (line.getType() != DiffType.EQUAL && line.hasNew()) {
                count++;
            }
        }
        return count;
    }

    /// @return The number of lines removed (deleted or changed from) in this hunk.
    public int getRemovedLineCount() {
        int count = 0;
        for (final DiffLine line : lines) {
            if (line.getType() != DiffType.EQUAL && line.hasOld()) {
                count++;
            }
        }
        return count;
    }

    /// The unified diff header for this hunk, e.g. `@@ -12,7 +12,8 @@`, with one based line numbers.
    /// As in GNU diff, a count of one is left out, and an empty range gives the line before it.
    ///
    /// @return The header.
    public String getHeader() {
        return "@@ -" + range(oldStart, oldCount) + " +" + range(newStart, newCount) + " @@";
    }

    private static String range(final int start, final int count) {
        if (count == 1) {
            return String.valueOf(start + 1);
        } else if (count == 0) {
            return start + ",0";
        } else {
            return (start + 1) + "," + count;
        }
    }

    @Override
    public String toString() {
        return getHeader();
    }
}
