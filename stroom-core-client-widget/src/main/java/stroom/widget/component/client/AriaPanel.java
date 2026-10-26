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

package stroom.widget.component.client;

import stroom.util.shared.NullSafe;

import com.google.gwt.user.client.ui.FlowPanel;

/// A panel (a `div`) whose ARIA role, states and tab stop a template can set, which UiBinder can't
/// write on a widget's element itself:
///
/// ```
/// <c:AriaPanel styleName="drop-down" role="button" tabIndex="0" ariaHasPopup="dialog">
///     ...
/// </c:AriaPanel>
/// ```
///
/// A blank or null value removes the attribute.
public class AriaPanel extends FlowPanel {

    /// @param role The panel's WAI-ARIA role, e.g. `button`.
    public void setRole(final String role) {
        setAttribute("role", role);
    }

    /// @param tabIndex Where the panel is in the tab order: 0 for where it is in the page, -1 to
    ///                 be focusable only from code.
    public void setTabIndex(final int tabIndex) {
        getElement().setTabIndex(tabIndex);
    }

    /// @param ariaLabel The panel's name for screen readers, where nothing else names it.
    public void setAriaLabel(final String ariaLabel) {
        setAttribute("aria-label", ariaLabel);
    }

    /// @param ariaHasPopup What the panel opens, e.g. `dialog` or `listbox`.
    public void setAriaHasPopup(final String ariaHasPopup) {
        setAttribute("aria-haspopup", ariaHasPopup);
    }

    /// @param ariaExpanded Whether what the panel opens is open.
    public void setAriaExpanded(final boolean ariaExpanded) {
        getElement().setAttribute("aria-expanded", String.valueOf(ariaExpanded));
    }

    /// @param ariaSelected Whether the panel (e.g. a tab) is the one chosen.
    public void setAriaSelected(final boolean ariaSelected) {
        getElement().setAttribute("aria-selected", String.valueOf(ariaSelected));
    }

    /// @param ariaHidden Whether screen readers skip the panel and all it holds.
    public void setAriaHidden(final boolean ariaHidden) {
        if (ariaHidden) {
            getElement().setAttribute("aria-hidden", "true");
        } else {
            getElement().removeAttribute("aria-hidden");
        }
    }

    /// @param ariaLive How screen readers announce changes to what it holds, e.g. `polite`.
    public void setAriaLive(final String ariaLive) {
        setAttribute("aria-live", ariaLive);
    }

    /// @param ariaValueMin The least value of what the panel (e.g. a slider) sets.
    public void setAriaValueMin(final int ariaValueMin) {
        getElement().setAttribute("aria-valuemin", String.valueOf(ariaValueMin));
    }

    /// @param ariaValueMax The greatest value of what the panel (e.g. a slider) sets.
    public void setAriaValueMax(final int ariaValueMax) {
        getElement().setAttribute("aria-valuemax", String.valueOf(ariaValueMax));
    }

    /// @param ariaValueNow The value the panel (e.g. a slider) is at now.
    public void setAriaValueNow(final int ariaValueNow) {
        getElement().setAttribute("aria-valuenow", String.valueOf(ariaValueNow));
    }

    private void setAttribute(final String name, final String value) {
        if (NullSafe.isBlankString(value)) {
            getElement().removeAttribute(name);
        } else {
            getElement().setAttribute(name, value);
        }
    }
}
