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

/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package stroom.dashboard.client.table;

import stroom.dashboard.client.table.FilterCell.ViewData;
import stroom.query.api.Column;
import stroom.query.api.ColumnFilter;
import stroom.security.client.presenter.ClassNameBuilder;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;

import com.google.gwt.cell.client.AbstractInputCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;


public class FilterCell
        extends AbstractInputCell<Column, ViewData> {

    /**
     * The {@code ViewData} for this cell.
     */
    public static class ViewData {

        /**
         * The last value that was updated.
         */
        private String lastValue;

        /**
         * The current value.
         */
        private String curValue;

        /**
         * Construct a ViewData instance containing a given value.
         *
         * @param value a String value
         */
        public ViewData(final String value) {
            this.lastValue = value;
            this.curValue = value;
        }

        /**
         * Return true if the last and current values of this ViewData object
         * are equal to those of the other object.
         */
        @Override
        public boolean equals(final Object other) {
            if (!(other instanceof ViewData)) {
                return false;
            }
            final ViewData vd = (ViewData) other;
            return equalsOrNull(lastValue, vd.lastValue) &&
                   equalsOrNull(curValue, vd.curValue);
        }

        /**
         * Return the current value of the input element.
         *
         * @return the current value String
         * @see #setCurrentValue(String)
         */
        public String getCurrentValue() {
            return curValue;
        }

        /**
         * Return the last value sent to the {@link ValueUpdater}.
         *
         * @return the last value String
         * @see #setLastValue(String)
         */
        public String getLastValue() {
            return lastValue;
        }

        /**
         * Return a hash code based on the last and current values.
         */
        @Override
        public int hashCode() {
            return (lastValue + "_*!@HASH_SEPARATOR@!*_" + curValue).hashCode();
        }

        /**
         * Set the current value.
         *
         * @param curValue the current value
         * @see #getCurrentValue()
         */
        protected void setCurrentValue(final String curValue) {
            this.curValue = curValue;
        }

        /**
         * Set the last value.
         *
         * @param lastValue the last value
         * @see #getLastValue()
         */
        protected void setLastValue(final String lastValue) {
            this.lastValue = lastValue;
        }

        private boolean equalsOrNull(final Object a, final Object b) {
            return (a != null)
                    ? a.equals(b)
                    : ((b == null)
                            ? true
                            : false);
        }
    }

    private final FilterCellManager filterCellManager;

    /**
     * Constructs a TextInputCell that renders its text without HTML markup.
     */
    public FilterCell(final FilterCellManager filterCellManager) {
        super(BrowserEvents.CHANGE, BrowserEvents.KEYUP);//, BrowserEvents.CLICK);
        this.filterCellManager = filterCellManager;
    }

    @Override
    public void onBrowserEvent(final Context context,
                               final Element parent,
                               final Column column,
                               final NativeEvent event,
                               final ValueUpdater<Column> valueUpdater) {
        super.onBrowserEvent(context, parent, column, event, valueUpdater);

        final String eventType = event.getType();
        final Element target = event.getEventTarget().cast();

        // Ignore events that don't target the input.
        final InputElement input = getInputElement(parent);
        if (!input.isOrHasChild(target)) {
            return;
        }

        final Object key = context.getKey();
        if (BrowserEvents.CHANGE.equals(eventType)) {
            finishEditing(parent, column, key, valueUpdater);
        } else if (BrowserEvents.KEYUP.equals(eventType)) {
            // Record keys as they are typed.
            ViewData vd = getViewData(key);
            if (vd == null) {
                vd = new ViewData(getValue(column));
                setViewData(key, vd);
            }
            vd.setCurrentValue(input.getValue());
        }
    }

    @Override
    public void render(final Context context, final Column column, final SafeHtmlBuilder sb) {
        // Get the view data.
        final Object key = context.getKey();
        ViewData viewData = getViewData(key);
        final String value = getValue(column);
        if (viewData != null && viewData.getCurrentValue().equals(value)) {
            clearViewData(key);
            viewData = null;
        }

        final String s = (viewData != null)
                ? viewData.getCurrentValue()
                : value;

        final ColumnFilter columnFilter = ColumnFilter.fromColumn(column).build();

        final boolean enabled = columnFilter.isEnabled();
        final String title = enabled
                ? "Disable Filter"
                : "Enable Filter";
        final ClassNameBuilder textClassName = new ClassNameBuilder();
        textClassName.addClassName("dashboard-table-filter-cell-text");
        if (!enabled) {
            textClassName.addClassName("dashboard-table-filter-cell-text-disabled");
        }

        final ClassNameBuilder buttonClassName = new ClassNameBuilder();
        buttonClassName.addClassName("inline-svg-button");
        buttonClassName.addClassName("icon-button");
        buttonClassName.addClassName("dashboard-table-filter-cell-disable-button");
        if (!enabled) {
            buttonClassName.addClassName("dashboard-table-filter-cell-disable-button-disabled");
        }

        final HtmlBuilder html = new HtmlBuilder(sb);
        html.div(outer -> {

            outer.elem(
                    SafeHtmlUtils.fromSafeConstant("input"),
                    new Attribute("type", "text"),
                    Attribute.className(textClassName.build()),
                    new Attribute("value", s));

            outer.div(buttons -> {
                buttons.elem(button -> {
                            button.div("", Attribute.className("background"));
                            button.div(div -> {
                                div.appendTrustedString(SvgImage.DISABLE.getSvg());
                            }, Attribute.className("svg-image svg-image__process face"));
                        }, SafeHtmlUtils.fromSafeConstant("button"),
                        new Attribute("type", "button"),
                        Attribute.className(buttonClassName.build()),
                        Attribute.title(title));
            }, Attribute.className("dashboard-table-filter-cell-buttons"));

        }, Attribute.className("dashboard-table-filter-cell"));
    }

    @Override
    protected void finishEditing(final Element parent,
                                 final Column column,
                                 final Object key,
                                 final ValueUpdater<Column> valueUpdater) {
        final String value = getValue(column);
        final String newValue = getInputElement(parent).getValue();

        // Get the view data.
        ViewData vd = getViewData(key);
        if (vd == null) {
            vd = new ViewData(value);
            setViewData(key, vd);
        }
        vd.setCurrentValue(newValue);

        // Fire the value updater if the value has changed.
        if (!vd.getCurrentValue().equals(vd.getLastValue())) {
            vd.setLastValue(newValue);

            if (filterCellManager != null) {
                final ColumnFilter.Builder builder = ColumnFilter.fromColumn(column);
                filterCellManager.setColumnFilter(column, builder.filter(newValue).build());
            }
            if (valueUpdater != null) {
                valueUpdater.update(column);
            }
        }
//
//        // Blur the element.
//        super.finishEditing(parent, newValue, key, valueUpdater);
    }

    @Override
    protected InputElement getInputElement(final Element parent) {
        final Element element = ElementUtil.findChild(parent, e -> e.getTagName().equalsIgnoreCase("input"));
        return element.cast();
    }

    private String getValue(final Column column) {
        return NullSafe.get(column.getColumnFilter(), ColumnFilter::getFilter);
    }
}
