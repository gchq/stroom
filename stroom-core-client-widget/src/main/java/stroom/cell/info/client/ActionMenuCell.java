/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.NullSafe;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * A cell containing an ellipses icon that when clicked
 * will show a popup menu of actions to perform.
 *
 * @param <R>
 */
public class ActionMenuCell<R> extends AbstractCell<R> {

    private static final Preset SVG_PRESET = SvgPresets.ELLIPSES_HORIZONTAL;
    private static final String ACTION_MENU_CELL_CLASS_NAME = "actionMenuCell";

    private final Function<R, String> tooltipExtractor;
    private final Function<R, List<Item>> menuCreator;
    private final HasHandlers hasHandlers;

    /**
     * @param menuCreator      Function to create the menu items to display
     * @param tooltipExtractor Function to supply the tooltip for the ellipses icon
     */
    public ActionMenuCell(final Function<R, List<Item>> menuCreator,
                          final Function<R, String> tooltipExtractor,
                          final HasHandlers hasHandlers) {
        super(BrowserEvents.MOUSEDOWN);

        this.menuCreator = menuCreator;
        this.tooltipExtractor = tooltipExtractor;
        this.hasHandlers = hasHandlers;
    }

    /**
     * @param menuCreator Function to create the menu items to display
     */
    public ActionMenuCell(final Function<R, List<Item>> menuCreator,
                          final HasHandlers hasHandlers) {
        this(menuCreator, null, hasHandlers);
    }

    @Override
    public void onBrowserEvent(final Context context,
                               final Element parent,
                               final R row,
                               final NativeEvent event,
                               final ValueUpdater<R> valueUpdater) {
        super.onBrowserEvent(context, parent, row, event, valueUpdater);
        if (BrowserEvents.MOUSEDOWN.equals(event.getType())) {
            final EventTarget eventTarget = event.getEventTarget();
            if (!Element.is(eventTarget)) {
                return;
            }
            if (parent.getFirstChildElement().isOrHasChild(Element.as(eventTarget))) {
                // Ignore clicks that occur outside the main element.
                onEnterKeyDown(context, parent, row, event, valueUpdater);
            }
        }
    }

    @Override
    protected void onEnterKeyDown(final Context context,
                                  final Element parent,
                                  final R row,
                                  final NativeEvent event,
                                  final ValueUpdater<R> valueUpdater) {
        final Element element = event.getEventTarget().cast();
        if (ElementUtil.hasClassName(element, ACTION_MENU_CELL_CLASS_NAME, 5)) {
            final List<Item> items = NullSafe.get(menuCreator, mc -> mc.apply(row));
            if (NullSafe.hasItems(items)) {
                showActionMenu(row, event);
            }
        }
        if (valueUpdater != null) {
            valueUpdater.update(row);
        }
    }

    private void showActionMenu(final R row, final NativeEvent event) {
        Objects.requireNonNull(menuCreator);
        final List<Item> items = menuCreator.apply(row);
        final PopupPosition popupPosition = new PopupPosition(
                event.getClientX() + 10,
                event.getClientY() + 10);
        ShowMenuEvent
                .builder()
                .items(items)
                .popupPosition(popupPosition)
//                    .onShow(e -> {
//                    })
//                    .onHide(e -> {
//                    })
                .allowCloseOnMoveLeft()
                .fire(hasHandlers);
    }

    @Override
    public void render(final Context context,
                       final R row,
                       final SafeHtmlBuilder sb) {
        if (row == null) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            final String tooltipText = NullSafe.getOrElse(
                    tooltipExtractor,
                    func -> func.apply(row),
                    "Actions...");

            final Preset preset = SVG_PRESET.title(tooltipText);
            sb.append(SvgImageUtil.toSafeHtml(
                    preset,
                    "svgCell-icon",
                    "svgCell-button",
                    ACTION_MENU_CELL_CLASS_NAME));
        }
    }
}
