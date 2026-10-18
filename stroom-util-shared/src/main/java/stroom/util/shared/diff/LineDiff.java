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
