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

import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.menu.client.MenuOpener;
import stroom.widget.tooltip.client.FocusTooltip;
import stroom.widget.util.client.DisabledState;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.ButtonBase;

/// An icon button in a [Toolbar]: a `<button class="toolbar-button">` holding its icon and its label.
/// It is named by its title, which is also its tooltip, and styled by toolbar-button.css.
///
/// Its label is shown beside its icon, or in place of it, when the user's toolbar display
/// preference says so (`toolbar-display--*`, on the page); otherwise only the icon shows. The label
/// is the title unless a shorter one is given ([#setLabel(String)]), which the title must start
/// with, so that the button's name always holds what it shows (WCAG 2.5.3, label in name). The
/// label is hidden from screen readers, which read the title.
///
/// A button that a shortcut also presses shows the shortcut in its tooltip, e.g. "Save (Ctrl+S)"
/// (see [#setShortcut(Action)]).
///
/// It is a real button, so Enter and Space press it as they press any button. Disabled, it says so
/// (`aria-disabled`) but stays focusable, so it can still be found in its toolbar and its name
/// heard, and pressing it does nothing.
///
/// Made by [Toolbar#button], or declared in a template inside a toolbar as
/// `<b:ToolbarButton svg="SAVE" title="Save"/>`.
///
/// It is a [IsButton], so controllers that are given their buttons as button views (as views give
/// them the older buttons) work with it unchanged; that interface goes when they no longer need it.
public class ToolbarButton extends ButtonBase implements IsButton {

    // Marks focus that a mouse press gave the button, which toolbar-button.css draws no focus ring for: the
    // button focuses itself from code (see onBrowserEvent), which browsers treat as keyboard focus
    private static final String POINTER_FOCUS_ATTRIBUTE = "data-pointer-focus";
    private static final String ARIA_LABEL = "aria-label";
    private static final String ARIA_KEY_SHORTCUTS = "aria-keyshortcuts";

    private boolean enabled = true;
    // Whether pressing the button with the mouse focuses it
    private boolean focusOnMouseDown = true;
    // What pressing the button does, if set
    private Runnable onClickCallback;
    // The action whose shortcut the tooltip shows, if any (see setShortcut)
    private Action shortcutAction;
    // The title as given, without the shortcut
    private String plainTitle;
    // The label given, or null to show the title
    private String label;
    // The icon, e.g. for a toolbar's More menu to show it
    private SvgImage icon;
    // Shows the label (toolbar-button.css draws it from data-label, and shows or hides it by the
    // toolbar display preference)
    private final Element labelElement;

    public ToolbarButton() {
        super(Document.get().createPushButtonElement());
        setStyleName("toolbar-button");
        labelElement = Document.get().createSpanElement();
        labelElement.setClassName("toolbar-button__label");
        // Read as the title instead, which holds it
        labelElement.setAttribute("aria-hidden", "true");
        getElement().appendChild(labelElement);
        sinkEvents(Event.ONCLICK | Event.ONMOUSEDOWN | Event.ONKEYDOWN | Event.ONBLUR);
        // Shows the button's name when it has the keyboard focus
        FocusTooltip.install();
    }

    /// Sets whether pressing the button with the mouse focuses it (the default), or leaves the focus
    /// where it was, e.g. for a button beside a field that shouldn't take the focus from it.
    ///
    /// @param focusOnMouseDown Whether pressing the button focuses it.
    public void setFocusOnMouseDown(final boolean focusOnMouseDown) {
        this.focusOnMouseDown = focusOnMouseDown;
    }

    /// Marks the button as one that opens a menu, so screen readers announce that it does and
    /// whether the menu is open. The menu must be opened with this button's element as its opener
    /// ([stroom.widget.menu.client.ShowMenuEvent.Builder#opener]), which keeps the open
    /// state up to date. In a template:
    /// `<b:ToolbarButton svg="ELLIPSES_VERTICAL" title="Show Main Menu" opensMenu="true"/>`.
    ///
    /// @param opensMenu Whether the button opens a menu.
    public void setOpensMenu(final boolean opensMenu) {
        if (opensMenu) {
            MenuOpener.mark(getElement());
        } else {
            MenuOpener.unmark(getElement());
        }
    }

    /// Shows the shortcut that does what the button does, as the user has bound it, in the
    /// button's tooltip, e.g. "Save (Ctrl+S)", so that it can be discovered, and tells screen
    /// readers about it (`aria-keyshortcuts`). The button's name stays its title alone. It can be
    /// set before or after the title. The tooltip shows the shortcut bound when the title is set.
    ///
    /// @param action The action the button does, or null for none.
    public void setShortcut(final Action action) {
        shortcutAction = action;
        setTitle(getTitle());
    }

    @Override
    public void setTitle(final String title) {
        plainTitle = title;
        updateLabel();
        final String shortcut = shortcutAction == null
                ? null
                : KeyBinding.getShortcut(shortcutAction);
        final Element element = getElement();
        if (shortcut == null || NullSafe.isBlankString(title)) {
            element.removeAttribute(ARIA_LABEL);
            element.removeAttribute(ARIA_KEY_SHORTCUTS);
            super.setTitle(title);
        } else {
            super.setTitle(title + " (" + shortcut + ")");
            // Named by the title alone, as it was before the shortcut was shown
            element.setAttribute(ARIA_LABEL, title);
            final String ariaShortcut = KeyBinding.getAriaShortcut(shortcutAction);
            if (ariaShortcut == null) {
                element.removeAttribute(ARIA_KEY_SHORTCUTS);
            } else {
                element.setAttribute(ARIA_KEY_SHORTCUTS, ariaShortcut);
            }
        }
    }

    /// @return The title as set, without the shortcut that the tooltip shows after it.
    @Override
    public String getTitle() {
        return plainTitle != null
                ? plainTitle
                : super.getTitle();
    }

    /// Sets the label shown when the toolbar display preference shows text, in place of the title,
    /// e.g. "Batch Edit" for "Batch Edit Permissions For Filtered Documents". The title must start
    /// with it, so that the button's name holds what it shows (`TestToolbarButtonLabels` checks the
    /// templates'). In a template: `<b:ToolbarButton svg="EDIT" title="Edit Permissions" label="Edit"/>`.
    ///
    /// @param label The label, or null to show the title.
    public void setLabel(final String label) {
        this.label = label;
        updateLabel();
    }

    /// @return The label shown when the preference shows text: the label given, or else the title.
    public String getLabel() {
        return NullSafe.isBlankString(label)
                ? getTitle()
                : label;
    }

    /// @return The button's icon, or null if it has none.
    public SvgImage getSvg() {
        return icon;
    }

    // Held as an attribute that toolbar-button.css draws (data-label), not as text, so that the page's
    // text (what text queries and textContent read) doesn't hold it while it is hidden
    private void updateLabel() {
        final String text = getLabel();
        labelElement.setAttribute("data-label", text == null
                ? ""
                : text);
    }

    /// @param icon The button's icon.
    public void setSvg(final SvgImage icon) {
        this.icon = icon;
        final Element holder = Document.get().createDivElement();
        holder.setInnerHTML(icon.getSvg());
        final Element svg = holder.getFirstChildElement();
        // An SVG element's className isn't a string, so set the class attribute itself
        final String classes = svg.getAttribute("class");
        svg.setAttribute("class", (classes == null || classes.isEmpty()
                ? ""
                : classes + " ") + "toolbar-button__icon");
        // The icon is decoration: the button is named by its title
        svg.setAttribute("aria-hidden", "true");
        // In place of the icon before, before the label
        getElement().removeAllChildren();
        getElement().appendChild(svg);
        getElement().appendChild(labelElement);
    }

    /// Sets what pressing the button does, replacing anything set before. There is only ever one,
    /// and it lives as long as the button, so there is no registration to keep or remove.
    ///
    /// @param onClick What to do, or null to do nothing.
    public void setOnClick(final Runnable onClick) {
        if (onClickCallback == null && onClick != null) {
            addClickHandler(event -> {
                if (onClickCallback != null) {
                    onClickCallback.run();
                }
            });
        }
        onClickCallback = onClick;
    }

    /// Disables the button with `aria-disabled` rather than the `disabled` attribute, so it stays
    /// focusable.
    ///
    /// @param enabled Whether the button can be pressed.
    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            getElement().removeAttribute(DisabledState.ARIA_DISABLED);
        } else {
            getElement().setAttribute(DisabledState.ARIA_DISABLED, "true");
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void focus() {
        setFocus(true);
    }

    @Override
    public void onBrowserEvent(final Event event) {
        final int type = DOM.eventGetType(event);
        if (type == Event.ONKEYDOWN || type == Event.ONBLUR) {
            // The keyboard is in use (or the focus has gone), so the focus ring shows again
            getElement().removeAttribute(POINTER_FOCUS_ATTRIBUTE);
        }
        if (!enabled) {
            // Its clicks, including those from Enter and Space, go no further
            if (type == Event.ONCLICK) {
                event.preventDefault();
                event.stopPropagation();
            }
            return;
        }
        if (type == Event.ONMOUSEDOWN && MouseUtil.isPrimary(event)) {
            // The browser doesn't move the focus (or start a drag): the button takes it itself, as
            // not every browser focuses a button that is clicked, unless it is to leave the focus
            // where it is (e.g. in the field beside it)
            event.preventDefault();
            if (focusOnMouseDown) {
                getElement().setAttribute(POINTER_FOCUS_ATTRIBUTE, "");
                setFocus(true);
            }
        }
        super.onBrowserEvent(event);
    }
}
