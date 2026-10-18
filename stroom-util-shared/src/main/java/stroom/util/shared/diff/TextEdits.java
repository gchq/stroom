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

/// Turns whole line replacements into editor position edits, and works out where a cursor moves to
/// when they are applied. The text is treated as lines joined by single newlines, as the code editor
/// sees it.
///
/// Applying the edits from last to first, each by selecting its range and replacing the selected
/// text, gives the same text as [#apply(List, List)].
public final class TextEdits {

    private TextEdits() {
        // Utility class.
    }

    /// Converts line replacements into editor edits on the given lines.
    ///
    /// Replacements may be in any order; they are sorted by line, and ones that touch are merged.
    ///
    /// @param lines        The current lines of the text, without line terminators (see
    ///                     [LineDiff#splitLines(String)]). An empty list is treated as one empty line.
    /// @param replacements The line replacements to make.
    /// @return The edits, in ascending order of position and not overlapping. Apply them last first so
    /// that the positions of earlier ones stay valid.
    /// @throws IllegalArgumentException If a replacement is outside the text or two of them overlap.
    public static List<TextEdit> fromLineReplacements(final List<String> lines,
                                                      final List<LineReplacement> replacements) {
        final List<String> safeLines = safeLines(lines);
        final int lineCount = safeLines.size();
        final int[] lineStarts = lineStarts(safeLines);
        final String doc = String.join("\n", safeLines);
        final int docLength = doc.length();

        // Work out the character range each replacement changes.
        final List<RawEdit> rawEdits = new ArrayList<>();
        for (final LineReplacement replacement : mergeReplacements(replacements)) {
            if (replacement.getToLine() > lineCount) {
                throw new IllegalArgumentException("Replacement " + replacement
                                                   + " is beyond the end of the text (" + lineCount + " lines)");
            }
            final RawEdit rawEdit = toRawEdit(replacement, safeLines, lineStarts, docLength);
            if (rawEdit != null && (rawEdit.start != rawEdit.end || !rawEdit.text.isEmpty())) {
                addOrMerge(rawEdits, rawEdit, doc);
            }
        }

        // Ranges that touch have been merged, so there is always an unchanged character either side of
        // an empty range (unless the text is empty) that it can take in without overlapping another.
        // The one exception is an insertion at the very end, whose previous character may have been
        // taken in by the edit before it; then the two become one edit.
        final List<TextEdit> edits = new ArrayList<>();
        for (final RawEdit rawEdit : rawEdits) {
            final TextEdit previous = edits.isEmpty()
                    ? null
                    : edits.get(edits.size() - 1);
            if (previous != null
                && rawEdit.start == rawEdit.end
                && rawEdit.end == docLength
                && previous.getEndOffset() == rawEdit.start) {
                edits.set(edits.size() - 1, joinToPrevious(previous, rawEdit, safeLines, lineStarts, doc));
            } else {
                edits.add(toTextEdit(rawEdit, safeLines, lineStarts, doc, docLength));
            }
        }
        return edits;
    }

    /// Applies edits to the lines, as an editor would when they are applied last first.
    ///
    /// @param lines The lines the edits were made from.
    /// @param edits The edits from [#fromLineReplacements(List, List)] for those lines.
    /// @return The new text, with lines separated by `\n`.
    public static String apply(final List<String> lines, final List<TextEdit> edits) {
        final String doc = String.join("\n", safeLines(lines));
        final StringBuilder sb = new StringBuilder();
        int position = 0;
        for (final TextEdit edit : edits) {
            sb.append(doc.substring(position, edit.getStartOffset()));
            sb.append(edit.getText());
            position = edit.getEndOffset();
        }
        sb.append(doc.substring(position));
        return sb.toString();
    }

    /// Works out where a position (e.g. the cursor, or one end of the selection) ends up once the edits
    /// have been applied. A position after an edit moves by the number of characters (and so lines)
    /// added or removed; one inside a replaced range stays at the same offset into the replacement,
    /// limited to its end; one at the point of a pure insertion moves to after the insertion.
    ///
    /// @param lines    The lines the edits were made from.
    /// @param edits    The edits from [#fromLineReplacements(List, List)] for those lines.
    /// @param position The position in the old text. It is limited to the text if beyond it.
    /// @return The position in the new text.
    public static TextPosition adjustPosition(final List<String> lines,
                                              final List<TextEdit> edits,
                                              final TextPosition position) {
        final List<String> safeLines = safeLines(lines);
        final int[] lineStarts = lineStarts(safeLines);
        final int row = Math.max(0, Math.min(position.getRow(), safeLines.size() - 1));
        final int column = Math.max(0, Math.min(position.getColumn(), safeLines.get(row).length()));
        final int offset = lineStarts[row] + column;

        int shift = 0;
        int newOffset = -1;
        for (final TextEdit edit : edits) {
            if (offset < edit.getLogicalStart()) {
                break;
            } else if (offset >= edit.getLogicalEnd()) {
                shift += edit.getLogicalLength() - (edit.getLogicalEnd() - edit.getLogicalStart());
            } else {
                newOffset = edit.getLogicalStart()
                            + shift
                            + Math.min(offset - edit.getLogicalStart(), edit.getLogicalLength());
                break;
            }
        }
        if (newOffset < 0) {
            newOffset = offset + shift;
        }

        final List<String> newLines = LineDiff.splitLines(apply(safeLines, edits));
        return toPosition(newOffset, lineStarts(newLines), newLines);
    }

    // The range of characters (in the text joined with single newlines) that a replacement changes,
    // and the text to replace it with, or null if it changes nothing.
    private static RawEdit toRawEdit(final LineReplacement replacement,
                                     final List<String> lines,
                                     final int[] lineStarts,
                                     final int docLength) {
        final int from = replacement.getFromLine();
        final int to = replacement.getToLine();
        final List<String> newLines = replacement.getNewLines();
        final int lineCount = lines.size();
        final String joined = String.join("\n", newLines);

        if (to > from) {
            if (!newLines.isEmpty()) {
                return new RawEdit(lineStarts[from], lineStarts[to - 1] + lines.get(to - 1).length(), joined);
            } else if (to < lineCount) {
                // Delete the lines and the line break after them.
                return new RawEdit(lineStarts[from], lineStarts[to], "");
            } else if (from > 0) {
                // Delete the last lines and the line break before them.
                return new RawEdit(lineStarts[from] - 1, docLength, "");
            } else {
                return new RawEdit(0, docLength, "");
            }
        } else if (newLines.isEmpty()) {
            return null;
        } else if (from < lineCount) {
            // Insert before line 'from'.
            return new RawEdit(lineStarts[from], lineStarts[from], joined + "\n");
        } else {
            // Append after the last line.
            return new RawEdit(docLength, docLength, "\n" + joined);
        }
    }

    // Adds the edit after the others, joining it to the last one if their ranges touch.
    private static void addOrMerge(final List<RawEdit> rawEdits, final RawEdit rawEdit, final String doc) {
        if (rawEdits.isEmpty()) {
            rawEdits.add(rawEdit);
            return;
        }
        final RawEdit last = rawEdits.get(rawEdits.size() - 1);
        if (rawEdit.start < last.end) {
            throw new IllegalStateException("Edits overlap");
        } else if (rawEdit.start == last.end) {
            rawEdits.set(rawEdits.size() - 1, new RawEdit(
                    last.start,
                    rawEdit.end,
                    last.text + doc.substring(last.end, rawEdit.start) + rawEdit.text));
        } else {
            rawEdits.add(rawEdit);
        }
    }

    private static TextEdit joinToPrevious(final TextEdit previous,
                                           final RawEdit rawEdit,
                                           final List<String> lines,
                                           final int[] lineStarts,
                                           final String doc) {
        final int logicalLength = previous.getLogicalLength()
                                  + doc.substring(previous.getLogicalEnd(), rawEdit.start).length()
                                  + rawEdit.text.length();
        return new TextEdit(
                previous.getFrom(),
                toPosition(rawEdit.end, lineStarts, lines),
                previous.getText() + rawEdit.text,
                previous.getStartOffset(),
                rawEdit.end,
                previous.getLogicalStart(),
                rawEdit.end,
                logicalLength);
    }

    private static TextEdit toTextEdit(final RawEdit rawEdit,
                                       final List<String> lines,
                                       final int[] lineStarts,
                                       final String doc,
                                       final int docLength) {
        int start = rawEdit.start;
        int end = rawEdit.end;
        String text = rawEdit.text;
        if (start == end) {
            // Never leave the range empty, as an empty selection is an insertion at the cursor.
            // Take in one unchanged character either side, and put it back in the replacement.
            if (end < docLength) {
                text = text + doc.charAt(end);
                end++;
            } else if (start > 0) {
                text = doc.charAt(start - 1) + text;
                start--;
            }
        }
        return new TextEdit(
                toPosition(start, lineStarts, lines),
                toPosition(end, lineStarts, lines),
                text,
                start,
                end,
                rawEdit.start,
                rawEdit.end,
                rawEdit.text.length());
    }

    private static List<LineReplacement> mergeReplacements(final List<LineReplacement> replacements) {
        if (replacements == null || replacements.isEmpty()) {
            return Collections.emptyList();
        }
        final List<LineReplacement> sorted = new ArrayList<>(replacements);
        sorted.sort((r1, r2) -> {
            final int compare = Integer.compare(r1.getFromLine(), r2.getFromLine());
            return compare != 0
                    ? compare
                    : Integer.compare(r1.getToLine(), r2.getToLine());
        });

        final List<LineReplacement> merged = new ArrayList<>();
        LineReplacement current = null;
        for (final LineReplacement next : sorted) {
            if (current == null) {
                current = next;
            } else if (next.getFromLine() < current.getToLine()) {
                throw new IllegalArgumentException("Replacements " + current + " and " + next + " overlap");
            } else if (next.getFromLine() == current.getToLine()) {
                final List<String> newLines = new ArrayList<>(current.getNewLines());
                newLines.addAll(next.getNewLines());
                current = new LineReplacement(current.getFromLine(), next.getToLine(), newLines);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private static List<String> safeLines(final List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Collections.singletonList("");
        }
        return lines;
    }

    private static int[] lineStarts(final List<String> lines) {
        final int[] lineStarts = new int[lines.size()];
        int offset = 0;
        for (int i = 0; i < lines.size(); i++) {
            lineStarts[i] = offset;
            offset += lines.get(i).length() + 1;
        }
        return lineStarts;
    }

    private static TextPosition toPosition(final int offset, final int[] lineStarts, final List<String> lines) {
        // Find the last line that starts at or before the offset.
        int low = 0;
        int high = lineStarts.length - 1;
        while (low < high) {
            final int mid = (low + high + 1) / 2;
            if (lineStarts[mid] <= offset) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        final int column = Math.min(offset - lineStarts[low], lines.get(low).length());
        return new TextPosition(low, Math.max(0, column));
    }


    // --------------------------------------------------------------------------------


    private static final class RawEdit {

        private final int start;
        private final int end;
        private final String text;

        private RawEdit(final int start, final int end, final String text) {
            this.start = start;
            this.end = end;
            this.text = text;
        }
    }
}
