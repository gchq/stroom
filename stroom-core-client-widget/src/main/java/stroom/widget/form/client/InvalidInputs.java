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

import stroom.widget.component.client.Component;

import com.google.gwt.user.client.ui.HasOneWidget;
import com.google.gwt.user.client.ui.HasWidgets;
import com.google.gwt.user.client.ui.Widget;

/// Finds the fields that hold input that can't be kept ([HasInvalidInput]), so that a dialog's OK
/// and a document's Save can refuse while one is shown, rather than keep something other than what
/// was typed.
///
/// A field is found by walking the widgets: panels' children, a [Component]'s own widget and a
/// [FormGroup]'s control. Hidden widgets, and what they hold, are skipped, and so is a read-only
/// component's.
public final class InvalidInputs {

    private InvalidInputs() {
        // Static utility
    }

    /// Finds the first field holding input that can't be kept within a widget, e.g. a dialog's
    /// content or a tab's. The widget itself may be hidden (e.g. a tab not showing); what it holds
    /// is looked at only where shown.
    ///
    /// @param root The widget to look in, or null.
    /// @return The first such field, or null if there is none.
    public static HasInvalidInput find(final Widget root) {
        return find(root, true);
    }

    /// Refuses to go on while a widget holds a field whose input can't be kept: says why under the
    /// first such field and focuses it.
    ///
    /// @param root The widget to look in, e.g. a dialog's content.
    /// @return Whether it refused, i.e. there was such a field.
    public static boolean refuse(final Widget root) {
        final HasInvalidInput input = find(root);
        if (input == null) {
            return false;
        }
        show(input);
        return true;
    }

    /// Says why a field's input can't be kept, under it, and focuses it.
    ///
    /// @param input The field.
    public static void show(final HasInvalidInput input) {
        showReason(input);
        input.focusInvalidInput();
    }

    /// Says why a field's input can't be kept under it, in its [FormGroup], or clears that once it
    /// can be kept, e.g. when the user leaves the field. A field outside a form group says nothing.
    ///
    /// @param input The field.
    public static void showReason(final HasInvalidInput input) {
        final FormGroup formGroup = formGroupOf(input.asWidget());
        if (formGroup != null) {
            formGroup.setInvalidReason(input.getInvalidReason());
        }
    }

    private static HasInvalidInput find(final Widget widget, final boolean root) {
        if (widget == null || (!root && !widget.isVisible())) {
            return null;
        }
        if (widget instanceof final HasInvalidInput input && input.getInvalidReason() != null) {
            return input;
        }
        if (widget instanceof final Component component) {
            // Its own widget is hidden from outside it
            return component.findInvalidInput();
        }
        if (widget instanceof final HasWidgets hasWidgets) {
            for (final Widget child : hasWidgets) {
                final HasInvalidInput found = find(child, false);
                if (found != null) {
                    return found;
                }
            }
        } else if (widget instanceof final HasOneWidget hasOneWidget) {
            return find(hasOneWidget.getWidget(), false);
        }
        return null;
    }

    // The form group a field is in, or null
    private static FormGroup formGroupOf(final Widget field) {
        for (Widget widget = field.getParent(); widget != null; widget = widget.getParent()) {
            if (widget instanceof final FormGroup formGroup) {
                return formGroup;
            }
        }
        return null;
    }
}
