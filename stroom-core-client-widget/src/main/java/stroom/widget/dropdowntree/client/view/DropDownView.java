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

import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Focus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public interface DropDownView extends View, Focus, HasUiHandlers<DropDownUiHandlers> {

    void setText(String text, boolean hasErrorMsg);

    HandlerRegistration addWarningClickHandler(final MouseDownHandler mouseDownHandler);

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
