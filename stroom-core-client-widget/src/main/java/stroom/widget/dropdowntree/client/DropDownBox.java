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

import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FieldValidity;
import stroom.widget.form.client.FormGroup;
import stroom.widget.util.client.DisabledState;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// A picker: a button (BEM block `drop-down`, `drop-down.css`) showing the chosen value, with a "…"
/// icon at its end, that asks to open a dialog to choose another. A problem with the value (e.g. the
/// document can't be found) is shown as a form shows what is wrong with a field: in its form group's
/// feedback, or, outside a form group, a line of its own under it (`drop-down__feedback`), added only
/// while there is a problem.
///
/// It is dumb: a controller (e.g. `DocSelectionController`) attaches to it, sets what it shows and
/// opens the dialog when it is asked to ([#setOnOpen(Runnable)]). Templates can declare it.
public class DropDownBox extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String BLOCK = "drop-down";

    @UiField
    Label label;
    @UiField
    SimplePanel ellipsesBtnPanel;
    // The problem with the value, briefly and in full, or null for none
    private String problem;
    private String fullProblem;
    // Where the problem is shown: the form group it is in, or its own feedback line
    private FormGroup problemFormGroup;
    private Element problemFeedback;

    private boolean enabled = true;
    private Runnable onOpen = () -> {
    };

    public DropDownBox() {
        initWidget(BINDER.createAndBindUi(this));
        addDomHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                open(event.getNativeEvent());
            }
        }, MouseDownEvent.getType());
        addDomHandler(event -> {
            // As a button: Enter or Space opens the dialog
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER || event.getNativeKeyCode() == KeyCodes.KEY_SPACE) {
                event.preventDefault();
                open(event.getNativeEvent());
            }
        }, KeyDownEvent.getType());

        SvgImageUtil.setSvgAsInnerHtml(ellipsesBtnPanel, SvgImage.ELLIPSES_HORIZONTAL);

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
        getElement().focus();
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
        fullProblem = problem == null || NullSafe.isBlankString(fullMessage)
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

    // Read only: the normal field with its value greyed (the `readonly` class). It stays focusable
    // and is `aria-disabled`, as a button can't be read only
    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        updateState();
    }

    // Shows the problem in the picker's form group, or in a feedback line of its own after it
    private void showProblem() {
        if (problem == null) {
            return;
        }
        problemFormGroup = findFormGroup();
        if (problemFormGroup != null) {
            problemFormGroup.setFeedback(problem, fullProblem);
        } else {
            final Element pickerElement = getElement();
            final Element parent = pickerElement.getParentElement();
            if (parent != null) {
                problemFeedback = DOM.createDiv();
                problemFeedback.setClassName(BLOCK + "__feedback");
                problemFeedback.setId(DOM.createUniqueId());
                final Element shown = DOM.createSpan();
                shown.setInnerText(problem);
                problemFeedback.appendChild(shown);
                if (fullProblem != null) {
                    // The full message is the tooltip, and what screen readers read
                    shown.setAttribute("aria-hidden", "true");
                    final Element full = DOM.createSpan();
                    full.setClassName("sr-only");
                    full.setInnerText(fullProblem);
                    problemFeedback.appendChild(full);
                    problemFeedback.setTitle(fullProblem);
                }
                parent.insertAfter(problemFeedback, pickerElement);
                FieldValidity.setInvalid(pickerElement, true);
                FieldValidity.addDescribedBy(pickerElement, problemFeedback.getId(), true);
            }
        }
    }

    // Takes back what showProblem showed
    private void withdrawProblem() {
        if (problemFormGroup != null) {
            problemFormGroup.clearFeedback();
            problemFormGroup = null;
        }
        if (problemFeedback != null) {
            FieldValidity.setInvalid(getElement(), false);
            FieldValidity.removeDescribedBy(getElement(), problemFeedback.getId());
            problemFeedback.removeFromParent();
            problemFeedback = null;
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
        DisabledState.set(getElement(), !enabled);
        setStyleName("readonly", isReadOnly() && enabled);
        // A button can't be read only, so a read-only picker is a button that does nothing
        if (isReadOnly()) {
            getElement().setAttribute(DisabledState.ARIA_DISABLED, "true");
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
