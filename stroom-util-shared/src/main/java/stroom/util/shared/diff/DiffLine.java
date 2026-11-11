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
import java.util.Objects;

/// One row of a side by side diff: an old line, a new line, or both.
///
/// - [DiffType#EQUAL]: the same line in both texts.
/// - [DiffType#DELETE]: an old line with no new counterpart.
/// - [DiffType#INSERT]: a new line with no old counterpart.
/// - [DiffType#CHANGE]: an old line paired with the new line that replaced it, with the ranges of
///   characters that differ on each side.
public final class DiffLine {

    private final DiffType type;
    private final int oldIndex;
    private final int newIndex;
    private final String oldText;
    private final String newText;
    private final List<CharRange> oldChanges;
    private final List<CharRange> newChanges;

    /// @param type       The kind of row.
    /// @param oldIndex   The zero based index of the old line, or -1 if there isn't one.
    /// @param newIndex   The zero based index of the new line, or -1 if there isn't one.
    /// @param oldText    The old line's text, or null if there isn't one.
    /// @param newText    The new line's text, or null if there isn't one.
    /// @param oldChanges The changed characters in the old line (only for [DiffType#CHANGE]).
    /// @param newChanges The changed characters in the new line (only for [DiffType#CHANGE]).
    public DiffLine(final DiffType type,
                    final int oldIndex,
                    final int newIndex,
                    final String oldText,
                    final String newText,
                    final List<CharRange> oldChanges,
                    final List<CharRange> newChanges) {
        this.type = Objects.requireNonNull(type);
        this.oldIndex = oldIndex;
        this.newIndex = newIndex;
        this.oldText = oldText;
        this.newText = newText;
        this.oldChanges = oldChanges == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(oldChanges);
        this.newChanges = newChanges == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(newChanges);
    }

    /// @return The kind of row.
    public DiffType getType() {
        return type;
    }

    /// @return The zero based index of the old line, or -1 if this row has no old line.
    public int getOldIndex() {
        return oldIndex;
    }

    /// @return The zero based index of the new line, or -1 if this row has no new line.
    public int getNewIndex() {
        return newIndex;
    }

    /// @return The old line's text, or null if this row has no old line.
    public String getOldText() {
        return oldText;
    }

    /// @return The new line's text, or null if this row has no new line.
    public String getNewText() {
        return newText;
    }

    /// @return True if this row has an old line.
    public boolean hasOld() {
        return oldIndex >= 0;
    }

    /// @return True if this row has a new line.
    public boolean hasNew() {
        return newIndex >= 0;
    }

    /// @return The ranges of changed characters in the old line, empty unless [DiffType#CHANGE].
    public List<CharRange> getOldChanges() {
        return oldChanges;
    }

    /// @return The ranges of changed characters in the new line, empty unless [DiffType#CHANGE].
    public List<CharRange> getNewChanges() {
        return newChanges;
    }

    @Override
    public String toString() {
        return type + " " + oldIndex + ":" + oldText + " -> " + newIndex + ":" + newText;
    }
}
