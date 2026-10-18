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
import java.util.function.Consumer;

/// A dialog for reviewing a proposed code change side by side before applying it: two read only code
/// editors, the current code on the left and the proposed code on the right. Removed lines are tinted
/// red on the left, added lines green on the right and changed lines amber on both, with the changed
/// characters marked more strongly. Blank filler rows keep each change lined up, the two sides scroll
/// together, and Previous and Next move between the changes.
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

    private final EditorPresenter leftEditor;
    private final EditorPresenter rightEditor;

    private SideBySideDiff sideBySideDiff;
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
        final ReviewHandler reviewHandler = (andStep, onProblem) -> {
            final String problem = checkElement(element, baseCode, editable);
            if (problem != null) {
                onProblem.accept(problem);
            } else if (approver == null) {
                onProblem.accept(applyToElement(element, baseCode, proposedCode, andStep, onApplied));
            } else {
                approver.approve(andStep, refusal -> onProblem.accept(refusal != null
                        ? refusal
                        : applyToElement(element, baseCode, proposedCode, andStep, onApplied)));
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
        final LineDiffResult diff = LineDiff.diff(currentCode, proposedCode);
        this.sideBySideDiff = SideBySideDiff.of(diff);
        this.currentChange = -1;

        setContent(leftEditor, mode, sideBySideDiff.getLeftText(), sideBySideDiff.getLeftLabels(),
                buildMarkers(sideBySideDiff.getLeft(), sideBySideDiff.getLeftCharChanges(), REMOVED_CHARS_CLASS));
        setContent(rightEditor, mode, sideBySideDiff.getRightText(), sideBySideDiff.getRightLabels(),
                buildMarkers(sideBySideDiff.getRight(), sideBySideDiff.getRightCharChanges(), ADDED_CHARS_CLASS));

        final CodeChangeReviewView view = getView();
        view.setSummary(CodeDiffCardRenderer.summary(diff));
        view.setMessage(null);
        view.setApplyEnabled(!diff.isIdentical());
        view.setStepAvailable(stepAvailable);
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
        getView().setMessage(message);
        getView().setApplyEnabled(false);
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
        handler.apply(andStep, problem -> {
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

    private static void setContent(final EditorPresenter editor,
                                   final AceEditorMode mode,
                                   final String text,
                                   final List<String> labels,
                                   final List<Marker> markers) {
        editor.setMode(mode);
        editor.setText(text);
        editor.setLineNumberLabels(labels);
        editor.setMarkers(markers);
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
        /// @param onResult Given null once the change has been applied, so the dialog closes, or a message
        ///                 saying why it wasn't, which the dialog shows (and stops offering to apply).
        void apply(boolean andStep, Consumer<String> onResult);
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
    }
}
