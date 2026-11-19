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

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;

/// Buttons and links inside a table's cells, e.g. a cell's 'open' icon or a link in a query's
/// results, marked with the [#ATTRIBUTE] attribute.
///
/// A data grid is one tab stop, and its keyboard moves the focus onto a row action (Right along the
/// row's cells), so every row action is out of the tab order (`tabindex="-1"`) and a long grid
/// doesn't cost a tab stop per row (see `DataGridKeyboard`).
///
/// The grid leaves the keys pressed on a row action to it: a cell's button acts on Enter and Space in
/// its cell's `onBrowserEvent` (see [#isKeyPress(NativeEvent)]), as it does on a mouse press, and a
/// link on Enter. A button whose action places something by the pointer (e.g. a menu) can be pressed
/// from the keyboard with [#clickBelow(Element)], which places it by the button.
public final class RowActions {

    /// The attribute that marks a button or link in a cell as a row action.
    public static final String ATTRIBUTE = "data-row-action";
    /// The class of a row action's icon, which shows while its button has the focus (row-action.css).
    public static final String ICON_CLASS_NAME = "row-action__icon";

    private static Template template;

    private RowActions() {
        // Static utility
    }

    /// A button in a cell, named by its title (also its tooltip), holding an icon.
    ///
    /// @param className The button's class names, e.g. the class its cell looks for to know that
    ///                  it was pressed.
    /// @param name      The button's name, e.g. "Open Feed 'MY_FEED'".
    /// @param icon      What the button shows, e.g. an icon from [SvgImageUtil].
    /// @return The button's HTML.
    public static SafeHtml button(final String className, final String name, final SafeHtml icon) {
        if (template == null) {
            template = GWT.create(Template.class);
        }
        return template.button(className, name, icon);
    }

    /// As [#button(String, String, SafeHtml)], for a button that opens a menu: it says so
    /// (`aria-haspopup="menu"`) and whether the menu is open (`aria-expanded`, which the menu keeps up
    /// to date for the button it is opened from), as the ARIA menu button pattern asks.
    ///
    /// @param className The button's classes.
    /// @param name      The button's name, its title.
    /// @param icon      The button's icon.
    /// @return The button's HTML.
    public static SafeHtml menuButton(final String className, final String name, final SafeHtml icon) {
        if (template == null) {
            template = GWT.create(Template.class);
        }
        return template.menuButton(className, name, icon);
    }

    /// @param element An element, or null.
    /// @return Whether the element is a row action.
    private static boolean isRowAction(final Element element) {
        return element != null && element.hasAttribute(ATTRIBUTE);
    }

    /// @param event An event.
    /// @return Whether the event's target is a row action.
    private static boolean isRowActionEvent(final NativeEvent event) {
        final EventTarget target = event.getEventTarget();
        return Element.is(target) && isRowAction(Element.as(target));
    }

    /// @param event An event in a cell.
    /// @return Whether the event is Enter or Space pressed (with no Control, Alt or Meta) on a row
    /// action, which presses it.
    public static boolean isKeyPress(final NativeEvent event) {
        if (!BrowserEvents.KEYDOWN.equals(event.getType())
            || event.getCtrlKey()
            || event.getAltKey()
            || event.getMetaKey()) {
            return false;
        }
        final int keyCode = event.getKeyCode();
        return (keyCode == KeyCodes.KEY_ENTER || keyCode == KeyCodes.KEY_SPACE)
               && isRowActionEvent(event);
    }

    /// @param event A click.
    /// @return Whether the click came from the keyboard (Enter or Space on a button), which has no
    /// pointer position (its `detail` is 0).
    public static native boolean isKeyboardClick(NativeEvent event) /*-{
        return event.detail === 0;
    }-*/;

    /// Clicks a button as the mouse would at its lower left corner, so that what its action places
    /// by the pointer (e.g. a menu) is placed below the button, and the click's target is the button.
    /// For a key press on a row action whose action takes the click.
    ///
    /// @param button The button.
    public static native void clickBelow(Element button) /*-{
        // In the window's coordinates, as a click's are
        var rect = button.getBoundingClientRect();
        button.dispatchEvent(new MouseEvent('click', {
            bubbles: true,
            cancelable: true,
            view: $wnd,
            detail: 1,
            clientX: rect.left,
            clientY: rect.bottom
        }));
    }-*/;


    // --------------------------------------------------------------------------------


    interface Template extends SafeHtmlTemplates {

        @Template("<button type=\"button\" class=\"row-action {0}\" title=\"{1}\" tabindex=\"-1\" "
                  + "data-row-action=\"\">{2}</button>")
        SafeHtml button(String className, String name, SafeHtml icon);

        @Template("<button type=\"button\" class=\"row-action {0}\" title=\"{1}\" tabindex=\"-1\" "
                  + "data-row-action=\"\" aria-haspopup=\"menu\" aria-expanded=\"false\">{2}</button>")
        SafeHtml menuButton(String className, String name, SafeHtml icon);
    }
}
