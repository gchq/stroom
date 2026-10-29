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

package stroom.widget.dropdowntree.client;

import stroom.util.shared.NullSafe;
import stroom.widget.component.client.AriaPanel;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FieldValidity;
import stroom.widget.form.client.FormGroup;
import stroom.widget.text.client.Label;
import stroom.widget.util.client.DisabledState;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;

/// A picker: a button (BEM block `drop-down`, `drop-down.css`) showing the chosen value, with a "…"
/// icon at its end, that asks to open a dialog to choose another. A problem with the value (e.g. the
/// document can't be found) is shown as a form shows what is wrong with a field: in its form group's
/// feedback, or, outside a form group, a line of its own under it (`drop-down__feedback`), shown only
/// while there is a problem.
///
/// Its root is a wrapper (`drop-down-box`) that takes no box of its own, holding the button and its
/// feedback line. The classes a template gives it (`addStyleNames`, `styleName`) are the button's, as
/// its look and size are the button's; hiding it hides both.
///
/// It is dumb: a controller (e.g. `DocSelectionController`) attaches to it, sets what it shows and
/// opens the dialog when it is asked to ([#setOnOpen(Runnable)]). Templates can declare it.
public class DropDownBox extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    AriaPanel button;
    @UiField
    Label label;
    // The problem shown under the picker when it isn't in a form group: briefly, and in full for
    // screen readers (the brief form if there is no full one)
    @UiField
    DivElement feedback;
    @UiField
    SpanElement shownProblem;
    @UiField
    SpanElement fullProblem;
    // The problem with the value, briefly and in full, or null for none
    private String problem;
    private String fullProblemText;
    // Where the problem is shown: the form group it is in, or its own feedback line
    private FormGroup problemFormGroup;
    private boolean showingFeedback;

    private boolean enabled = true;
    private Runnable onOpen = () -> {
    };

    public DropDownBox() {
        initWidget(BINDER.createAndBindUi(this));
        feedback.setId(DOM.createUniqueId());
        UIObject.setVisible(feedback, false);
        button.addDomHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                open(event.getNativeEvent());
            }
        }, MouseDownEvent.getType());
        button.addDomHandler(event -> {
            // As a button: Enter or Space opens the dialog
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER || event.getNativeKeyCode() == KeyCodes.KEY_SPACE) {
                event.preventDefault();
                open(event.getNativeEvent());
            }
        }, KeyDownEvent.getType());

        // A problem is shown once the picker is in the page (in its form group, if it is in one)
        addAttachHandler(event -> {
            withdrawProblem();
            if (event.isAttached()) {
                showProblem();
            }
        });
    }

    /// @param onOpen Run when the user asks to choose another value (a click, Enter or Space), even
    ///               while it is disabled or read only, which its controller checks.
    public void setOnOpen(final Runnable onOpen) {
        this.onOpen = onOpen;
    }

    /// Puts the focus on it.
    public void focus() {
        button.getElement().focus();
    }

    /// @param text The chosen value, as shown.
    public void setText(final String text) {
        label.setText(text);
        label.setTitle(text);
    }

    /// Shows a problem with the chosen value (e.g. the document can't be found), as a form shows
    /// what is wrong with a field: under the picker (in its form group's feedback, or a line of its
    /// own), with the picker marked invalid (`aria-invalid`) and described by the message.
    ///
    /// @param message     The problem, briefly, as shown, or null to clear it.
    /// @param fullMessage The full message (e.g. with what to do about it), shown as a tooltip and
    ///                    read by screen readers in place of the short one, or null if the short one
    ///                    says it all.
    public void setProblem(final String message, final String fullMessage) {
        withdrawProblem();
        problem = NullSafe.isBlankString(message)
                ? null
                : message;
        fullProblemText = problem == null || NullSafe.isBlankString(fullMessage)
                ? null
                : fullMessage;
        if (isAttached()) {
            showProblem();
        }
    }

    /// Shows whether the value can be changed: greys the control out (the `disabled` class) and
    /// tells assistive technology (`aria-disabled`). It stays focusable.
    ///
    /// @param enabled Whether the value can be changed.
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        updateState();
    }

    // Read only: the normal field with its value greyed (`drop-down--read-only`). It stays
    // focusable and is `aria-disabled`, as a button can't be read only
    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        updateState();
    }

    // The button, which the classes a template or its owner gives the picker style, as its look and
    // size are the button's (the root is a wrapper with no box of its own)
    @Override
    protected com.google.gwt.user.client.Element getStyleElement() {
        return button.getElement();
    }

    /// Sizes the button, as the wrapper has no box of its own to size.
    ///
    /// @param width The width, as CSS gives it, e.g. `240px`.
    @Override
    public void setWidth(final String width) {
        button.setWidth(width);
    }

    /// Sizes the button, as the wrapper has no box of its own to size.
    ///
    /// @param height The height, as CSS gives it.
    @Override
    public void setHeight(final String height) {
        button.setHeight(height);
    }

    // Shows the problem in the picker's form group, or in its feedback line under it
    private void showProblem() {
        if (problem == null) {
            return;
        }
        problemFormGroup = findFormGroup();
        if (problemFormGroup != null) {
            problemFormGroup.setFeedback(problem, fullProblemText);
        } else {
            // Shown briefly; the full message (or the brief one if there is none) is what screen
            // readers read, and the full one is the tooltip
            shownProblem.setInnerText(problem);
            fullProblem.setInnerText(fullProblemText != null
                    ? fullProblemText
                    : problem);
            if (fullProblemText != null) {
                feedback.setTitle(fullProblemText);
            } else {
                feedback.removeAttribute("title");
            }
            UIObject.setVisible(feedback, true);
            showingFeedback = true;
            final Element pickerElement = button.getElement();
            FieldValidity.setInvalid(pickerElement, true);
            FieldValidity.addDescribedBy(pickerElement, feedback.getId(), true);
        }
    }

    // Takes back what showProblem showed
    private void withdrawProblem() {
        if (problemFormGroup != null) {
            problemFormGroup.clearFeedback();
            problemFormGroup = null;
        }
        if (showingFeedback) {
            final Element pickerElement = button.getElement();
            FieldValidity.setInvalid(pickerElement, false);
            FieldValidity.removeDescribedBy(pickerElement, feedback.getId());
            UIObject.setVisible(feedback, false);
            showingFeedback = false;
        }
    }

    private FormGroup findFormGroup() {
        Widget parent = getParent();
        while (parent != null && !(parent instanceof FormGroup)) {
            parent = parent.getParent();
        }
        return (FormGroup) parent;
    }

    private void updateState() {
        DisabledState.set(button.getElement(), !enabled);
        setStyleName("drop-down--read-only", isReadOnly() && enabled);
        // A button can't be read only, so a read-only picker is a button that does nothing
        if (isReadOnly()) {
            button.getElement().setAttribute(DisabledState.ARIA_DISABLED, "true");
        }
    }

    private void open(final NativeEvent e) {
        e.stopPropagation();
        onOpen.run();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DropDownBox> {

    }
}
