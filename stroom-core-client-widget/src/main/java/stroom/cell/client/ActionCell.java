/*
 * Copyright 2016 Crown Copyright
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

package stroom.cell.client;

import stroom.svg.shared.SvgImage;
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

import java.util.function.BiConsumer;
import java.util.function.Function;

/// A cell holding a button with an icon (an ellipsis by default) that does an action when pressed:
/// clicked, or Enter or Space while it has the focus (the grid's keyboard puts the focus on it). A
/// key press clicks it at its lower left corner (see [RowActions#clickBelow(Element)]), so an action
/// that places a menu by the pointer places it below the button.
///
/// @param <R> The row's type.
public class ActionCell<R> extends AbstractCell<R> {

    private final CellIcon cellIcon;
    private final BiConsumer<R, NativeEvent> action;
    private final Function<R, String> nameFunction;

    public ActionCell(final BiConsumer<R, NativeEvent> action) {
        this(CellIcon.of(SvgImage.ELLIPSES_HORIZONTAL, "Actions..."), action);
    }

    /// An "Actions..." button named for its row, so that each row's is told apart, e.g.
    /// "Actions for job 'Data Retention' on node 'node1'" (as the menu it opens is named).
    ///
    /// @param action       What a press does, given the row and the event.
    /// @param nameFunction The button's name (and tooltip) for a row.
    public ActionCell(final BiConsumer<R, NativeEvent> action,
                      final Function<R, String> nameFunction) {
        this(CellIcon.of(SvgImage.ELLIPSES_HORIZONTAL, "Actions..."), action, nameFunction);
    }

    public ActionCell(final CellIcon cellIcon,
                      final BiConsumer<R, NativeEvent> action) {
        this(cellIcon, action, null);
    }

    private ActionCell(final CellIcon cellIcon,
                       final BiConsumer<R, NativeEvent> action,
                       final Function<R, String> nameFunction) {
        super(BrowserEvents.CLICK, BrowserEvents.KEYDOWN);
        this.action = action;
        this.cellIcon = cellIcon;
        this.nameFunction = nameFunction;
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
        if (RowActions.isKeyPress(event)) {
            // Enter or Space: a click placed by the button. The browser's own click from the key
            // is prevented (and would be ignored, having no position)
            event.preventDefault();
            RowActions.clickBelow(button);
        } else if (BrowserEvents.CLICK.equals(event.getType()) && !RowActions.isKeyboardClick(event)) {
            onEnterKeyDown(context, parent, row, event, valueUpdater);
        }
    }

    @Override
    protected void onEnterKeyDown(final Context context,
                                  final Element parent,
                                  final R row,
                                  final NativeEvent event,
                                  final ValueUpdater<R> valueUpdater) {
        action.accept(row, event);

        if (valueUpdater != null) {
            valueUpdater.update(row);
        }
    }

    @Override
    public void render(final Context context,
                       final R row,
                       final SafeHtmlBuilder sb) {
        if (row == null) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            final String name = nameFunction == null
                    ? cellIcon.getName()
                    : nameFunction.apply(row);
            // A button the grid's keyboard reaches (a row action), named by its title
            sb.append(RowActions.button(
                    "icon-cell--button",
                    name,
                    SvgImageUtil.toSafeHtml(cellIcon.getIcon(), "icon-cell", RowActions.ICON_CLASS_NAME)));
        }
    }
}
