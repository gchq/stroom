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

import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public class IconCell extends AbstractCell<CellIcon> {

    private final boolean isButton;

    public IconCell() {
        this(true);
    }

    public IconCell(final boolean isButton) {
        super(isButton
                ? "click"
                : null);
        this.isButton = isButton;
    }

    @Override
    public void onBrowserEvent(final Context context,
                               final Element parent,
                               final CellIcon value,
                               final NativeEvent event,
                               final ValueUpdater<CellIcon> valueUpdater) {
        if (isButton) {
            super.onBrowserEvent(context, parent, value, event, valueUpdater);
            if ("click".equals(event.getType())) {
                final EventTarget eventTarget = event.getEventTarget();
                if (!Element.is(eventTarget)) {
                    return;
                }
                if (parent.getFirstChildElement().isOrHasChild(Element.as(eventTarget))) {
                    // Ignore clicks that occur outside of the main element.
                    onEnterKeyDown(context, parent, value, event, valueUpdater);
                }
            }
        }
    }

    @Override
    protected void onEnterKeyDown(final Context context,
                                  final Element parent,
                                  final CellIcon value,
                                  final NativeEvent event,
                                  final ValueUpdater<CellIcon> valueUpdater) {
        // A disabled icon is drawn as disabled, so it mustn't act either
        if (isButton && value != null && value.isEnabled()) {
            if (valueUpdater != null) {
                valueUpdater.update(value);
            }
        }
    }

    @Override
    public void render(final Context context, final CellIcon value, final SafeHtmlBuilder sb) {
        if (value == null) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            String className = "icon-cell";
            if (isButton) {
                className += " icon-cell--button";
            }

            if (!value.isEnabled()) {
                className += " " + "icon-cell--disabled";
            }

            sb.append(SvgImageUtil.toSafeHtml(
                    value.getName(),
                    value.getIcon(),
                    className));
        }
    }
}
