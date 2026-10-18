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

/// Replaces the text between two editor positions with new text, as an editor selection followed
/// by a replacement of the selected text. Rows and columns are zero based, and the end is exclusive.
///
/// Create these with [TextEdits#fromLineReplacements(java.util.List, java.util.List)], which makes sure
/// the range is never empty when the text has any characters, so applying it is always a replacement
/// of selected text rather than an insertion at the cursor (which an editor may auto-indent or
/// auto-close).
public final class TextEdit {

    private final TextPosition from;
    private final TextPosition to;
    private final String text;

    // Character offsets into the text joined with single newlines. The applied range may include one
    // extra, unchanged character so it is never empty; the logical range is the change itself and is
    // used to work out where positions move to.
    private final int startOffset;
    private final int endOffset;
    private final int logicalStart;
    private final int logicalEnd;
    private final int logicalLength;

    TextEdit(final TextPosition from,
             final TextPosition to,
             final String text,
             final int startOffset,
             final int endOffset,
             final int logicalStart,
             final int logicalEnd,
             final int logicalLength) {
        this.from = from;
        this.to = to;
        this.text = text;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.logicalStart = logicalStart;
        this.logicalEnd = logicalEnd;
        this.logicalLength = logicalLength;
    }

    /// @return The start of the range to replace (inclusive).
    public TextPosition getFrom() {
        return from;
    }

    /// @return The end of the range to replace (exclusive).
    public TextPosition getTo() {
        return to;
    }

    /// @return The text to put in place of the range. Lines are separated by `\n`.
    public String getText() {
        return text;
    }

    int getStartOffset() {
        return startOffset;
    }

    int getEndOffset() {
        return endOffset;
    }

    int getLogicalStart() {
        return logicalStart;
    }

    int getLogicalEnd() {
        return logicalEnd;
    }

    int getLogicalLength() {
        return logicalLength;
    }

    @Override
    public String toString() {
        return from + "-" + to + " -> '" + text + "'";
    }
}
