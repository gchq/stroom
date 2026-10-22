/*
 * Copyright 2021 Crown Copyright
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

import stroom.widget.presenter.client.HasUiHandlers;
import stroom.widget.presenter.client.View;

import com.google.gwt.user.client.ui.Focus;

public interface DropDownView extends View, Focus, HasUiHandlers<DropDownUiHandlers> {

    /// @param text The chosen value, as shown.
    void setText(String text);

    /// Shows a problem with the chosen value (e.g. the document can't be found), as a form shows
    /// what is wrong with a field: under the picker (in its form group's feedback, or a line of its
    /// own), with the picker marked invalid (`aria-invalid`) and described by the message.
    ///
    /// @param message     The problem, briefly, as shown, or null to clear it.
    /// @param fullMessage The full message (e.g. with what to do about it), shown as a tooltip and read
    ///                    by screen readers in place of the short one, or null if the short one says
    ///                    it all.
    void setProblem(String message, String fullMessage);

    /// Shows whether the value can be changed: greys the control out (the `disabled` class) and
    /// tells assistive technology (`aria-disabled`). It stays focusable, and it doesn't stop the
    /// popup being requested; the presenter ignores that while disabled.
    ///
    /// @param enabled Whether the value can be changed.
    void setEnabled(boolean enabled);

    /// Shows that the value can be read but not changed: the normal field with its value greyed
    /// (the `readonly` class). It stays focusable and is `aria-disabled`, as a button can't be
    /// read only; the presenter ignores the popup being requested while read only.
    ///
    /// @param readOnly Whether the value is read only.
    void setReadOnly(boolean readOnly);
}
