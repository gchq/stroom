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

import stroom.editor.client.presenter.EditorPresenter;
import stroom.editor.client.view.Marker;
import stroom.pipeline.stepping.client.event.SteppingCodeChangeEvent.Approver;
import stroom.pipeline.stepping.client.presenter.CodeChangeReviewPresenter.CodeChangeReviewView;
import stroom.pipeline.stepping.client.presenter.SideBySideDiff.Side;
import stroom.util.shared.diff.CharRange;
import stroom.util.shared.diff.LineDiff;
import stroom.util.shared.diff.LineDiffResult;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.Scheduler;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;
import edu.ycp.cs.dh.acegwt.client.ace.AceMarkerType;
import edu.ycp.cs.dh.acegwt.client.ace.AceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/// A dialog for reviewing a proposed code change side by side before applying it: two read only code
/// editors, the current code on the left and the proposed code on the right. Removed lines are tinted
/// red on the left, added lines green on the right and changed lines amber on both, with the changed
/// characters marked more strongly. Blank filler rows keep each change lined up, the two sides scroll
/// together, and Previous and Next move between the changes.
///
/// The user can reject any of the changes (e.g. a removed final line break) with "Reject change", which marks
/// it on both sides and leaves it out of what is applied.
///
/// The buttons are "Apply to editor", "Apply and step" and "Cancel"; what applying does is up to the
/// caller (see [#show(String, String, String, AceEditorMode, boolean, ReviewHandler)]), or use
/// [#showForElement(ElementPresenter, String, String, boolean, Approver, Consumer)] for a stepping element's
/// code.
public class CodeChangeReviewPresenter
        extends MyPresenterWidget<CodeChangeReviewView>
        implements CodeChangeReviewUiHandlers {

    /// Shown when the element's code has changed since the change was proposed.
    public static final String CODE_CHANGED_MESSAGE =
            "The code has changed since this was proposed. Ask again for a change to the current code.";
    /// Shown when the user may not edit the element's document.
    public static final String NO_PERMISSION_MESSAGE =
            "You don't have permission to edit this element's document, so the change can't be applied.";

    private static final String REMOVED_CLASS = "codeDiff-removed";
    private static final String ADDED_CLASS = "codeDiff-added";
    private static final String CHANGED_CLASS = "codeDiff-changed";
    private static final String FILLER_CLASS = "codeDiff-filler";
    private static final String REMOVED_CHARS_CLASS = "codeDiff-removedChars";
    private static final String ADDED_CHARS_CLASS = "codeDiff-addedChars";
    private static final String REJECTED_CLASS = "codeDiff-rejected";
    /// Shown when every change has been rejected.
    static final String ALL_REJECTED_MESSAGE = "Every change is rejected, so there is nothing to apply.";

    private final EditorPresenter leftEditor;
    private final EditorPresenter rightEditor;

    private SideBySideDiff sideBySideDiff;
    private LineDiffResult diff;
    private String currentCode;
    private String proposedCode;
    // The changes the user has rejected, by their index among the diff's changed blocks.
    private final Set<Integer> rejected = new TreeSet<>();
    // Why the change can't be applied, whatever is rejected, or null.
    private String problem;
    private ReviewHandler handler;
    private int currentChange = -1;
    private boolean syncingScroll;

    @Inject
    public CodeChangeReviewPresenter(final EventBus eventBus,
                                     final CodeChangeReviewView view,
                                     final Provider<EditorPresenter> editorProvider) {
        super(eventBus, view);
        this.leftEditor = createEditor(editorProvider);
        this.rightEditor = createEditor(editorProvider);
        view.setUiHandlers(this);
        view.setLeftView(leftEditor.getView());
        view.setRightView(rightEditor.getView());

        leftEditor.addScrollListener(() -> syncScroll(leftEditor, rightEditor));
        rightEditor.addScrollListener(() -> syncScroll(rightEditor, leftEditor));
    }

    /// Shows the dialog to review a proposed change to a stepping element's code, applying it to the
    /// element's editor (unsaved) if the user chooses to.
    ///
    /// If the user may not edit the element's document, or its code is no longer the code the change was
    /// proposed against, the dialog says so and won't apply it. These are checked again when the user
    /// applies it, before and after asking the approver.
    ///
    /// @param element      The element whose code the change is for.
    /// @param baseCode     The code the change was proposed against (shown on the left).
    /// @param proposedCode The whole proposed code (shown on the right).
    /// @param editable     False if the user may not edit the element's document.
    /// @param approver     Asked before the change is applied (e.g. to check and record it on the server),
    ///                     or null to apply it straight away.
    /// @param onApplied    Called with [ApplyCodeChangeResult#APPLIED] once the change has been applied
    ///                     (and, for "Apply and step", the step requested). May be null.
    public void showForElement(final ElementPresenter element,
                               final String baseCode,
                               final String proposedCode,
                               final boolean editable,
                               final Approver approver,
                               final Consumer<ApplyCodeChangeResult> onApplied) {
        final String caption = "Review Proposed Change"
                               + (element.getElement() != null
                ? " - " + element.getElement().getId()
                : "");
        final ReviewHandler reviewHandler = (andStep, code, rejectedChanges, onProblem) -> {
            final String problem = checkElement(element, baseCode, editable);
            if (problem != null) {
                onProblem.accept(problem);
            } else if (approver == null) {
                onProblem.accept(applyToElement(element, baseCode, code, andStep, onApplied));
            } else {
                approver.approve(andStep, rejectedChanges, refusal -> onProblem.accept(refusal != null
                        ? refusal
                        : applyToElement(element, baseCode, code, andStep, onApplied)));
            }
        };

        show(caption, baseCode, proposedCode, element.getCodeMode(), true, reviewHandler);

        final String problem = checkElement(element, baseCode, editable);
        if (problem != null) {
            showProblem(problem);
        }
    }

    /// @param element  The element.
    /// @param baseCode The code a change was proposed against.
    /// @param editable False if the user may not edit the element's document.
    /// @return Why a change to the element's code can't be applied, or null if it can.
    static String checkElement(final ElementPresenter element, final String baseCode, final boolean editable) {
        if (!editable) {
            return NO_PERMISSION_MESSAGE;
        } else if (!element.canApplyCodeChange()) {
            return getMessage(ApplyCodeChangeResult.NOT_EDITABLE);
        } else if (!LineDiff.diff(baseCode, element.getCode()).isIdentical()) {
            return CODE_CHANGED_MESSAGE;
        }
        return null;
    }

    private static String applyToElement(final ElementPresenter element,
                                         final String baseCode,
                                         final String proposedCode,
                                         final boolean andStep,
                                         final Consumer<ApplyCodeChangeResult> onApplied) {
        final ApplyCodeChangeResult result = andStep
                ? element.applyCodeChangeAndStep(baseCode, proposedCode)
                : element.applyCodeChange(baseCode, proposedCode);
        if (result == ApplyCodeChangeResult.APPLIED) {
            if (onApplied != null) {
                onApplied.accept(result);
            }
            return null;
        }
        return getMessage(result);
    }

    /// Shows the dialog to review a proposed change.
    ///
    /// @param caption       The dialog caption.
    /// @param currentCode   The current code (shown on the left).
    /// @param proposedCode  The proposed code (shown on the right).
    /// @param mode          The editor mode for the code's syntax highlighting.
    /// @param stepAvailable True to offer "Apply and step".
    /// @param handler       Applies the change when the user asks to.
    public void show(final String caption,
                     final String currentCode,
                     final String proposedCode,
                     final AceEditorMode mode,
                     final boolean stepAvailable,
                     final ReviewHandler handler) {
        this.handler = handler;
        this.currentCode = currentCode;
        this.proposedCode = proposedCode;
        this.diff = LineDiff.diff(currentCode, proposedCode);
        this.sideBySideDiff = SideBySideDiff.of(diff);
        this.currentChange = -1;
        this.rejected.clear();
        this.problem = null;

        leftEditor.setMode(mode);
        leftEditor.setText(sideBySideDiff.getLeftText());
        leftEditor.setLineNumberLabels(sideBySideDiff.getLeftLabels());
        rightEditor.setMode(mode);
        rightEditor.setText(sideBySideDiff.getRightText());
        rightEditor.setLineNumberLabels(sideBySideDiff.getRightLabels());
        updateMarkers();

        final CodeChangeReviewView view = getView();
        view.setMessage(null);
        view.setStepAvailable(stepAvailable);
        updateRejection();
        updateNavigation();

        ShowPopupEvent.builder(this)
                .popupType(PopupType.DIALOG)
                .popupSize(PopupSize.resizable(1200, 700, 600, 400))
                .caption(caption)
                .modal()
                .onShow(e -> Scheduler.get().scheduleDeferred(this::onNextChange))
                .onHideRequest(e -> e.hide())
                .fire();
    }

    /// Shows a message in the dialog saying why the change can't be applied, and disables applying.
    ///
    /// @param message The message.
    public void showProblem(final String message) {
        problem = message;
        getView().setMessage(message);
        getView().setApplyEnabled(false);
    }

    @Override
    public void onToggleRejectChange() {
        if (sideBySideDiff == null || currentChange < 0) {
            return;
        }
        if (!rejected.remove(currentChange)) {
            rejected.add(currentChange);
        }
        updateMarkers();
        updateRejection();
    }

    /// @return The code to apply: the proposed code without the changes the user rejected.
    String codeToApply() {
        return rejected.isEmpty()
                ? proposedCode
                : diff.withChangesExcept(rejected, LineDiff.lineSeparatorOf(currentCode));
    }

    // Shows what is rejected: the summary, the reject button, and whether anything is left to apply.
    private void updateRejection() {
        final int changes = sideBySideDiff.getChangeStartRows().size();
        final CodeChangeReviewView view = getView();
        view.setSummary(summary(diff, rejected.size(), changes, codeToApply(), currentCode));
        view.setRejectEnabled(currentChange >= 0);
        view.setRejected(rejected.contains(currentChange));
        final boolean allRejected = changes > 0 && rejected.size() >= changes;
        if (problem == null) {
            view.setMessage(allRejected
                    ? ALL_REJECTED_MESSAGE
                    : null);
            view.setApplyEnabled(!diff.isIdentical() && !allRejected);
        }
    }

    /// @param diff     The proposed change.
    /// @param rejected How many of its changes are rejected.
    /// @param changes  How many changes it has.
    /// @param applied  The code that applying would give.
    /// @param current  The current code.
    /// @return The summary, e.g. "+3 −1 lines", or with some rejected "+1 −1 lines (1 of 2 changes rejected)".
    static String summary(final LineDiffResult diff,
                          final int rejected,
                          final int changes,
                          final String applied,
                          final String current) {
        if (rejected == 0) {
            return CodeDiffCardRenderer.summary(diff);
        }
        return CodeDiffCardRenderer.summary(LineDiff.diff(current, applied)) + " (" + rejected + " of " + changes
               + (changes == 1
                ? " change"
                : " changes") + " rejected)";
    }

    @Override
    public void onPreviousChange() {
        if (sideBySideDiff != null && currentChange > 0) {
            currentChange--;
            gotoCurrentChange();
        }
    }

    @Override
    public void onNextChange() {
        if (sideBySideDiff != null && currentChange < sideBySideDiff.getChangeStartRows().size() - 1) {
            currentChange++;
            gotoCurrentChange();
        }
    }

    @Override
    public void onApply() {
        apply(false);
    }

    @Override
    public void onApplyAndStep() {
        apply(true);
    }

    @Override
    public void onCancel() {
        hide();
    }

    private void apply(final boolean andStep) {
        if (handler == null) {
            hide();
            return;
        }
        // Not twice while the change is being checked.
        getView().setApplyEnabled(false);
        handler.apply(andStep, codeToApply(), new ArrayList<>(rejected), problem -> {
            if (problem == null) {
                hide();
            } else {
                showProblem(problem);
            }
        });
    }

    private void hide() {
        HidePopupEvent.builder(this).fire();
    }

    private void gotoCurrentChange() {
        final int row = sideBySideDiff.getChangeStartRows().get(currentChange);
        leftEditor.gotoLine(row + 1);
        rightEditor.gotoLine(row + 1);
        updateNavigation();
        updateRejection();
    }

    private void updateNavigation() {
        final int changeCount = sideBySideDiff.getChangeStartRows().size();
        getView().setPreviousEnabled(currentChange > 0);
        getView().setNextEnabled(currentChange < changeCount - 1);
        if (changeCount == 0) {
            getView().setChangePosition("No changes");
        } else if (currentChange < 0) {
            getView().setChangePosition(changeCount + (changeCount == 1
                    ? " change"
                    : " changes"));
        } else {
            getView().setChangePosition("Change " + (currentChange + 1) + " of " + changeCount);
        }
    }

    private void syncScroll(final EditorPresenter from, final EditorPresenter to) {
        if (!syncingScroll) {
            syncingScroll = true;
            try {
                final int scrollTop = from.getScrollTop();
                if (scrollTop >= 0 && scrollTop != to.getScrollTop()) {
                    to.setScrollTop(scrollTop);
                }
            } finally {
                syncingScroll = false;
            }
        }
    }

    private static String getMessage(final ApplyCodeChangeResult result) {
        switch (result) {
            case CODE_CHANGED:
                return CODE_CHANGED_MESSAGE;
            case NO_CHANGE:
                return "The proposed code is the same as the current code.";
            case NOT_EDITABLE:
                return "This element's code can't be edited here.";
            case NOT_READY:
                return "The element's code editor isn't ready. Select the element and try again.";
            default:
                return null;
        }
    }

    private static EditorPresenter createEditor(final Provider<EditorPresenter> editorProvider) {
        final EditorPresenter editor = editorProvider.get();
        editor.setReadOnly(true);
        // Wrapped lines would stop the two sides lining up.
        editor.getLineWrapOption().setOff();
        editor.getLineWrapOption().setUnavailable();
        editor.getLineNumbersOption().setOn();
        editor.getIndicatorsOption().setOff();
        editor.getIndicatorsOption().setUnavailable();
        editor.getViewAsHexOption().setUnavailable();
        return editor;
    }

    // Marks the changes on both sides, with the rejected ones marked over them.
    private void updateMarkers() {
        final List<Marker> leftMarkers = buildMarkers(sideBySideDiff.getLeft(), sideBySideDiff.getLeftCharChanges(),
                REMOVED_CHARS_CLASS);
        final List<Marker> rightMarkers = buildMarkers(sideBySideDiff.getRight(),
                sideBySideDiff.getRightCharChanges(), ADDED_CHARS_CLASS);
        for (final int change : rejected) {
            final int start = sideBySideDiff.getChangeStartRows().get(change);
            final int end = sideBySideDiff.getChangeEndRow(change);
            leftMarkers.add(rejectedMarker(start, end));
            rightMarkers.add(rejectedMarker(start, end));
        }
        leftEditor.setMarkers(leftMarkers);
        rightEditor.setMarkers(rightMarkers);
    }

    private static Marker rejectedMarker(final int startRow, final int endRow) {
        // Ace skips empty ranges, so end at column 1 (a full line marker covers whole rows).
        return new Marker(AceRange.create(startRow, 0, endRow - 1, 1), REJECTED_CLASS, AceMarkerType.FULL_LINE,
                false);
    }

    // One full line marker per run of rows of the same kind, plus a text marker for each changed range
    // of characters.
    private static List<Marker> buildMarkers(final List<Side> sides,
                                             final List<List<CharRange>> charChanges,
                                             final String charClass) {
        final List<Marker> markers = new ArrayList<>();
        int row = 0;
        while (row < sides.size()) {
            final Side side = sides.get(row);
            final int runStart = row;
            while (row < sides.size() && sides.get(row) == side) {
                row++;
            }
            final String lineClass = getLineClass(side);
            if (lineClass != null) {
                // Ace skips empty ranges, so end at column 1 (a full line marker covers whole rows).
                markers.add(new Marker(
                        AceRange.create(runStart, 0, row - 1, 1),
                        lineClass,
                        AceMarkerType.FULL_LINE,
                        false));
            }
        }

        for (int i = 0; i < charChanges.size(); i++) {
            for (final CharRange range : charChanges.get(i)) {
                markers.add(new Marker(
                        AceRange.create(i, range.getStart(), i, range.getEnd()),
                        charClass,
                        AceMarkerType.TEXT,
                        false));
            }
        }
        return markers;
    }

    private static String getLineClass(final Side side) {
        switch (side) {
            case REMOVED:
                return REMOVED_CLASS;
            case ADDED:
                return ADDED_CLASS;
            case CHANGED:
                return CHANGED_CLASS;
            case FILLER:
                return FILLER_CLASS;
            default:
                return null;
        }
    }


    // --------------------------------------------------------------------------------


    /// Applies a reviewed change when the user clicks "Apply to editor" or "Apply and step".
    public interface ReviewHandler {

        /// Applies the change.
        ///
        /// @param andStep  True for "Apply and step", which should also re-run the current step.
        /// @param code     The code to apply: the proposed code without any changes the user rejected.
        /// @param rejected The changes the user rejected, by their index among the diff's changed blocks.
        /// @param onResult Given null once the change has been applied, so the dialog closes, or a message
        ///                 saying why it wasn't, which the dialog shows (and stops offering to apply).
        void apply(boolean andStep, String code, List<Integer> rejected, Consumer<String> onResult);
    }


    // --------------------------------------------------------------------------------


    /// The view of the code change review dialog.
    public interface CodeChangeReviewView extends View, HasUiHandlers<CodeChangeReviewUiHandlers> {

        /// @param view The editor for the current code.
        void setLeftView(View view);

        /// @param view The editor for the proposed code.
        void setRightView(View view);

        /// @param summary The summary of the change, e.g. "+3 −1 lines".
        void setSummary(String summary);

        /// @param position Where the user is among the changes, e.g. "Change 2 of 5".
        void setChangePosition(String position);

        /// @param message A message saying why the change can't be applied, or null for none.
        void setMessage(String message);

        /// @param enabled True to enable the apply buttons.
        void setApplyEnabled(boolean enabled);

        /// @param available True to show "Apply and step".
        void setStepAvailable(boolean available);

        /// @param enabled True to enable the Previous button.
        void setPreviousEnabled(boolean enabled);

        /// @param enabled True to enable the Next button.
        void setNextEnabled(boolean enabled);

        /// @param enabled True to enable the button that rejects (or keeps) the current change.
        void setRejectEnabled(boolean enabled);

        /// @param rejected True if the current change is rejected, so the button offers to keep it.
        void setRejected(boolean rejected);
    }
}
