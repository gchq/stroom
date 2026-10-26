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

package stroom.explorer.client.view;

import stroom.explorer.client.presenter.FindInContentPresenter.FindInContentView;
import stroom.explorer.client.presenter.FindInContentUiHandlers;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.button.client.ToolbarToggleButton;
import stroom.widget.form.client.FieldValidity;
import stroom.widget.form.client.TextAreaInput;
import stroom.widget.presenter.client.View;
import stroom.widget.presenter.client.ViewWithUiHandlers;
import stroom.widget.spinner.client.SpinnerLarge;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusUtil;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class FindInContentViewImpl
        extends ViewWithUiHandlers<FindInContentUiHandlers>
        implements FindInContentView {

    // What may be typed, shown under the pattern and read when it is focused
    private static final String TEXT_HINT = "Finds documents whose content contains the text. "
                                            + "Add tag:name to only search documents with that tag.";
    private static final String REGEX_HINT = "A regular expression, found anywhere in a document's content "
                                             + "(^ and $ are not supported). "
                                             + "Add tag:name to only search documents with that tag.";

    private final Widget widget;

    @UiField
    TextAreaInput pattern;
    @UiField
    Label patternFeedback;
    @UiField
    Label patternHint;
    @UiField
    SimplePanel resultContainer;
    @UiField
    ToolbarToggleButton toggleMatchCase;
    @UiField
    ToolbarToggleButton toggleRegex;
    @UiField
    FlowPanel textContainer;
    @UiField
    SpinnerLarge spinner;

    @Inject
    public FindInContentViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        spinner.setVisible(false);

        pattern.getElement().setAttribute("aria-label", "Find in content");
        // The pattern keeps the focus while the user types, so a screen reader is told of the
        // feedback as it changes
        patternFeedback.getElement().setAttribute("aria-live", "polite");
        patternHint.getElement().setId(DOM.createUniqueId());
        FieldValidity.addDescribedBy(pattern.getElement(), patternHint.getElement().getId(), false);
        updateHint();
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getPattern() {
        return pattern.getValue();
    }

    @Override
    public void setResultView(final View view) {
        resultContainer.setWidget(view.asWidget());
    }

    @Override
    public void setTextView(final View view) {
        textContainer.add(view.asWidget());
    }

    @Override
    public void focus() {
        FocusUtil.forceFocus(() -> {
            pattern.setFocus(true);
            pattern.selectAll();
        });
    }

    @Override
    public void setPatternInvalid(final String message) {
        // Not focused, as the user is still typing in it; the feedback is a live region instead
        FieldValidity.setInvalid(pattern, patternFeedback, message);
    }

    @Override
    public void setPatternValid() {
        FieldValidity.setValid(pattern, patternFeedback);
    }

    private void updateHint() {
        patternHint.setText(toggleRegex.getState()
                ? REGEX_HINT
                : TEXT_HINT);
    }

    @UiHandler("pattern")
    void onPatternKeyUp(final KeyUpEvent e) {
        getUiHandlers().changePattern(pattern.getText(), toggleMatchCase.getState(), toggleRegex.getState());
    }

    @UiHandler("pattern")
    void onPatternKeyDown(final KeyDownEvent e) {
        if (!e.getNativeEvent().getShiftKey()) {
            getUiHandlers().onPatternKeyDown(e);
        }
    }

    @UiHandler("pattern")
    void onPatternChange(final ValueChangeEvent<String> e) {
        if (pattern.getText().contains("\n")) {
            pattern.getElement().addClassName("multiLine");
        } else {
            pattern.getElement().removeClassName("multiLine");
        }
    }

    @UiHandler("toggleMatchCase")
    void onToggleMatchCase(final ClickEvent e) {
        getUiHandlers().changePattern(pattern.getText(), toggleMatchCase.getState(), toggleRegex.getState());
    }

    @UiHandler("toggleRegex")
    void onToggleRegex(final ClickEvent e) {
        updateHint();
        getUiHandlers().changePattern(pattern.getText(), toggleMatchCase.getState(), toggleRegex.getState());
    }

    @Override
    public TaskMonitorFactory getTaskListener() {
        return spinner;
    }

    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, FindInContentViewImpl> {

    }
}
