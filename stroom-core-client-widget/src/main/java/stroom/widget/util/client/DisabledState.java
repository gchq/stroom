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

package stroom.widget.util.client;

import com.google.gwt.dom.client.Element;

/// The one way Stroom's own widgets (icon buttons, pickers, menu items and the like) show that they
/// are disabled: the [#CLASS_NAME] class gives them the shared disabled look, and `aria-disabled`
/// tells assistive technology. Unlike the `disabled` attribute, this leaves the element focusable,
/// so keyboard and screen reader users can still find it and hear its name and tooltip; the widget
/// itself must ignore clicks and keys while it is disabled.
///
/// Native form controls (text boxes, check boxes, dialog buttons) use the `disabled` attribute
/// instead.
public final class DisabledState {

    /// The class that gives a disabled widget the shared disabled look.
    public static final String CLASS_NAME = "disabled";
    /// The attribute that tells assistive technology that an element is disabled.
    public static final String ARIA_DISABLED = "aria-disabled";

    private DisabledState() {
        // Static utility
    }

    /// Marks an element as disabled, or no longer disabled.
    ///
    /// @param element  The widget's element.
    /// @param disabled Whether it is disabled.
    public static void set(final Element element, final boolean disabled) {
        if (disabled) {
            element.addClassName(CLASS_NAME);
            element.setAttribute(ARIA_DISABLED, "true");
        } else {
            element.removeClassName(CLASS_NAME);
            element.removeAttribute(ARIA_DISABLED);
        }
    }

    /// Whether an element is marked as disabled by [#set(Element, boolean)].
    ///
    /// @param element The widget's element.
    /// @return Whether it is disabled.
    public static boolean isDisabled(final Element element) {
        return "true".equals(element.getAttribute(ARIA_DISABLED));
    }
}
