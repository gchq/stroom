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

/// The result of [LineDiff#diff(String, String)]: the blocks of equal and differing lines, and views of
/// them as side by side rows, unified diff hunks and line replacements.
public final class LineDiffResult {

    /// The number of lines of unchanged context usually shown around each change.
    public static final int DEFAULT_CONTEXT_LINES = 3;

    private final List<String> oldLines;
    private final List<String> newLines;
    private final List<DiffBlock> blocks;

    LineDiffResult(final List<String> oldLines,
                   final List<String> newLines,
                   final List<DiffBlock> blocks) {
        this.oldLines = Collections.unmodifiableList(new ArrayList<>(oldLines));
        this.newLines = Collections.unmodifiableList(new ArrayList<>(newLines));
        this.blocks = Collections.unmodifiableList(blocks);
    }

    /// @return The old lines, without line terminators.
    public List<String> getOldLines() {
        return oldLines;
    }

    /// @return The new lines, without line terminators.
    public List<String> getNewLines() {
        return newLines;
    }

    /// @return All the blocks, equal ones included, covering both texts in order.
    public List<DiffBlock> getBlocks() {
        return blocks;
    }

    /// @return Only the blocks that differ, in order.
    public List<DiffBlock> getChangedBlocks() {
        final List<DiffBlock> changed = new ArrayList<>();
        for (final DiffBlock block : blocks) {
            if (block.isDifference()) {
                changed.add(block);
            }
        }
        return changed;
    }

    /// @return True if the texts have the same lines (line endings are ignored).
    public boolean isIdentical() {
        for (final DiffBlock block : blocks) {
            if (block.isDifference()) {
                return false;
            }
        }
        return true;
    }

    /// @return The number of new lines that were inserted or changed to.
    public int getAddedLineCount() {
        int count = 0;
        for (final DiffBlock block : blocks) {
            if (block.isDifference()) {
                count += block.getNewCount();
            }
        }
        return count;
    }

    /// @return The number of old lines that were deleted or changed from.
    public int getRemovedLineCount() {
        int count = 0;
        for (final DiffBlock block : blocks) {
            if (block.isDifference()) {
                count += block.getOldCount();
            }
        }
        return count;
    }

    /// Every row of a side by side view of the two texts, in order.
    ///
    /// In a changed block the old and new lines are paired in order, each pair being a
    /// [DiffType#CHANGE] row with its changed characters; any extra old lines follow as
    /// [DiffType#DELETE] rows, or extra new lines as [DiffType#INSERT] rows.
    ///
    /// @return The rows.
    public List<DiffLine> getLines() {
        final List<DiffLine> lines = new ArrayList<>();
        for (final DiffBlock block : blocks) {
            addLines(block, lines);
        }
        return lines;
    }

    /// Groups the changes into hunks with some lines of unchanged context around each, as in a unified
    /// diff. Changes whose context would meet or overlap share a hunk.
    ///
    /// @param contextLines The number of unchanged lines to show before and after each change.
    /// @return The hunks, in order. Empty if the texts are identical.
    public List<DiffHunk> getHunks(final int contextLines) {
        final int context = Math.max(0, contextLines);
        final List<DiffLine> lines = getLines();
        final int size = lines.size();

        // The number of old and new lines before each row.
        final int[] oldBefore = new int[size + 1];
        final int[] newBefore = new int[size + 1];
        for (int i = 0; i < size; i++) {
            final DiffLine line = lines.get(i);
            oldBefore[i + 1] = oldBefore[i] + (line.hasOld() ? 1 : 0);
            newBefore[i + 1] = newBefore[i] + (line.hasNew() ? 1 : 0);
        }

        final List<DiffHunk> hunks = new ArrayList<>();
        int hunkStart = -1;
        int hunkEnd = -1;
        int i = 0;
        while (i < size) {
            if (lines.get(i).getType() == DiffType.EQUAL) {
                i++;
            } else {
                final int changeStart = i;
                while (i < size && lines.get(i).getType() != DiffType.EQUAL) {
                    i++;
                }
                final int start = Math.max(0, changeStart - context);
                final int end = Math.min(size, i + context);
                if (hunkStart >= 0 && start <= hunkEnd) {
                    hunkEnd = end;
                } else {
                    if (hunkStart >= 0) {
                        hunks.add(createHunk(lines, oldBefore, newBefore, hunkStart, hunkEnd));
                    }
                    hunkStart = start;
                    hunkEnd = end;
                }
            }
        }
        if (hunkStart >= 0) {
            hunks.add(createHunk(lines, oldBefore, newBefore, hunkStart, hunkEnd));
        }
        return hunks;
    }

    /// The diff in unified diff form, without the file name header lines: each hunk's `@@` header
    /// followed by its lines, prefixed with a space (unchanged), `-` (removed) or `+` (added). In each
    /// change the removed lines come before the added ones.
    ///
    /// @param contextLines The number of unchanged lines to show before and after each change.
    /// @return The unified diff, with lines separated by `\n`. Empty if the texts are identical.
    public String toUnifiedDiff(final int contextLines) {
        final StringBuilder sb = new StringBuilder();
        for (final DiffHunk hunk : getHunks(contextLines)) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(hunk.getHeader());
            final List<DiffLine> lines = hunk.getLines();
            int i = 0;
            while (i < lines.size()) {
                if (lines.get(i).getType() == DiffType.EQUAL) {
                    sb.append("\n ").append(lines.get(i).getOldText());
                    i++;
                } else {
                    final int changeStart = i;
                    while (i < lines.size() && lines.get(i).getType() != DiffType.EQUAL) {
                        i++;
                    }
                    for (int j = changeStart; j < i; j++) {
                        if (lines.get(j).hasOld()) {
                            sb.append("\n-").append(lines.get(j).getOldText());
                        }
                    }
                    for (int j = changeStart; j < i; j++) {
                        if (lines.get(j).hasNew()) {
                            sb.append("\n+").append(lines.get(j).getNewText());
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    /// The changes as line replacements on the old text which, applied together, turn it into the new
    /// text. Use with [TextEdits#fromLineReplacements(List, List)] to apply them to an editor.
    ///
    /// @return One replacement per changed block, in order.
    public List<LineReplacement> getLineReplacements() {
        final List<LineReplacement> replacements = new ArrayList<>();
        for (final DiffBlock block : blocks) {
            if (block.isDifference()) {
                replacements.add(new LineReplacement(
                        block.getOldFrom(),
                        block.getOldTo(),
                        newLines.subList(block.getNewFrom(), block.getNewTo())));
            }
        }
        return replacements;
    }

    private void addLines(final DiffBlock block, final List<DiffLine> lines) {
        switch (block.getType()) {
            case EQUAL:
                for (int i = 0; i < block.getOldCount(); i++) {
                    final int oldIndex = block.getOldFrom() + i;
                    final int newIndex = block.getNewFrom() + i;
                    lines.add(new DiffLine(DiffType.EQUAL, oldIndex, newIndex,
                            oldLines.get(oldIndex), newLines.get(newIndex), null, null));
                }
                break;
            case DELETE:
                addDeletes(block.getOldFrom(), block.getOldTo(), lines);
                break;
            case INSERT:
                addInserts(block.getNewFrom(), block.getNewTo(), lines);
                break;
            case CHANGE:
                final int paired = Math.min(block.getOldCount(), block.getNewCount());
                for (int i = 0; i < paired; i++) {
                    final int oldIndex = block.getOldFrom() + i;
                    final int newIndex = block.getNewFrom() + i;
                    final String oldText = oldLines.get(oldIndex);
                    final String newText = newLines.get(newIndex);
                    final CharDiff charDiff = CharDiff.of(oldText, newText);
                    lines.add(new DiffLine(DiffType.CHANGE, oldIndex, newIndex, oldText, newText,
                            charDiff.getOldRanges(), charDiff.getNewRanges()));
                }
                addDeletes(block.getOldFrom() + paired, block.getOldTo(), lines);
                addInserts(block.getNewFrom() + paired, block.getNewTo(), lines);
                break;
            default:
                throw new IllegalStateException("Unexpected type " + block.getType());
        }
    }

    private void addDeletes(final int from, final int to, final List<DiffLine> lines) {
        for (int oldIndex = from; oldIndex < to; oldIndex++) {
            lines.add(new DiffLine(DiffType.DELETE, oldIndex, -1, oldLines.get(oldIndex), null, null, null));
        }
    }

    private void addInserts(final int from, final int to, final List<DiffLine> lines) {
        for (int newIndex = from; newIndex < to; newIndex++) {
            lines.add(new DiffLine(DiffType.INSERT, -1, newIndex, null, newLines.get(newIndex), null, null));
        }
    }

    private static DiffHunk createHunk(final List<DiffLine> lines,
                                       final int[] oldBefore,
                                       final int[] newBefore,
                                       final int start,
                                       final int end) {
        return new DiffHunk(
                oldBefore[start],
                oldBefore[end] - oldBefore[start],
                newBefore[start],
                newBefore[end] - newBefore[start],
                new ArrayList<>(lines.subList(start, end)));
    }
}
