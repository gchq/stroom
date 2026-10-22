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

package stroom.widget.dropdowntree.client.view;

import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.form.client.FieldValidity;
import stroom.widget.form.client.FormGroup;
import stroom.widget.util.client.DisabledState;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.SvgImageUtil;

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
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

/// A picker: a button (BEM block `drop-down`, `drop-down.css`) showing the chosen value, with a "…"
/// icon at its end, that opens a dialog to choose another. A problem with the value (e.g. the
/// document can't be found) is shown as a form shows what is wrong with a field: in its form group's
/// feedback, or, outside a form group, a line of its own under it (`drop-down__feedback`), added only
/// while there is a problem.
public class DropDownViewImpl extends ViewWithUiHandlers<DropDownUiHandlers>
        implements DropDownView {

    private static final String BLOCK = "drop-down";

    private final Widget widget;
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
    private boolean readOnly;

    @Inject
    public DropDownViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        widget.addDomHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
//                GWT.log(event.getRelativeElement().toString());
                showPopup(event.getNativeEvent());
            }
        }, MouseDownEvent.getType());
        widget.addDomHandler(event -> {
            // As a button: Enter or Space opens the dialog
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER || event.getNativeKeyCode() == KeyCodes.KEY_SPACE) {
                event.preventDefault();
                showPopup(event.getNativeEvent());
            }
        }, KeyDownEvent.getType());
        widget.getElement().setTabIndex(0);
        // To assistive technology it is a button that opens a dialog to choose the value it shows.
        // A FormGroup names it with its label, followed by the value.
        widget.getElement().setAttribute("role", "button");
        widget.getElement().setAttribute("aria-haspopup", "dialog");

        SvgImageUtil.setSvgAsInnerHtml(ellipsesBtnPanel, SvgImage.ELLIPSES_HORIZONTAL);

        // A problem is shown once the picker is in the page (in its form group, if it is in one)
        widget.addAttachHandler(event -> {
            withdrawProblem();
            if (event.isAttached()) {
                showProblem();
            }
        });
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        widget.getElement().focus();
    }

    @Override
    public void setText(final String text) {
        label.setText(text);
        label.setTitle(text);
    }

    @Override
    public void setProblem(final String message, final String fullMessage) {
        withdrawProblem();
        problem = NullSafe.isBlankString(message)
                ? null
                : message;
        fullProblem = problem == null || NullSafe.isBlankString(fullMessage)
                ? null
                : fullMessage;
        if (widget.isAttached()) {
            showProblem();
        }
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
            final Element pickerElement = widget.getElement();
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
            FieldValidity.setInvalid(widget.getElement(), false);
            FieldValidity.removeDescribedBy(widget.getElement(), problemFeedback.getId());
            problemFeedback.removeFromParent();
            problemFeedback = null;
        }
    }

    private FormGroup findFormGroup() {
        Widget parent = widget.getParent();
        while (parent != null && !(parent instanceof FormGroup)) {
            parent = parent.getParent();
        }
        return (FormGroup) parent;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        updateState();
    }

    @Override
    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        updateState();
    }

    private void updateState() {
        DisabledState.set(widget.getElement(), !enabled);
        widget.setStyleName("readonly", readOnly && enabled);
        // A button can't be read only, so a read-only picker is a button that does nothing
        if (readOnly) {
            widget.getElement().setAttribute(DisabledState.ARIA_DISABLED, "true");
        }
    }

    private void showPopup(final NativeEvent e) {
        e.stopPropagation();
        if (getUiHandlers() != null) {
            getUiHandlers().showPopup();
        }
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, DropDownViewImpl> {

    }
}
