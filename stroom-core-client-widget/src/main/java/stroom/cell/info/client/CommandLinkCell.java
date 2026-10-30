/*
 * Copyright 2023 Crown Copyright
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
import stroom.widget.datagrid.client.EventCell;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.RowActions;
import stroom.widget.util.client.SvgImageUtil;
import stroom.widget.util.client.Templates;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.Command;
import com.google.gwt.view.client.CellPreviewEvent;

/// A cell showing a [CommandLink]'s text with, when it has a command, an 'open' button that runs
/// the command. The button is named by the link's tooltip (e.g. "Open Dictionary 'X'"), and is a
/// row action (see [RowActions]): the keyboard reaches it from its row and presses it with Enter
/// or Space.
public class CommandLinkCell extends AbstractCell<CommandLink> implements EventCell {

    private static final String ICON_CLASS_NAME = "icon-wrap";
    private static final String OPEN_CLASS_NAME = "command-link__open";

    public CommandLinkCell() {
        super(BrowserEvents.MOUSEDOWN, BrowserEvents.KEYDOWN);
    }

    @Override
    public void onBrowserEvent(final Context context, final Element parent, final CommandLink value,
                               final NativeEvent event, final ValueUpdater<CommandLink> valueUpdater) {
        final boolean keyPress = RowActions.isKeyPress(event);
        if (keyPress
            || (BrowserEvents.MOUSEDOWN.equals(event.getType()) && MouseUtil.isPrimary(event))) {
            final Element element = event.getEventTarget().cast();
            if (ElementUtil.hasClassName(element, OPEN_CLASS_NAME, 5)) {
                if (keyPress) {
                    // The button's own click would come too
                    event.preventDefault();
                }
                final Command command = NullSafe.get(value, CommandLink::getCommand);
                if (command != null) {
                    command.execute();
                }
            }
        }
    }

    @Override
    public boolean isConsumed(final CellPreviewEvent<?> event) {
        final NativeEvent nativeEvent = event.getNativeEvent();
        if (BrowserEvents.MOUSEDOWN.equals(nativeEvent.getType()) && MouseUtil.isPrimary(nativeEvent)) {
            final Element element = nativeEvent.getEventTarget().cast();
            return ElementUtil.hasClassName(element, OPEN_CLASS_NAME, 5);
        }
        return false;
    }

    @Override
    public void render(final Context context, final CommandLink value, final SafeHtmlBuilder sb) {
        if (value == null) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else if (value.hasCommand()) {
            final String text = value.getText();

            final SafeHtml textSafeHtml = Templates
                    .div("commandLinkText", SafeHtmlUtils.fromString(text));

            sb.appendHtmlConstant("<div class=\"command-link\">");
            sb.append(textSafeHtml);

            final SafeHtml open = SvgImageUtil.toSafeHtml(SvgImage.OPEN, ICON_CLASS_NAME,
                    "command-link__icon", RowActions.ICON_CLASS_NAME);
            sb.append(RowActions.button(OPEN_CLASS_NAME, value.getTooltip(), open));

            sb.appendHtmlConstant("</div>");

        } else {
            sb.appendEscaped(value.getText());
        }
    }
}
