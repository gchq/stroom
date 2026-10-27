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

package stroom.widget.layout.client;

import com.google.gwt.user.client.ui.ScrollPanel;

/// A scroll panel that fills its parent, its content at least as wide and tall as it
/// (`scroll-pane.css`), e.g. a tree or a list that scrolls in a dialog:
///
/// ```
/// <l:ScrollPane addStyleNames="dock-max form-control-border">
///     <g:SimplePanel ui:field="tree"/>
/// </l:ScrollPane>
/// ```
///
/// Give it more classes with `addStyleNames` (not `styleName`, which would replace its own).
public class ScrollPane extends ScrollPanel {

    private static final String BLOCK = "scroll-pane";

    /// Makes an empty pane.
    public ScrollPane() {
        addStyleName(BLOCK);
        getContainerElement().addClassName(BLOCK + "__content");
    }

    /// @param role The pane's ARIA role, e.g. `region` for content that is named and can be
    ///             scrolled with the keyboard.
    public void setRole(final String role) {
        getElement().setAttribute("role", role);
    }

    /// @param ariaLabel What the pane holds, for screen readers.
    public void setAriaLabel(final String ariaLabel) {
        getElement().setAttribute("aria-label", ariaLabel);
    }

    /// Makes the pane a tab stop, so that content too long to show can be scrolled with the
    /// keyboard, or takes it out of the tab order.
    ///
    /// @param focusable Whether the pane is a tab stop.
    public void setFocusable(final boolean focusable) {
        if (focusable) {
            getElement().setTabIndex(0);
        } else {
            getElement().removeAttribute("tabindex");
        }
    }
}
