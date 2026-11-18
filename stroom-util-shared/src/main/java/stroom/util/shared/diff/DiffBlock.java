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

/// A run of lines that are either all equal, or all differ, between the old and new text.
/// Line indexes are zero based; `from` is inclusive and `to` exclusive.
///
/// An [DiffType#INSERT] block has no old lines, a [DiffType#DELETE] block no new lines and a
/// [DiffType#CHANGE] block both. Blocks that differ are always separated by an [DiffType#EQUAL] block.
public final class DiffBlock {

    private final DiffType type;
    private final int oldFrom;
    private final int oldTo;
    private final int newFrom;
    private final int newTo;

    /// @param type    The kind of block.
    /// @param oldFrom The zero based index of the first old line in the block (inclusive).
    /// @param oldTo   The zero based index of the old line after the block (exclusive).
    /// @param newFrom The zero based index of the first new line in the block (inclusive).
    /// @param newTo   The zero based index of the new line after the block (exclusive).
    public DiffBlock(final DiffType type,
                     final int oldFrom,
                     final int oldTo,
                     final int newFrom,
                     final int newTo) {
        this.type = Objects.requireNonNull(type);
        this.oldFrom = oldFrom;
        this.oldTo = oldTo;
        this.newFrom = newFrom;
        this.newTo = newTo;
    }

    /// @return The kind of block.
    public DiffType getType() {
        return type;
    }

    /// @return The zero based index of the first old line in the block (inclusive).
    public int getOldFrom() {
        return oldFrom;
    }

    /// @return The zero based index of the old line after the block (exclusive).
    public int getOldTo() {
        return oldTo;
    }

    /// @return The zero based index of the first new line in the block (inclusive).
    public int getNewFrom() {
        return newFrom;
    }

    /// @return The zero based index of the new line after the block (exclusive).
    public int getNewTo() {
        return newTo;
    }

    /// @return The number of old lines in the block.
    public int getOldCount() {
        return oldTo - oldFrom;
    }

    /// @return The number of new lines in the block.
    public int getNewCount() {
        return newTo - newFrom;
    }

    /// @return True if this block is a difference rather than equal lines.
    public boolean isDifference() {
        return type != DiffType.EQUAL;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DiffBlock that = (DiffBlock) o;
        return oldFrom == that.oldFrom
               && oldTo == that.oldTo
               && newFrom == that.newFrom
               && newTo == that.newTo
               && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, oldFrom, oldTo, newFrom, newTo);
    }

    @Override
    public String toString() {
        return type + " old[" + oldFrom + "," + oldTo + ") new[" + newFrom + "," + newTo + ")";
    }
}
