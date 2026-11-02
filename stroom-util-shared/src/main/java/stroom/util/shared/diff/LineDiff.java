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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// A line based diff of two texts using the Myers algorithm, with the changed characters found within
/// changed lines.
///
/// Texts are split into lines on `\r\n`, `\n` or `\r`, and the terminators are dropped, so two texts
/// that only differ in their line endings are identical. A text ending in a line break has a final
/// empty line, as in the code editor, so `"a\n"` is the lines `"a"` and `""`.
///
/// This is plain Java so that the server and the browser (through GWT) compute the same diff.
public final class LineDiff {

    private LineDiff() {
        // Utility class.
    }

    /// Diffs two texts line by line.
    ///
    /// @param oldText The old text. Null is treated as empty.
    /// @param newText The new text. Null is treated as empty.
    /// @return The diff.
    public static LineDiffResult diff(final String oldText, final String newText) {
        return diff(splitLines(oldText), splitLines(newText));
    }

    /// Diffs two lists of lines.
    ///
    /// @param oldLines The old lines, without line terminators.
    /// @param newLines The new lines, without line terminators.
    /// @return The diff.
    public static LineDiffResult diff(final List<String> oldLines, final List<String> newLines) {
        final Map<String, Integer> ids = new HashMap<>();
        final int[] a = toIds(oldLines, ids);
        final int[] b = toIds(newLines, ids);

        final List<DiffType> ops = new ArrayList<>(a.length + b.length);
        MyersDiff.diff(a, b, ops);

        return new LineDiffResult(oldLines, newLines, toBlocks(ops));
    }

    /// Puts a change made to a text onto another version of that text (a three way merge by lines), e.g. to
    /// apply a change proposed against some code to the code as it is now, after other changes were made to it.
    ///
    /// Each block of lines the change adds, removes or replaces in `base` is put at the same place in
    /// `current`, as long as `current` left those lines of `base` alone. Blocks of the two that touch but
    /// don't overlap are both kept, e.g. one changing a line and the other the next line. Two insertions at
    /// the same place, or an insertion inside lines the other replaced, clash.
    ///
    /// @param base     The text the change was made to. Null is treated as empty.
    /// @param proposed The changed text. Null is treated as empty.
    /// @param current  The other version of `base`. Null is treated as empty.
    /// @return `current` with the change from `base` to `proposed` put onto it, using the line break of
    /// `current`, or null if the change clashes with the differences between `base` and `current`.
    public static String rebase(final String base, final String proposed, final String current) {
        final List<String> baseLines = splitLines(base);
        final LineDiffResult change = diff(baseLines, splitLines(proposed));
        final LineDiffResult other = diff(baseLines, splitLines(current));
        final List<DiffBlock> changeBlocks = change.getChangedBlocks();
        final List<DiffBlock> otherBlocks = other.getChangedBlocks();
        for (final DiffBlock changeBlock : changeBlocks) {
            for (final DiffBlock otherBlock : otherBlocks) {
                if (clash(changeBlock, otherBlock)) {
                    return null;
                }
            }
        }

        // Walks through the base lines, taking each changed block from whichever side changed it.
        final List<String> merged = new ArrayList<>();
        int basePos = 0;
        int changeIndex = 0;
        int otherIndex = 0;
        while (changeIndex < changeBlocks.size() || otherIndex < otherBlocks.size()) {
            final DiffBlock changeBlock = changeIndex < changeBlocks.size()
                    ? changeBlocks.get(changeIndex)
                    : null;
            final DiffBlock otherBlock = otherIndex < otherBlocks.size()
                    ? otherBlocks.get(otherIndex)
                    : null;
            final boolean takeChange = otherBlock == null
                                       || (changeBlock != null && comesFirst(changeBlock, otherBlock));
            final DiffBlock block;
            final List<String> newLines;
            if (takeChange) {
                block = changeBlock;
                newLines = change.getNewLines();
                changeIndex++;
            } else {
                block = otherBlock;
                newLines = other.getNewLines();
                otherIndex++;
            }
            merged.addAll(baseLines.subList(basePos, block.getOldFrom()));
            merged.addAll(newLines.subList(block.getNewFrom(), block.getNewTo()));
            basePos = block.getOldTo();
        }
        merged.addAll(baseLines.subList(basePos, baseLines.size()));
        return String.join(lineSeparatorOf(current), merged);
    }

    /// @param text A text, or null.
    /// @return The line break it uses (`\r\n`, `\r` or `\n`), or `\n` if it has none.
    public static String lineSeparatorOf(final String text) {
        if (text != null) {
            final int index = text.indexOf('\r');
            if (index >= 0) {
                return index + 1 < text.length() && text.charAt(index + 1) == '\n'
                        ? "\r\n"
                        : "\r";
            }
        }
        return "\n";
    }

    /// Splits text into lines on `\r\n`, `\n` or `\r`, without the line terminators.
    /// There is always at least one line, and a text ending in a line break has a final empty line.
    ///
    /// @param text The text to split. Null is treated as empty.
    /// @return The lines.
    public static List<String> splitLines(final String text) {
        final List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        int lineStart = 0;
        int i = 0;
        final int length = text.length();
        while (i < length) {
            final char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                lines.add(text.substring(lineStart, i));
                if (c == '\r' && i + 1 < length && text.charAt(i + 1) == '\n') {
                    i++;
                }
                lineStart = i + 1;
            }
            i++;
        }
        lines.add(text.substring(lineStart));
        return lines;
    }

    // True if two changed blocks of the same base lines can't both be made: their base lines overlap, they
    // insert at the same place, or one inserts inside the lines the other replaces.
    private static boolean clash(final DiffBlock a, final DiffBlock b) {
        final boolean aInserts = a.getOldCount() == 0;
        final boolean bInserts = b.getOldCount() == 0;
        if (aInserts && bInserts) {
            return a.getOldFrom() == b.getOldFrom();
        } else if (aInserts) {
            return b.getOldFrom() < a.getOldFrom() && a.getOldFrom() < b.getOldTo();
        } else if (bInserts) {
            return a.getOldFrom() < b.getOldFrom() && b.getOldFrom() < a.getOldTo();
        }
        return a.getOldFrom() < b.getOldTo() && b.getOldFrom() < a.getOldTo();
    }

    // True if block a goes before block b in the merged text. Blocks that don't clash and start at the same
    // base line are an insertion and a replacement, and the insertion goes before the lines replaced.
    private static boolean comesFirst(final DiffBlock a, final DiffBlock b) {
        if (a.getOldFrom() != b.getOldFrom()) {
            return a.getOldFrom() < b.getOldFrom();
        }
        return a.getOldCount() == 0;
    }

    private static int[] toIds(final List<String> lines, final Map<String, Integer> ids) {
        final int[] result = new int[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            Integer id = ids.get(line);
            if (id == null) {
                id = ids.size();
                ids.put(line, id);
            }
            result[i] = id;
        }
        return result;
    }

    // Groups the per line operations into runs of equal lines and runs of differing lines. All the
    // differing lines between two runs of equal lines become one block, whatever order the deletes
    // and inserts came in.
    private static List<DiffBlock> toBlocks(final List<DiffType> ops) {
        final List<DiffBlock> blocks = new ArrayList<>();
        int oldPos = 0;
        int newPos = 0;
        int index = 0;
        while (index < ops.size()) {
            final int oldStart = oldPos;
            final int newStart = newPos;
            if (ops.get(index) == DiffType.EQUAL) {
                while (index < ops.size() && ops.get(index) == DiffType.EQUAL) {
                    oldPos++;
                    newPos++;
                    index++;
                }
                blocks.add(new DiffBlock(DiffType.EQUAL, oldStart, oldPos, newStart, newPos));
            } else {
                while (index < ops.size() && ops.get(index) != DiffType.EQUAL) {
                    if (ops.get(index) == DiffType.DELETE) {
                        oldPos++;
                    } else {
                        newPos++;
                    }
                    index++;
                }
                final DiffType type;
                if (oldPos == oldStart) {
                    type = DiffType.INSERT;
                } else if (newPos == newStart) {
                    type = DiffType.DELETE;
                } else {
                    type = DiffType.CHANGE;
                }
                blocks.add(new DiffBlock(type, oldStart, oldPos, newStart, newPos));
            }
        }
        return blocks;
    }
}
