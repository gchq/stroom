/*
 * Copyright 2024 Crown Copyright
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

package stroom.cell.info.client;

import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.MenuRequest;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.PopupPosition.PopupLocation;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.Rect;
import stroom.widget.util.client.RowActions;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/// A cell holding an "Actions..." button (an ellipsis) that shows a menu of actions for its row:
/// pressed with the mouse, the menu by the pointer; Enter or Space while it has the focus (the grid's
/// keyboard puts the focus on it), the menu below the button. A row with no actions has no button.
///
/// @param <R> The row's type.
public class ActionMenuCell<R> extends AbstractCell<R> {

    private static final CellIcon ICON = CellIcon.of(SvgImage.ELLIPSES_HORIZONTAL, "Actions...");
    private static final String ACTION_MENU_CELL_CLASS_NAME = "actionMenuCell";

    private final Function<R, String> tooltipExtractor;
    private final Function<R, List<Item>> menuCreator;
    private final Function<R, String> menuNameExtractor;

    /// @param menuCreator       Function to create the menu items to display.
    /// @param tooltipExtractor  Function to supply the tooltip for the ellipses icon, or null for
    ///                          'Actions...'.
    /// @param menuNameExtractor Function to supply the menu's accessible name for a row as plain
    ///                          text, e.g. "Actions for user 'jbloggs'" (see
    ///                          [ShowMenuEvent.Builder#name]), or null (or a function giving null)
    ///                          for "Actions for row N".
    public ActionMenuCell(final Function<R, List<Item>> menuCreator,
                          final Function<R, String> tooltipExtractor,
                          final Function<R, String> menuNameExtractor) {
        super(BrowserEvents.MOUSEDOWN, BrowserEvents.KEYDOWN);

        this.menuCreator = menuCreator;
        this.tooltipExtractor = tooltipExtractor;
        this.menuNameExtractor = menuNameExtractor;
    }

    /// @param menuCreator      Function to create the menu items to display.
    /// @param tooltipExtractor Function to supply the tooltip for the ellipses icon, or null for
    ///                         'Actions...'.
    public ActionMenuCell(final Function<R, List<Item>> menuCreator,
                          final Function<R, String> tooltipExtractor) {
        this(menuCreator, tooltipExtractor, null);
    }

    /// @param menuCreator Function to create the menu items to display.
    public ActionMenuCell(final Function<R, List<Item>> menuCreator) {
        this(menuCreator, null);
    }

    @Override
    public void onBrowserEvent(final Context context,
                               final Element parent,
                               final R row,
                               final NativeEvent event,
                               final ValueUpdater<R> valueUpdater) {
        final EventTarget eventTarget = event.getEventTarget();
        final Element button = parent.getFirstChildElement();
        if (row == null || button == null || !Element.is(eventTarget)
            || !button.isOrHasChild(Element.as(eventTarget))) {
            // Ignore events outside the button
            return;
        }
        if (BrowserEvents.MOUSEDOWN.equals(event.getType()) && MouseUtil.isPrimary(event)) {
            showActionMenu(context, row, button, new PopupPosition(
                    event.getClientX() + 10,
                    event.getClientY() + 10));
            if (valueUpdater != null) {
                valueUpdater.update(row);
            }
        } else if (RowActions.isKeyPress(event)) {
            // Enter or Space: the menu below the button (the browser's own click is prevented)
            event.preventDefault();
            showActionMenu(context, row, button, new PopupPosition(new Rect(button), PopupLocation.BELOW));
            if (valueUpdater != null) {
                valueUpdater.update(row);
            }
        }
    }

    private void showActionMenu(final Context context,
                                final R row,
                                final Element button,
                                final PopupPosition popupPosition) {
        Objects.requireNonNull(menuCreator);
        final List<Item> items = menuCreator.apply(row);
        if (!NullSafe.hasItems(items)) {
            return;
        }
        // The request bubbles up to the page's menu relay (see MenuRequest#relay)
        MenuRequest.fireFromOpener(
                button,
                getMenuName(NullSafe.get(menuNameExtractor, func -> func.apply(row)), context),
                items,
                popupPosition,
                true);
    }

    /// The accessible name of a row's menu: the name given, or else "Actions for row N", where N
    /// is the row's number counting from 1, so that no row's menu is unnamed.
    ///
    /// @param name    The menu's name, or null or blank for none.
    /// @param context The cell's context, giving the row's index.
    /// @return The menu's name.
    static String getMenuName(final String name, final Context context) {
        if (NullSafe.isNonBlankString(name)) {
            return name;
        }
        final int index = context == null
                ? 0
                : context.getIndex();
        return "Actions for row " + (index + 1);
    }

    @Override
    public void render(final Context context,
                       final R row,
                       final SafeHtmlBuilder sb) {
        // A row with no actions has no button: there would be no menu for it to open
        final List<Item> items = row == null || menuCreator == null
                ? null
                : menuCreator.apply(row);
        if (!NullSafe.hasItems(items)) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            final String tooltipText = NullSafe.getOrElse(
                    tooltipExtractor,
                    func -> func.apply(row),
                    "Actions...");

            // A button the grid's keyboard reaches (a row action), named by its title, that says it
            // opens a menu
            sb.append(RowActions.menuButton(
                    "icon-cell--button " + ACTION_MENU_CELL_CLASS_NAME,
                    tooltipText,
                    SvgImageUtil.toSafeHtml(ICON.getIcon(), "icon-cell", RowActions.ICON_CLASS_NAME)));
        }
    }
}
