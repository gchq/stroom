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

package stroom.query.client.result.table;

import stroom.cell.textfield.client.TextFieldCell;
import stroom.query.api.Column;
import stroom.query.api.ColumnFilter;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ToolbarButtonCell;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.cell.client.CompositeCell;
import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.cell.client.HasCell;
import com.google.gwt.cell.client.TextInputCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/// A column's filter box in a results table's header (`filter-cell`,
/// filter-cell.css): Stroom's text field ([TextFieldCell]) for the filter, named for its
/// column, with a toolbar button ([ToolbarButtonCell]) over its right end that turns the filter off
/// and on. An off filter's box looks disabled but can still be changed.
///
/// What is typed sets the column's filter when the box loses the focus or Enter is pressed (the
/// field's change). The box keeps the focus after Enter, so the header drawn again keeps it in the
/// box (with its caret). Enter and Space on the button press it, and it keeps the focus.
public class FilterCell extends CompositeCell<Column> {

    private static final Template TEMPLATE = GWT.create(Template.class);
    // A column's header finds the button by it, to know that a press was the button's (and opens
    // no menu)
    private static final String DISABLE_BUTTON_CLASS = "filter-cell__disable-button";

    private final TextFieldCell field;
    private final HasCell<Column, ?> fieldPart;
    private final FilterCellManager filterCellManager;

    /// @param filterCellManager Sets a column's filter, when it is typed or turned off or on.
    public FilterCell(final FilterCellManager filterCellManager) {
        this(new TextFieldCell(null), filterCellManager);
    }

    private FilterCell(final TextFieldCell field, final FilterCellManager filterCellManager) {
        this(field, part(field, FilterCell::getFilter), filterCellManager);
    }

    private FilterCell(final TextFieldCell field,
                       final HasCell<Column, String> fieldPart,
                       final FilterCellManager filterCellManager) {
        super(List.of(fieldPart, part(new ToolbarButtonCell<Column>(
                SvgImage.DISABLE,
                DISABLE_BUTTON_CLASS,
                column -> isEnabled(column)
                        ? "Disable Filter"
                        : "Enable Filter",
                column -> toggle(filterCellManager, column)), Function.identity())));
        this.field = field;
        this.fieldPart = fieldPart;
        this.filterCellManager = filterCellManager;
    }

    @Override
    public void onBrowserEvent(final Context context,
                               final Element parent,
                               final Column column,
                               final NativeEvent event,
                               final ValueUpdater<Column> valueUpdater) {
        // The field's change (or Enter) sets the filter here rather than in the text field, which
        // would take the focus from the box: the header drawn again keeps the focus in it
        final InputElement input = getInputElement(parent);
        final EventTarget target = event.getEventTarget();
        if (input != null && Element.is(target) && input.isOrHasChild(Element.as(target))
            && isCommit(event)) {
            setFilter(context, column, input.getValue());
            return;
        }
        super.onBrowserEvent(context, parent, column, event, valueUpdater);
    }

    @Override
    public void render(final Context context, final Column column, final SafeHtmlBuilder sb) {
        final SafeHtmlBuilder parts = new SafeHtmlBuilder();
        super.render(context, column, parts);
        sb.append(isEnabled(column)
                ? TEMPLATE.filter(parts.toSafeHtml())
                : TEMPLATE.disabledFilter(parts.toSafeHtml()));
    }

    @Override
    protected <X> void render(final Context context,
                              final Column column,
                              final SafeHtmlBuilder sb,
                              final HasCell<Column, X> hasCell) {
        if (hasCell == fieldPart) {
            final SafeHtmlBuilder fieldHtml = new SafeHtmlBuilder();
            field.render(context, getFilter(column), "Filter " + NullSafe.string(column.getName()), fieldHtml);
            sb.append(TEMPLATE.field(fieldHtml.toSafeHtml()));
        } else {
            final SafeHtmlBuilder buttonHtml = new SafeHtmlBuilder();
            hasCell.getCell().render(context, hasCell.getValue(column), buttonHtml);
            sb.append(TEMPLATE.buttons(buttonHtml.toSafeHtml()));
        }
    }

    // The parts are in the filter's own element, the cell's first
    @Override
    protected Element getContainerElement(final Element parent) {
        return parent.getFirstChildElement();
    }

    // The field's element, the first part's first child
    private InputElement getInputElement(final Element parent) {
        final Element container = getContainerElement(parent);
        final Element fieldElement = container == null
                ? null
                : container.getFirstChildElement();
        final Element input = fieldElement == null
                ? null
                : fieldElement.getFirstChildElement();
        return input == null
                ? null
                : input.cast();
    }

    // Sets the column's filter to what was typed, if it isn't the filter already set from the box
    private void setFilter(final Context context, final Column column, final String text) {
        final Object key = context.getKey();
        final TextInputCell.ViewData viewData = field.getViewData(key);
        final String lastValue = viewData == null
                ? getFilter(column)
                : viewData.getLastValue();
        // What was typed is the box's value now, so its change (as it loses the focus) sets nothing
        field.setViewData(key, new TextInputCell.ViewData(text));
        if (!Objects.equals(text, lastValue) && filterCellManager != null) {
            filterCellManager.setColumnFilter(column, ColumnFilter.fromColumn(column).filter(text).build());
        }
    }

    // The field's change, or Enter pressed in it
    private static boolean isCommit(final NativeEvent event) {
        final String type = event.getType();
        return BrowserEvents.CHANGE.equals(type)
               || (BrowserEvents.KEYDOWN.equals(type) && event.getKeyCode() == KeyCodes.KEY_ENTER);
    }

    private static void toggle(final FilterCellManager filterCellManager, final Column column) {
        if (filterCellManager != null) {
            filterCellManager.setColumnFilter(column, ColumnFilter.fromColumn(column)
                    .enabled(!isEnabled(column))
                    .build());
        }
    }

    private static boolean isEnabled(final Column column) {
        return ColumnFilter.fromColumn(column).build().isEnabled();
    }

    private static String getFilter(final Column column) {
        return NullSafe.get(column.getColumnFilter(), ColumnFilter::getFilter);
    }

    // A part of the filter: a cell showing a value got from the column
    private static <X> HasCell<Column, X> part(final Cell<X> cell, final Function<Column, X> getter) {
        return new HasCell<>() {
            @Override
            public Cell<X> getCell() {
                return cell;
            }

            @Override
            public FieldUpdater<Column, X> getFieldUpdater() {
                return null;
            }

            @Override
            public X getValue(final Column column) {
                return getter.apply(column);
            }
        };
    }


    // --------------------------------------------------------------------------------


    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"filter-cell\">{0}</div>")
        SafeHtml filter(SafeHtml parts);

        @Template("<div class=\"filter-cell filter-cell--disabled\">{0}</div>")
        SafeHtml disabledFilter(SafeHtml parts);

        @Template("<div class=\"filter-cell__field\">{0}</div>")
        SafeHtml field(SafeHtml field);

        @Template("<div class=\"filter-cell__buttons\">{0}</div>")
        SafeHtml buttons(SafeHtml button);
    }
}
