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

package stroom.widget.form.client;

import stroom.util.shared.NullSafe;

import com.google.gwt.dom.client.Element;

import java.util.function.Function;

/// A text box whose type, placeholder, ARIA role and states a template can set, which UiBinder
/// can't write on a widget's element itself:
///
/// ```
/// <f:TextBox ui:field="filter" type="search" placeholder="Quick Filter"/>
/// <f:TextBox ui:field="value" role="combobox" ariaHasPopup="listbox" ariaExpanded="false"/>
/// ```
///
/// A blank or null value removes the attribute.
///
/// A box whose text must be a value of some kind (e.g. a number) is given a check of its text
/// ([#setInvalidReason(Function)]): while its text fails it, a dialog's OK and a document's Save
/// refuse, and when the box is left the reason is said under it ([InvalidInputs]).
// Stroom's copy of GWT's TextBox (stroom-core-client-widget's com.google.gwt.user.client.ui), which
// gives every text field its text-field.css look
public class TextBox extends com.google.gwt.user.client.ui.TextBox implements HasInvalidInput {

    // Why the text can't be kept, or null if it can; null for a box whose text is anything
    private Function<String, String> invalidReason;

    /// Checks the box's text, e.g. that it is a number from 0 to 1. While the text fails the check,
    /// a dialog's OK and a document's Save refuse, and when the box is left (or Enter is pressed)
    /// the reason is said under it, in its form group.
    ///
    /// @param invalidReason Given the text, says why it can't be kept, e.g. `Must be a number from
    ///                      0 to 1`, or gives null if it can.
    public void setInvalidReason(final Function<String, String> invalidReason) {
        if (this.invalidReason == null && invalidReason != null) {
            addValueChangeHandler(event -> InvalidInputs.showReason(this));
        }
        this.invalidReason = invalidReason;
    }

    @Override
    public String getInvalidReason() {
        return invalidReason == null || !isEnabled() || isReadOnly()
                ? null
                : invalidReason.apply(getText());
    }

    @Override
    public void focusInvalidInput() {
        setFocus(true);
    }

    /// @param type The input's type, e.g. `search`.
    public void setType(final String type) {
        setAttribute(getElement(), "type", type);
    }

    /// @param placeholder The hint shown in the box while it is empty.
    public void setPlaceholder(final String placeholder) {
        setAttribute(getElement(), "placeholder", placeholder);
    }

    /// @param role The box's WAI-ARIA role, e.g. `spinbutton` or `combobox`.
    public void setRole(final String role) {
        setAttribute(getElement(), "role", role);
    }

    /// @param ariaLabel The box's name for screen readers, where no label names it.
    public void setAriaLabel(final String ariaLabel) {
        setAttribute(getElement(), "aria-label", ariaLabel);
    }

    /// @param ariaHasPopup What the box opens, e.g. `listbox` or `dialog`.
    public void setAriaHasPopup(final String ariaHasPopup) {
        setAttribute(getElement(), "aria-haspopup", ariaHasPopup);
    }

    /// @param ariaExpanded Whether what the box opens is open.
    public void setAriaExpanded(final boolean ariaExpanded) {
        getElement().setAttribute("aria-expanded", String.valueOf(ariaExpanded));
    }

    private static void setAttribute(final Element element, final String name, final String value) {
        if (NullSafe.isBlankString(value)) {
            element.removeAttribute(name);
        } else {
            element.setAttribute(name, value);
        }
    }
}
