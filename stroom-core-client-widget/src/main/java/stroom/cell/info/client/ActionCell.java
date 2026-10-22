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

package stroom.cell.info.client;

import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A cell containing an icon (ellipses icon by default) that when clicked
 * will execute an action. Useful for a simple
 *
 * @param <R>
 */
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
        super("click");
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
        super.onBrowserEvent(context, parent, row, event, valueUpdater);
        if ("click".equals(event.getType())) {
            final EventTarget eventTarget = event.getEventTarget();
            if (!Element.is(eventTarget)) {
                return;
            }
            if (parent.getFirstChildElement().isOrHasChild(Element.as(eventTarget))) {
                // Ignore clicks that occur outside of the main element.
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
            final String className = "svgCell-icon svgCell-button";
            final CellIcon icon = nameFunction == null
                    ? cellIcon
                    : cellIcon.withName(nameFunction.apply(row));
            sb.append(SvgImageUtil.toSafeHtml(icon, className));
        }
    }
}
