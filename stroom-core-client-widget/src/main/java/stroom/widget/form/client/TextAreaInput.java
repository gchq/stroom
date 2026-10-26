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
import com.google.gwt.user.client.ui.TextArea;

/// A text area whose name and placeholder a template can set, as [TextInput] does for a text box:
///
/// ```
/// <f:TextAreaInput ui:field="info" ariaLabel="Dependency Information" readOnly="true"/>
/// ```
///
/// A blank or null value removes the attribute.
public class TextAreaInput extends TextArea {

    /// @param placeholder The hint shown in the area while it is empty.
    public void setPlaceholder(final String placeholder) {
        setAttribute(getElement(), "placeholder", placeholder);
    }

    /// @param ariaLabel The area's name for screen readers, where no label names it.
    public void setAriaLabel(final String ariaLabel) {
        setAttribute(getElement(), "aria-label", ariaLabel);
    }

    private static void setAttribute(final Element element, final String name, final String value) {
        if (NullSafe.isBlankString(value)) {
            element.removeAttribute(name);
        } else {
            element.setAttribute(name, value);
        }
    }
}
