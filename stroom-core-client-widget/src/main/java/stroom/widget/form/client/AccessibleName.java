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

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.user.client.ui.Widget;

import java.util.Locale;

/// Names the form control in a widget for assistive technology (`aria-label`), for a control that
/// no label is for, e.g. one of several in a [FormGroup] (an amount and its unit). The group is
/// named by its label, so the control's name only needs to say which part it is, e.g. `Unit`.
public final class AccessibleName {

    private static final String ARIA_LABEL = "aria-label";

    private AccessibleName() {
        // Static utility
    }

    /// Names the widget's form control: the widget's element if it is an input, select or text
    /// area, otherwise the first one inside it (e.g. the text box of a spinner or selection box).
    ///
    /// @param widget The widget.
    /// @param name   The control's name, e.g. `Unit`.
    public static void set(final Widget widget, final String name) {
        final Element control = findControl(widget.getElement());
        if (control != null) {
            control.setAttribute(ARIA_LABEL, name);
        }
    }

    private static Element findControl(final Element root) {
        if (isControl(root)) {
            return root;
        }
        final NodeList<Element> controls = querySelectorAll(root, "input, select, textarea");
        for (int i = 0; i < controls.getLength(); i++) {
            if (isControl(controls.getItem(i))) {
                return controls.getItem(i);
            }
        }
        return null;
    }

    private static boolean isControl(final Element element) {
        final String tagName = element.getTagName().toLowerCase(Locale.ROOT);
        if ("input".equals(tagName)) {
            return !"hidden".equalsIgnoreCase(element.getAttribute("type"));
        }
        return "select".equals(tagName) || "textarea".equals(tagName);
    }

    private static native NodeList<Element> querySelectorAll(Element root, String selectors) /*-{
        return root.querySelectorAll(selectors);
    }-*/;
}
