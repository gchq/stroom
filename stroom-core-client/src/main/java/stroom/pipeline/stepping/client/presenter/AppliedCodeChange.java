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

import stroom.util.shared.diff.DiffBlock;
import stroom.util.shared.diff.LineDiffResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// A proposed code change that has been applied to an element's editor, remembered so it can be undone
/// as a whole ("Undo apply").
///
/// It keeps the exact text the editor held before the change, unsaved edits included, and the text it
/// held straight after. Undoing is only offered while the editor still holds the applied text; once
/// the user has edited it, Ctrl+Z is the way back.
public final class AppliedCodeChange {

    private final String previousCode;
    private final String appliedCode;
    private final LineDiffResult diff;

    /// @param previousCode The editor's exact text before the change was applied.
    /// @param appliedCode  The editor's exact text straight after the change was applied.
    /// @param diff         The diff from the previous code to the proposed code that was applied.
    public AppliedCodeChange(final String previousCode,
                             final String appliedCode,
                             final LineDiffResult diff) {
        this.previousCode = Objects.requireNonNull(previousCode);
        this.appliedCode = Objects.requireNonNull(appliedCode);
        this.diff = Objects.requireNonNull(diff);
    }

    /// @return The editor's exact text before the change was applied.
    public String getPreviousCode() {
        return previousCode;
    }

    /// @return The editor's exact text straight after the change was applied.
    public String getAppliedCode() {
        return appliedCode;
    }

    /// @return The diff from the previous code to the proposed code.
    public LineDiffResult getDiff() {
        return diff;
    }

    /// @param currentCode The editor's current text.
    /// @return True if the editor still holds exactly the applied text, so the change can be undone.
    public boolean canUndo(final String currentCode) {
        return appliedCode.equals(currentCode);
    }

    /// The zero based line ranges of the applied text that the change added or changed, for
    /// highlighting in the editor. Deleted lines leave nothing to highlight.
    ///
    /// @return Pairs of `[fromLine, toLine)`, in order.
    public List<int[]> getChangedLineRanges() {
        final List<int[]> ranges = new ArrayList<>();
        for (final DiffBlock block : diff.getChangedBlocks()) {
            if (block.getNewCount() > 0) {
                ranges.add(new int[]{block.getNewFrom(), block.getNewTo()});
            }
        }
        return ranges;
    }
}
