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

package stroom.pipeline.stepping.client.presenter;

import stroom.util.shared.diff.CharRange;
import stroom.util.shared.diff.DiffLine;
import stroom.util.shared.diff.DiffType;
import stroom.util.shared.diff.LineDiffResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// The two sides of a side by side diff, lined up row for row for display in two read only editors.
///
/// Where one side has lines the other doesn't (an insertion, a deletion, or a change to a different
/// number of lines), the other side gets blank filler rows so each change lines up and the two editors
/// can simply scroll together. As the filler rows would throw out the editor's own line numbers, each
/// side also has a gutter label per row: the real line number, or blank for a filler row.
public final class SideBySideDiff {

    private final List<Side> left;
    private final List<Side> right;
    private final List<String> leftLabels;
    private final List<String> rightLabels;
    private final List<List<CharRange>> leftCharChanges;
    private final List<List<CharRange>> rightCharChanges;
    private final List<Integer> changeStartRows;
    private final List<String> leftLines;
    private final List<String> rightLines;

    private SideBySideDiff(final List<Side> left,
                           final List<Side> right,
                           final List<String> leftLines,
                           final List<String> rightLines,
                           final List<String> leftLabels,
                           final List<String> rightLabels,
                           final List<List<CharRange>> leftCharChanges,
                           final List<List<CharRange>> rightCharChanges,
                           final List<Integer> changeStartRows) {
        this.left = Collections.unmodifiableList(left);
        this.right = Collections.unmodifiableList(right);
        this.leftLines = Collections.unmodifiableList(leftLines);
        this.rightLines = Collections.unmodifiableList(rightLines);
        this.leftLabels = Collections.unmodifiableList(leftLabels);
        this.rightLabels = Collections.unmodifiableList(rightLabels);
        this.leftCharChanges = Collections.unmodifiableList(leftCharChanges);
        this.rightCharChanges = Collections.unmodifiableList(rightCharChanges);
        this.changeStartRows = Collections.unmodifiableList(changeStartRows);
    }

    /// Lines up a diff for side by side display, the old text on the left and the new on the right.
    ///
    /// @param diff The diff.
    /// @return The two sides, with the same number of rows.
    public static SideBySideDiff of(final LineDiffResult diff) {
        final List<Side> left = new ArrayList<>();
        final List<Side> right = new ArrayList<>();
        final List<String> leftLines = new ArrayList<>();
        final List<String> rightLines = new ArrayList<>();
        final List<String> leftLabels = new ArrayList<>();
        final List<String> rightLabels = new ArrayList<>();
        final List<List<CharRange>> leftCharChanges = new ArrayList<>();
        final List<List<CharRange>> rightCharChanges = new ArrayList<>();
        final List<Integer> changeStartRows = new ArrayList<>();

        boolean inChange = false;
        for (final DiffLine line : diff.getLines()) {
            final DiffType type = line.getType();
            if (type == DiffType.EQUAL) {
                inChange = false;
            } else if (!inChange) {
                inChange = true;
                changeStartRows.add(left.size());
            }

            if (line.hasOld()) {
                left.add(sideFor(type));
                leftLines.add(line.getOldText());
                leftLabels.add(String.valueOf(line.getOldIndex() + 1));
                leftCharChanges.add(line.getOldChanges());
            } else {
                left.add(Side.FILLER);
                leftLines.add("");
                leftLabels.add("");
                leftCharChanges.add(Collections.emptyList());
            }

            if (line.hasNew()) {
                right.add(sideFor(type));
                rightLines.add(line.getNewText());
                rightLabels.add(String.valueOf(line.getNewIndex() + 1));
                rightCharChanges.add(line.getNewChanges());
            } else {
                right.add(Side.FILLER);
                rightLines.add("");
                rightLabels.add("");
                rightCharChanges.add(Collections.emptyList());
            }
        }

        return new SideBySideDiff(left, right, leftLines, rightLines, leftLabels, rightLabels,
                leftCharChanges, rightCharChanges, changeStartRows);
    }

    private static Side sideFor(final DiffType type) {
        switch (type) {
            case EQUAL:
                return Side.EQUAL;
            case DELETE:
                return Side.REMOVED;
            case INSERT:
                return Side.ADDED;
            case CHANGE:
                return Side.CHANGED;
            default:
                throw new IllegalArgumentException("Unexpected type " + type);
        }
    }

    /// @return The number of rows, the same on both sides.
    public int getRowCount() {
        return left.size();
    }

    /// @return What each row on the left (old) side shows.
    public List<Side> getLeft() {
        return left;
    }

    /// @return What each row on the right (new) side shows.
    public List<Side> getRight() {
        return right;
    }

    /// @return The text to show on the left, with filler rows, lines separated by `\n`.
    public String getLeftText() {
        return String.join("\n", leftLines);
    }

    /// @return The text to show on the right, with filler rows, lines separated by `\n`.
    public String getRightText() {
        return String.join("\n", rightLines);
    }

    /// @return The gutter label for each row on the left: its old line number, or blank for a filler.
    public List<String> getLeftLabels() {
        return leftLabels;
    }

    /// @return The gutter label for each row on the right: its new line number, or blank for a filler.
    public List<String> getRightLabels() {
        return rightLabels;
    }

    /// @return The changed characters in each row on the left (only changed rows have any).
    public List<List<CharRange>> getLeftCharChanges() {
        return leftCharChanges;
    }

    /// @return The changed characters in each row on the right (only changed rows have any).
    public List<List<CharRange>> getRightCharChanges() {
        return rightCharChanges;
    }

    /// @return The zero based row at which each change starts, in order, for moving between changes.
    public List<Integer> getChangeStartRows() {
        return changeStartRows;
    }

    /// @param change A change, by its index in [#getChangeStartRows()]; its index among the diff's changed blocks.
    /// @return The zero based row after the change's last row.
    public int getChangeEndRow(final int change) {
        int row = changeStartRows.get(change);
        while (row < left.size() && left.get(row) != Side.EQUAL) {
            row++;
        }
        return row;
    }

    /// @param currentRow The zero based row the user is at, or -1 for before the first.
    /// @return The index (into [#getChangeStartRows()]) of the first change after the row, or -1 if
    /// there isn't one.
    public int getNextChange(final int currentRow) {
        for (int i = 0; i < changeStartRows.size(); i++) {
            if (changeStartRows.get(i) > currentRow) {
                return i;
            }
        }
        return -1;
    }

    /// @param currentRow The zero based row the user is at.
    /// @return The index (into [#getChangeStartRows()]) of the last change before the row, or -1 if
    /// there isn't one.
    public int getPreviousChange(final int currentRow) {
        for (int i = changeStartRows.size() - 1; i >= 0; i--) {
            if (changeStartRows.get(i) < currentRow) {
                return i;
            }
        }
        return -1;
    }


    // --------------------------------------------------------------------------------


    /// What one side of a row shows.
    public enum Side {
        /// A line that is the same on both sides.
        EQUAL,
        /// An old line that was removed (left only).
        REMOVED,
        /// A new line that was added (right only).
        ADDED,
        /// A line that was changed (on both sides).
        CHANGED,
        /// A blank row, there to line up the other side's extra lines.
        FILLER
    }
}
