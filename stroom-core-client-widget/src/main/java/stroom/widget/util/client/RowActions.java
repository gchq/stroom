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
/// A table is one tab stop, its keyboard selected row's cell, and the arrow keys move between its
/// rows. The row actions of the keyboard selected row come next in the tab order (Tab from the row
/// reaches them); those of the other rows are left out of it (`tabindex="-1"`), so a long table
/// doesn't cost a tab stop per row. [stroom.data.grid.client.MyDataGrid] keeps their tab indexes
/// up to date as the row changes, and a table with no keyboard selection leaves all of them in the
/// tab order.
///
/// The table leaves the keys pressed on a row action to it (see [AbstractSelectionEventManager]):
/// a cell's button acts on Enter and Space in its cell's `onBrowserEvent` (see
/// [#isKeyPress(NativeEvent)]), as it does on a mouse press, and a link on Enter.
public final class RowActions {

    /// The attribute that marks a button or link in a cell as a row action.
    public static final String ATTRIBUTE = "data-row-action";

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

    /// @param element An element, or null.
    /// @return Whether the element is a row action.
    public static boolean isRowAction(final Element element) {
        return element != null && element.hasAttribute(ATTRIBUTE);
    }

    /// @param event An event.
    /// @return Whether the event's target is a row action.
    public static boolean isRowActionEvent(final NativeEvent event) {
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

    /// Puts the row actions of the keyboard selected row in the tab order and takes those of the
    /// other rows out of it.
    ///
    /// @param tableBody   The table's body, whose rows are the visible rows.
    /// @param keyboardRow The keyboard selected row, relative to the visible rows, or -1 for none
    ///                    (the table has no keyboard selection), which puts every row action in
    ///                    the tab order.
    public static native void updateTabStops(Element tableBody, int keyboardRow) /*-{
        var actions = tableBody.querySelectorAll('[data-row-action]');
        if (actions.length === 0) {
            return;
        }
        var row = keyboardRow >= 0 && keyboardRow < tableBody.rows.length
            ? tableBody.rows[keyboardRow]
            : null;
        for (var i = 0; i < actions.length; i++) {
            var inTabOrder = keyboardRow < 0 || (row !== null && row.contains(actions[i]));
            actions[i].tabIndex = inTabOrder ? 0 : -1;
        }
    }-*/;


    // --------------------------------------------------------------------------------


    interface Template extends SafeHtmlTemplates {

        @Template("<button type=\"button\" class=\"rowAction {0}\" title=\"{1}\" tabindex=\"-1\" "
                  + "data-row-action=\"\">{2}</button>")
        SafeHtml button(String className, String name, SafeHtml icon);
    }
}
