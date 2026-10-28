/*
 * Copyright 2016 Crown Copyright
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

package stroom.explorer.client.component;

import stroom.editor.client.component.CodeEditor;
import stroom.explorer.client.FindInContentResultCell;
import stroom.explorer.shared.FindInContentResult;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.button.client.ToolbarToggleButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.FindResultLists;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.form.client.FieldValidity;
import stroom.widget.form.client.TextArea;
import stroom.widget.spinner.client.SpinnerLarge;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.FocusUtil;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.Objects;
import java.util.function.Consumer;

/// The content of Find In Content: a pattern, with Match case and Regex, above the documents whose
/// content it is found in, above the text of the one chosen with what was found highlighted.
public class FindInContent extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    // What may be typed, shown under the pattern and read when it is focused
    private static final String TEXT_HINT = "Finds documents whose content contains the text. "
                                            + "Add tag:name to only search documents with that tag.";
    private static final String REGEX_HINT = "A regular expression, found anywhere in a document's content "
                                             + "(^ and $ are not supported). "
                                             + "Add tag:name to only search documents with that tag.";
    // A pattern of more than one line is taller (find-panel.css)
    private static final String MULTILINE = "find-panel__pattern--multiline";

    @UiField
    TextArea pattern;
    @UiField
    Label patternFeedback;
    @UiField
    Label patternHint;
    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<FindInContentResult> resultList;
    @UiField
    ToolbarToggleButton toggleMatchCase;
    @UiField
    ToolbarToggleButton toggleRegex;
    @UiField
    SpinnerLarge spinner;
    @UiField
    CodeEditor text;

    private final MultiSelectionModelImpl<FindInContentResult> selectionModel = new MultiSelectionModelImpl<>();
    private Runnable onPatternChange = () -> {
    };
    private Consumer<KeyDownEvent> onPatternKeyDown = event -> {
    };
    private Consumer<FindInContentResult> onOpen = result -> {
    };
    private Consumer<FindInContentResult> onSelectionChange = result -> {
    };

    /// Makes empty content.
    public FindInContent() {
        initWidget(BINDER.createAndBindUi(this));
        pattern.addKeyUpHandler(this::onPatternKeyUp);
        pattern.addKeyDownHandler(this::onPatternKeyDown);
        pattern.addValueChangeHandler(this::onPatternValueChange);
        toggleMatchCase.addClickHandler(this::onToggleMatchCase);
        toggleRegex.addClickHandler(this::onToggleRegex);
        // Enter, or a double click, opens a result, and Up on the first goes back to the pattern
        FindResultLists.wire(
                resultList,
                new FindInContentResultCell(),
                selectionModel,
                result -> onOpen.accept(result),
                this::focus);
        selectionModel.addSelectionChangeHandler(event -> onSelectionChange.accept(selectionModel.getSelected()));

        // The pattern keeps the focus while the user types, so a screen reader is told of the
        // feedback as it changes (a Label has no setter for it)
        patternFeedback.getElement().setAttribute("aria-live", "polite");
        patternHint.getElement().setId(DOM.createUniqueId());
        FieldValidity.addDescribedBy(pattern.getElement(), patternHint.getElement().getId(), false);
        updateHint();
    }

    /// Focuses the pattern, all of it selected.
    public void focus() {
        FocusUtil.forceFocus(() -> {
            pattern.setFocus(true);
            pattern.selectAll();
        });
    }

    /// @return The pattern.
    public String getPattern() {
        return pattern.getText();
    }

    /// @return Whether the pattern's case must match.
    public boolean isMatchCase() {
        return toggleMatchCase.getState();
    }

    /// @return Whether the pattern is a regular expression.
    public boolean isRegex() {
        return toggleRegex.getState();
    }

    /// @param onPatternChange Run whenever the pattern, Match case or Regex changes.
    public void setOnPatternChange(final Runnable onPatternChange) {
        this.onPatternChange = Objects.requireNonNull(onPatternChange, "onPatternChange");
    }

    /// @param onPatternKeyDown Given each key pressed in the pattern without Shift, e.g. to open the
    ///                         result chosen on Enter.
    public void setOnPatternKeyDown(final Consumer<KeyDownEvent> onPatternKeyDown) {
        this.onPatternKeyDown = Objects.requireNonNull(onPatternKeyDown, "onPatternKeyDown");
    }

    /// @param onOpen Given a result to open (Enter, or a double click, on it).
    public void setOnOpen(final Consumer<FindInContentResult> onOpen) {
        this.onOpen = Objects.requireNonNull(onOpen, "onOpen");
    }

    /// @param onSelectionChange Given the result chosen, or null for none, whenever it changes.
    public void setOnSelectionChange(final Consumer<FindInContentResult> onSelectionChange) {
        this.onSelectionChange = Objects.requireNonNull(onSelectionChange, "onSelectionChange");
    }

    /// Shows why the pattern is invalid, under it. Not focused, as the user is still typing in it;
    /// the feedback is a live region instead.
    ///
    /// @param message What is wrong with the pattern.
    public void setPatternInvalid(final String message) {
        FieldValidity.setInvalid(pattern, patternFeedback, message);
    }

    /// Clears any message about the pattern being invalid.
    public void setPatternValid() {
        FieldValidity.setValid(pattern, patternFeedback);
    }

    /// @param dataProvider Gives the results, a page at a time.
    public void setDataProvider(final AbstractDataProvider<FindInContentResult> dataProvider) {
        dataProvider.addDataDisplay(resultList);
    }

    /// @return The selection: the result with the focus.
    public MultiSelectionModel<FindInContentResult> getSelectionModel() {
        return selectionModel;
    }

    /// @return The absolute index of the first result shown.
    public int getPageStart() {
        return resultList.getPageStart();
    }

    /// @param pageStart The absolute index of the first result to show.
    public void setPageStart(final int pageStart) {
        resultList.setPageStart(pageStart);
    }

    /// Moves the focus to a result.
    ///
    /// @param row        The row on the page.
    /// @param stealFocus Whether to take the focus from where it is.
    public void setKeyboardSelectedRow(final int row, final boolean stealFocus) {
        resultList.setKeyboardSelectedRow(row, stealFocus);
    }

    /// @return The task monitor that spins the pager's refresh button, for finding the results.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The task monitor that spins over the text, for fetching it.
    public TaskMonitorFactory getTextTaskMonitor() {
        return spinner;
    }

    /// @return The text's editor, for its controller to attach to.
    public CodeEditor getText() {
        return text;
    }

    private void updateHint() {
        patternHint.setText(toggleRegex.getState()
                ? REGEX_HINT
                : TEXT_HINT);
    }

    private void onPatternKeyUp(final KeyUpEvent e) {
        onPatternChange.run();
    }

    private void onPatternKeyDown(final KeyDownEvent e) {
        if (!e.getNativeEvent().getShiftKey()) {
            onPatternKeyDown.accept(e);
        }
    }

    private void onPatternValueChange(final ValueChangeEvent<String> e) {
        pattern.setStyleName(MULTILINE, pattern.getText().contains("\n"));
    }

    private void onToggleMatchCase(final ClickEvent e) {
        onPatternChange.run();
    }

    private void onToggleRegex(final ClickEvent e) {
        updateHint();
        onPatternChange.run();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FindInContent> {

    }
}
