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

import stroom.util.shared.diff.LineDiff;
import stroom.util.shared.diff.LineDiffResult;
import stroom.util.shared.diff.LineReplacement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// Applies proposed code changes to a code editor and undoes them ("Undo apply"), keeping track of
/// the applied change and the highlighting of its lines.
///
/// Applying diffs the editor's current code against the proposal and replaces only the changed lines,
/// through the editor's selection, so the editor's undo history is kept. The exact previous text is
/// kept so the whole change can be undone at once, but only while the editor still holds the applied
/// text. The changed lines are highlighted until the next edit by the user, or the next step.
///
/// A change proposed against the code as it was before some of the applied changes can still be
/// applied on top of them, as long as the editor holds the text they left and the change doesn't
/// touch the lines they changed (see [#rebase(String, String)]).
///
/// This holds no GWT dependencies of its own, so it can be unit tested with a fake editor.
public class CodeChangeApplier {

    // How many applied changes are kept for putting later proposals on top of them.
    static final int MAX_APPLIED_CHANGES = 20;

    private final CodeEditor editor;
    // The changes applied (and not undone), oldest first.
    private final List<AppliedCodeChange> appliedChanges = new ArrayList<>();

    // The change that "Undo apply" undoes, or null.
    private AppliedCodeChange appliedChange;
    private boolean applying;
    private boolean showingMarkers;
    private boolean keepMarkersForNextStep;

    /// @param editor The editor to apply changes to.
    public CodeChangeApplier(final CodeEditor editor) {
        this.editor = Objects.requireNonNull(editor);
    }

    /// Applies a proposed change if the editor still holds the code it was proposed against, or that
    /// code with changes applied here since, which the proposed change can be put on top of (see
    /// [#rebase(String, String)]).
    ///
    /// @param baseCode     The code the change was proposed against. Line endings are ignored.
    /// @param proposedCode The whole proposed code.
    /// @return [ApplyCodeChangeResult#CODE_CHANGED] if the change can't be applied to the editor's code,
    /// otherwise as for [#apply(String)].
    public ApplyCodeChangeResult apply(final String baseCode, final String proposedCode) {
        final String rebased = rebase(baseCode, proposedCode);
        if (rebased == null) {
            return ApplyCodeChangeResult.CODE_CHANGED;
        }
        return apply(rebased);
    }

    /// Works out what a proposed change makes of the editor's current code.
    ///
    /// If the editor holds the code the change was proposed against, that is the proposed code. If it
    /// holds that code with changes applied here since (and nothing else), it is the current code with the
    /// proposed change put on top of them, as long as the proposed change doesn't touch the lines they
    /// changed (see [LineDiff#rebase(String, String, String)]). Anything else, such as the user's own
    /// edit, means the change was proposed against code that is no longer there.
    ///
    /// @param baseCode     The code the change was proposed against. Line endings are ignored.
    /// @param proposedCode The whole proposed code.
    /// @return The whole code the change makes of the editor's current code, or null if it can't be
    /// applied to it.
    public String rebase(final String baseCode, final String proposedCode) {
        final String currentCode = editor.getCode();
        if (sameLines(baseCode, currentCode)) {
            return proposedCode;
        }
        // Walks back through the applied changes from the current code, to see if it is the base code
        // with only those changes made to it.
        String code = currentCode;
        for (int i = appliedChanges.size() - 1; i >= 0; i--) {
            final AppliedCodeChange change = appliedChanges.get(i);
            if (!sameLines(change.getAppliedCode(), code)) {
                return null;
            }
            code = change.getPreviousCode();
            if (sameLines(baseCode, code)) {
                final String rebased = LineDiff.rebase(baseCode, proposedCode, currentCode);
                // So the parts the user rejects in a review are numbered as they are in the proposal.
                if (rebased != null
                    && LineDiff.diff(currentCode, rebased).getChangedBlocks().size()
                       != LineDiff.diff(baseCode, proposedCode).getChangedBlocks().size()) {
                    return null;
                }
                return rebased;
            }
        }
        return null;
    }

    /// Applies a proposed change to the editor, replacing only the changed lines, and highlights the
    /// lines it adds or changes.
    ///
    /// @param proposedCode The whole proposed code.
    /// @return [ApplyCodeChangeResult#APPLIED], [ApplyCodeChangeResult#NO_CHANGE] if the proposal is the
    /// same as the current code, or [ApplyCodeChangeResult#NOT_READY] if the editor couldn't take the
    /// change.
    public ApplyCodeChangeResult apply(final String proposedCode) {
        final String previousCode = editor.getCode();
        final LineDiffResult diff = LineDiff.diff(previousCode, proposedCode);
        if (diff.isIdentical()) {
            return ApplyCodeChangeResult.NO_CHANGE;
        }
        if (!replaceLines(diff.getLineReplacements())) {
            return ApplyCodeChangeResult.NOT_READY;
        }
        appliedChange = new AppliedCodeChange(previousCode, editor.getCode(), diff);
        appliedChanges.add(appliedChange);
        if (appliedChanges.size() > MAX_APPLIED_CHANGES) {
            appliedChanges.remove(0);
        }
        final List<int[]> ranges = appliedChange.getChangedLineRanges();
        editor.setChangedLineMarkers(ranges);
        showingMarkers = !ranges.isEmpty();
        keepMarkersForNextStep = false;
        return ApplyCodeChangeResult.APPLIED;
    }

    /// @return True if a change has been applied and the editor still holds exactly the applied text.
    public boolean canUndo() {
        return appliedChange != null && appliedChange.canUndo(editor.getCode());
    }

    /// Puts back the exact text the editor held before the applied change, through the editor's
    /// selection, if the editor still holds the applied text.
    ///
    /// @return True if the previous text was put back.
    public boolean undo() {
        if (!canUndo()) {
            return false;
        }
        final LineDiffResult diff = LineDiff.diff(editor.getCode(), appliedChange.getPreviousCode());
        if (!replaceLines(diff.getLineReplacements())) {
            return false;
        }
        appliedChanges.remove(appliedChange);
        appliedChange = null;
        clearMarkers();
        return true;
    }

    /// @return The applied change, or null if there isn't one or it has been undone.
    public AppliedCodeChange getAppliedChange() {
        return appliedChange;
    }

    /// @return True while a change is being written to the editor, so the editor's change events
    /// can be told apart from the user's edits.
    public boolean isApplying() {
        return applying;
    }

    /// @return True if the lines of an applied change are highlighted.
    public boolean isShowingMarkers() {
        return showingMarkers;
    }

    /// To be called when the editor's text changes. An edit by the user ends the highlighting; the
    /// edits made while applying a change don't.
    public void onEditorChange() {
        if (!applying) {
            clearMarkers();
        }
    }

    /// Keeps the highlighting through the next step, for "Apply and step".
    public void keepMarkersForNextStep() {
        keepMarkersForNextStep = showingMarkers;
    }

    /// To be called before each step. Ends the highlighting, unless it is being kept for this step.
    public void onBeforeStep() {
        if (keepMarkersForNextStep) {
            keepMarkersForNextStep = false;
        } else {
            clearMarkers();
        }
    }

    /// Removes the highlighting of an applied change's lines.
    public void clearMarkers() {
        if (showingMarkers) {
            editor.setChangedLineMarkers(Collections.emptyList());
        }
        showingMarkers = false;
        keepMarkersForNextStep = false;
    }

    private static boolean sameLines(final String text1, final String text2) {
        return LineDiff.splitLines(text1).equals(LineDiff.splitLines(text2));
    }

    private boolean replaceLines(final List<LineReplacement> replacements) {
        applying = true;
        try {
            return editor.applyLineReplacements(replacements);
        } finally {
            applying = false;
        }
    }


    // --------------------------------------------------------------------------------


    /// The code editor that changes are applied to.
    public interface CodeEditor {

        /// @return The editor's current text.
        String getCode();

        /// Replaces ranges of whole lines, keeping the undo history.
        ///
        /// @param replacements The replacements, with zero based line numbers in the current text.
        /// @return False if the editor couldn't make them (e.g. it hasn't been shown yet).
        boolean applyLineReplacements(List<LineReplacement> replacements);

        /// Highlights whole lines, replacing any lines highlighted before.
        ///
        /// @param ranges Zero based `[fromLine, toLine)` pairs, or an empty list to clear.
        void setChangedLineMarkers(List<int[]> ranges);
    }
}
