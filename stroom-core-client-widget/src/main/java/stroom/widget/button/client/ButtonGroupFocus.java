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

package stroom.widget.button.client;

import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.Element;

/// Makes a group of buttons that sit together one tab stop, as the WAI-ARIA toolbar pattern does:
/// Tab moves into the group (to the button last used) and out again, the arrow keys move between
/// its buttons (Left and Right, or Up and Down for a vertical group, wrapping at the ends) and
/// Home and End move to the first and last.
///
/// The group is the container element it is installed on, and its buttons are the `<button>`s
/// (and `role="button"` elements) inside it that are shown. Disabled buttons (`aria-disabled`)
/// stay reachable, so their names can still be found; a button with a `tabindex` of -2 or less
/// (e.g. a help button) is left out. A group of two or more buttons is a `role="toolbar"`, named
/// by [#setName(String)]; a single button is left as a plain button.
///
/// One button has `tabindex="0"` and the others `-1` (a roving tab index). The group keeps this as
/// buttons are added, shown, hidden and attached (GWT's `FocusWidget.onAttach` turns a `tabindex`
/// of -1 back into 0) by watching the container's elements.
///
/// If the button with the focus is hidden or removed (e.g. an Edit button that goes when nothing is
/// selected), the focus moves to the button now in its place, rather than to the page.
///
/// Groups can be nested, so adjacent groups can be made one by installing a group on the element
/// that holds them: a group inside another leaves its buttons to the outer group.
public final class ButtonGroupFocus {

    /// The attribute that marks a group's container.
    public static final String GROUP_ATTRIBUTE = "data-button-group";

    private static final String ROLE = "role";
    private static final String TOOLBAR = "toolbar";
    private static final String ARIA_ORIENTATION = "aria-orientation";
    private static final String ARIA_LABEL = "aria-label";
    private static final String BUTTON_SELECTOR = "button, [role=button]";

    private final Element container;
    private boolean vertical;
    private String name;
    // The button that Tab moves to, i.e. the one last used, and where it was among those shown
    private Element current;
    private int currentIndex;
    // Whether the focus is in the group, or was until its button was hidden or removed
    private boolean focusInside;

    private ButtonGroupFocus(final Element container) {
        this.container = container;
        container.setAttribute(GROUP_ATTRIBUTE, "");
    }

    /// Makes the buttons in a container a group, one tab stop.
    ///
    /// @param container The element holding the buttons.
    /// @return The group, e.g. to make it vertical.
    public static ButtonGroupFocus install(final Element container) {
        final ButtonGroupFocus group = new ButtonGroupFocus(container);
        group.listen(container);
        group.update();
        return group;
    }

    /// Sets whether the buttons are in a column, so Up and Down move between them rather than Left
    /// and Right.
    ///
    /// @param vertical Whether the buttons are in a column.
    public void setVertical(final boolean vertical) {
        this.vertical = vertical;
        update();
    }

    /// Names the group, e.g. "Dictionary actions", which a screen reader announces with its role.
    /// The name is only given while the group is a toolbar (has two or more buttons), as a name on
    /// an element with no role is ignored, and flagged by accessibility checkers.
    ///
    /// @param name The group's name, or null for none.
    public void setName(final String name) {
        this.name = name;
        update();
    }

    /// Updates the tab stop and role to suit the buttons shown now. Changes to the container's
    /// elements do this anyway; call it once the container is attached or shown.
    public void update() {
        if (isInsideAnotherGroup()) {
            // The outer group manages these buttons
            removeRole();
            return;
        }
        final JsArray<Element> buttons = buttons();
        if (buttons.length() == 0) {
            removeRole();
            return;
        }
        boolean refocus = false;
        if (current == null) {
            current = buttons.get(0);
        } else if (!contains(buttons, current)) {
            // The button was hidden or removed: its neighbour takes its place, and the focus too if
            // it had the focus and nothing else has taken it (e.g. a Maximise button that hides
            // itself and focuses its Restore button)
            refocus = focusInside && isFocusLost(current);
            current = buttons.get(Math.min(currentIndex, buttons.length() - 1));
        }
        currentIndex = indexOf(buttons, current);
        if (buttons.length() > 1) {
            setAttributeIfChanged(container, ROLE, TOOLBAR);
            if (name != null) {
                setAttributeIfChanged(container, ARIA_LABEL, name);
            }
            if (vertical) {
                setAttributeIfChanged(container, ARIA_ORIENTATION, "vertical");
            } else {
                container.removeAttribute(ARIA_ORIENTATION);
            }
        } else {
            removeRole();
        }
        final JsArray<Element> all = allButtons();
        for (int i = 0; i < all.length(); i++) {
            final Element button = all.get(i);
            if (!isOptedOut(button)) {
                setAttributeIfChanged(button, "tabindex", button == current
                        ? "0"
                        : "-1");
            }
        }
        if (refocus) {
            current.focus();
        }
    }

    private void onFocusIn(final Element target) {
        focusInside = true;
        if (!isInsideAnotherGroup() && contains(buttons(), target) && target != current) {
            current = target;
            update();
        }
    }

    // The focus left the group, unless its button is going because it was hidden or removed
    private void onFocusOut(final Element target, final Element relatedTarget) {
        if (relatedTarget != null && container.isOrHasChild(relatedTarget)) {
            return;
        }
        if (isConnected(target) && isShown(target)) {
            focusInside = false;
        }
    }

    // Returns whether the key moved focus, so the event can be consumed
    private boolean onKeyDown(final Element target, final String key) {
        if (isInsideAnotherGroup()) {
            return false;
        }
        final JsArray<Element> buttons = buttons();
        final int index = indexOf(buttons, target);
        if (index == -1) {
            return false;
        }
        final int last = buttons.length() - 1;
        final int next;
        if (key.equals(vertical
                ? "ArrowDown"
                : "ArrowRight")) {
            next = index == last
                    ? 0
                    : index + 1;
        } else if (key.equals(vertical
                ? "ArrowUp"
                : "ArrowLeft")) {
            next = index == 0
                    ? last
                    : index - 1;
        } else if (key.equals("Home")) {
            next = 0;
        } else if (key.equals("End")) {
            next = last;
        } else {
            return false;
        }
        current = buttons.get(next);
        update();
        current.focus();
        return true;
    }

    private void removeRole() {
        if (TOOLBAR.equals(container.getAttribute(ROLE))) {
            container.removeAttribute(ROLE);
        }
        container.removeAttribute(ARIA_ORIENTATION);
        if (name != null) {
            container.removeAttribute(ARIA_LABEL);
        }
    }

    // The buttons shown in the group, in order
    private JsArray<Element> buttons() {
        final JsArray<Element> all = allButtons();
        final JsArray<Element> shown = JsArray.createArray().cast();
        for (int i = 0; i < all.length(); i++) {
            final Element button = all.get(i);
            if (!isOptedOut(button) && !isNativelyDisabled(button) && isShown(button)) {
                shown.push(button);
            }
        }
        return shown;
    }

    private static boolean isOptedOut(final Element button) {
        final String tabIndex = button.getAttribute("tabindex");
        if (tabIndex == null || tabIndex.isEmpty()) {
            return false;
        }
        try {
            return Integer.parseInt(tabIndex.trim()) <= -2;
        } catch (final NumberFormatException e) {
            // A browser ignores a tabindex that isn't a number
            return false;
        }
    }

    private static boolean contains(final JsArray<Element> elements, final Element element) {
        return indexOf(elements, element) != -1;
    }

    private static int indexOf(final JsArray<Element> elements, final Element element) {
        for (int i = 0; i < elements.length(); i++) {
            if (elements.get(i) == element) {
                return i;
            }
        }
        return -1;
    }

    private static void setAttributeIfChanged(final Element element, final String name, final String value) {
        if (!value.equals(element.getAttribute(name))) {
            element.setAttribute(name, value);
        }
    }

    private native boolean isInsideAnotherGroup() /*-{
        var container = this.@stroom.widget.button.client.ButtonGroupFocus::container;
        var parent = container.parentElement;
        var groupSelector = '[' + @stroom.widget.button.client.ButtonGroupFocus::GROUP_ATTRIBUTE + ']';
        return !!(parent && parent.closest(groupSelector));
    }-*/;

    // All the buttons in the container, including those of any group nested in it (which defers
    // to this one)
    private native JsArray<Element> allButtons() /*-{
        var container = this.@stroom.widget.button.client.ButtonGroupFocus::container;
        var selector = @stroom.widget.button.client.ButtonGroupFocus::BUTTON_SELECTOR;
        return Array.prototype.slice.call(container.querySelectorAll(selector));
    }-*/;

    // Whether the focus has gone nowhere (to the page) or is still on a button that has gone
    private static native boolean isFocusLost(Element previous) /*-{
        var active = previous.ownerDocument.activeElement;
        return !active || active === previous.ownerDocument.body || active === previous || !active.isConnected;
    }-*/;

    private static native boolean isConnected(Element element) /*-{
        return !!element.isConnected;
    }-*/;

    private static native boolean isNativelyDisabled(Element button) /*-{
        return !!button.disabled;
    }-*/;

    // Shown if neither it nor anything between it and the group is display: none or hidden, so a
    // group in a tab that isn't showing still keeps its tab stop
    private native boolean isShown(Element button) /*-{
        var container = this.@stroom.widget.button.client.ButtonGroupFocus::container;
        var win = button.ownerDocument.defaultView;
        for (var node = button; node && node !== container; node = node.parentElement) {
            var style = win.getComputedStyle(node);
            if (style.display === 'none' || style.visibility === 'hidden') {
                return false;
            }
        }
        return true;
    }-*/;

    private native void listen(Element container) /*-{
        var self = this;
        container.addEventListener('focusin', function (event) {
            self.@stroom.widget.button.client.ButtonGroupFocus::onFocusIn(*)(event.target);
        });
        container.addEventListener('focusout', function (event) {
            var target = event.target;
            var relatedTarget = event.relatedTarget;
            // Decided once the change that caused it is complete, as a button being removed can lose
            // the focus while it is still in the page
            Promise.resolve().then(function () {
                self.@stroom.widget.button.client.ButtonGroupFocus::onFocusOut(*)(target, relatedTarget);
            });
        });
        container.addEventListener('keydown', function (event) {
            if (event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) {
                return;
            }
            if (self.@stroom.widget.button.client.ButtonGroupFocus::onKeyDown(*)(event.target, event.key)) {
                event.preventDefault();
                event.stopPropagation();
            }
        });
        // Keep the tab stop as buttons are added, removed, shown, hidden or attached
        var observer = new MutationObserver(function () {
            self.@stroom.widget.button.client.ButtonGroupFocus::update()();
        });
        observer.observe(container, {
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: ['style', 'class', 'tabindex', 'hidden', 'disabled']
        });
    }-*/;
}
