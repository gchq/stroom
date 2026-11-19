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
import stroom.widget.util.client.SvgMarkup;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.function.Consumer;
import java.util.function.Function;

/// A toolbar's icon button ([ToolbarButton]'s look, `toolbar-button`, toolbar-button.css) in a
/// grid's cell or header, e.g. a column's filter box's disable button.
///
/// It is a real button and a tab stop, so Enter and Space press it as they press any button (the
/// browser's click), as does the mouse; a press does the cell's action with its value. Its name is
/// its title, which may differ by value (e.g. "Disable Filter" or "Enable Filter").
///
/// @param <T> The cell's value.
public class ToolbarButtonCell<T> extends AbstractCell<T> {

    private static final Template TEMPLATE = GWT.create(Template.class);

    private final SafeHtml icon;
    private final String className;
    private final Function<T, String> title;
    private final Consumer<T> action;

    /// @param icon      The button's icon.
    /// @param className The button's own class, added to `toolbar-button`, e.g. the class its
    ///                  container finds it by.
    /// @param title     The button's name (and tooltip) for a value.
    /// @param action    What a press does, given the cell's value.
    public ToolbarButtonCell(final SvgImage icon,
                             final String className,
                             final Function<T, String> title,
                             final Consumer<T> action) {
        super(BrowserEvents.CLICK);
        this.icon = SafeHtmlUtils.fromTrustedString(SvgMarkup.decorative(icon.getSvg(), "toolbar-button__icon"));
        this.className = className;
        this.title = title;
        this.action = action;
    }

    @Override
    public void onBrowserEvent(final Context context,
                               final Element parent,
                               final T value,
                               final NativeEvent event,
                               final ValueUpdater<T> valueUpdater) {
        final EventTarget target = event.getEventTarget();
        final Element button = parent.getFirstChildElement();
        if (BrowserEvents.CLICK.equals(event.getType())
            && button != null
            && Element.is(target)
            && button.isOrHasChild(Element.as(target))) {
            action.accept(value);
        }
    }

    @Override
    public void render(final Context context, final T value, final SafeHtmlBuilder sb) {
        sb.append(TEMPLATE.button(className, title.apply(value), icon));
    }


    // --------------------------------------------------------------------------------


    interface Template extends SafeHtmlTemplates {

        @Template("<button type=\"button\" class=\"toolbar-button {0}\" title=\"{1}\">{2}</button>")
        SafeHtml button(String className, String title, SafeHtml icon);
    }
}
