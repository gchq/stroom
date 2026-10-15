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
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.FocusWidget;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

/// Shows whether a form field's value is valid, both to the eye (the `invalid` class) and to
/// assistive technology (`aria-invalid`, and `aria-describedby` linking the field to the feedback
/// that says what is wrong).
public final class FieldValidity {

    /// The class that shows a field is invalid.
    public static final String INVALID_CLASS = "invalid";

    private static final String ARIA_INVALID = "aria-invalid";
    private static final String ARIA_DESCRIBED_BY = "aria-describedby";

    private FieldValidity() {
        // Static utility
    }

    /// Marks a field invalid, showing why in its feedback.
    ///
    /// @param field    The field.
    /// @param feedback The label under the field that says what is wrong with it.
    /// @param message  What is wrong with the field.
    public static void setInvalid(final Widget field, final Label feedback, final String message) {
        feedback.setText(message);
        setInvalid(field.getElement(), true);
        final Element feedbackElement = feedback.getElement();
        if (NullSafe.isBlankString(feedbackElement.getId())) {
            feedbackElement.setId(DOM.createUniqueId());
        }
        // The message is read first, before any help the field also has
        addDescribedBy(field.getElement(), feedbackElement.getId(), true);
    }

    /// Marks a field valid, clearing its feedback.
    ///
    /// @param field    The field.
    /// @param feedback The label under the field that says what is wrong with it.
    public static void setValid(final Widget field, final Label feedback) {
        feedback.setText("");
        setInvalid(field.getElement(), false);
        removeDescribedBy(field.getElement(), feedback.getElement().getId());
    }

    /// Adds an element to those describing a field (its `aria-describedby`), keeping the others.
    ///
    /// @param field The field.
    /// @param id    The id of the element describing it.
    /// @param first Whether it is read first, rather than last.
    public static void addDescribedBy(final Element field, final String id, final boolean first) {
        setOrRemove(field, ARIA_DESCRIBED_BY, AriaIdRefs.add(field.getAttribute(ARIA_DESCRIBED_BY), id, first));
    }

    /// Removes an element from those describing a field (its `aria-describedby`), keeping the
    /// others.
    ///
    /// @param field The field.
    /// @param id    The id of the element describing it, or null or empty for none.
    public static void removeDescribedBy(final Element field, final String id) {
        if (!NullSafe.isBlankString(id)) {
            setOrRemove(field, ARIA_DESCRIBED_BY, AriaIdRefs.remove(field.getAttribute(ARIA_DESCRIBED_BY), id));
        }
    }

    /// Moves focus to the first of the fields that is marked invalid, so that the user goes straight
    /// to what needs fixing (and a screen reader reads its label and the feedback describing it).
    /// Call it when validation fails.
    ///
    /// @param fields The form's fields, in the order they are shown.
    /// @return True if a field was focused, false if none is marked invalid.
    public static boolean focusFirstInvalid(final FocusWidget... fields) {
        for (final FocusWidget field : fields) {
            if (isInvalid(field.getElement())) {
                field.setFocus(true);
                return true;
            }
        }
        return false;
    }

    /// @param field A field's element.
    /// @return True if the field is marked invalid.
    public static boolean isInvalid(final Element field) {
        return "true".equals(field.getAttribute(ARIA_INVALID));
    }

    /// Marks a field valid or invalid, for a field with no feedback of its own.
    ///
    /// @param field   The field's element, e.g. a text box's `<input>`.
    /// @param invalid Whether the field is invalid.
    public static void setInvalid(final Element field, final boolean invalid) {
        if (invalid) {
            field.addClassName(INVALID_CLASS);
            field.setAttribute(ARIA_INVALID, "true");
        } else {
            field.removeClassName(INVALID_CLASS);
            field.removeAttribute(ARIA_INVALID);
        }
    }

    private static void setOrRemove(final Element element, final String name, final String value) {
        if (value == null) {
            element.removeAttribute(name);
        } else {
            element.setAttribute(name, value);
        }
    }
}
