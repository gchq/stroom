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

package stroom.cell.item.client;

import stroom.docref.HasDisplayValue;

import com.google.gwt.cell.client.AbstractInputCell;
import com.google.gwt.cell.client.Cell;
import com.google.gwt.cell.client.SelectionCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.SelectElement;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link Cell} used to render a drop-down list.
 */
public class ItemCell<V extends HasDisplayValue> extends AbstractInputCell<V, V> {

    private static Template template;
    private final List<V> options;
    private final Map<V, Integer> indexForOption = new HashMap<>();

    /**
     * Construct a new {@link SelectionCell} with the specified options.
     *
     * @param options the options in the cell
     */
    public ItemCell(final List<V> options) {
        super(BrowserEvents.CHANGE);
        if (template == null) {
            template = GWT.create(Template.class);
        }
        this.options = new ArrayList<>(options);
        int index = 0;
        for (final V option : options) {
            indexForOption.put(option, index++);
        }
    }

    @Override
    public void onBrowserEvent(final Context context, final Element parent, final V value, final NativeEvent event,
                               final ValueUpdater<V> valueUpdater) {
        super.onBrowserEvent(context, parent, value, event, valueUpdater);
        final String type = event.getType();
        if (BrowserEvents.CHANGE.equals(type)) {
            final Object key = context.getKey();
            final SelectElement select = parent.getFirstChild().cast();
            final V newValue = options.get(select.getSelectedIndex());
            setViewData(key, newValue);
            finishEditing(parent, newValue, key, valueUpdater);
            if (valueUpdater != null) {
                valueUpdater.update(newValue);
            }
        }
    }

    @Override
    public void render(final Context context, final V value, final SafeHtmlBuilder sb) {
        // Get the view data.
        final Object key = context.getKey();
        V viewData = getViewData(key);
        if (viewData != null && viewData.equals(value)) {
            clearViewData(key);
            viewData = null;
        }

        final int selectedIndex = getSelectedIndex(viewData == null
                ? value
                : viewData);
        sb.appendHtmlConstant("<select tabindex=\"-1\">");
        int index = 0;
        for (final V option : options) {
            if (index++ == selectedIndex) {
                sb.append(template.selected(option.getDisplayValue()));
            } else {
                sb.append(template.deselected(option.getDisplayValue()));
            }
        }
        sb.appendHtmlConstant("</select>");
    }

    private int getSelectedIndex(final V value) {
        final Integer index = indexForOption.get(value);
        if (index == null) {
            return -1;
        }
        return index.intValue();
    }

    interface Template extends SafeHtmlTemplates {

        @Template("<option value=\"{0}\">{0}</option>")
        SafeHtml deselected(String option);

        @Template("<option value=\"{0}\" selected=\"selected\">{0}</option>")
        SafeHtml selected(String option);
    }
}
