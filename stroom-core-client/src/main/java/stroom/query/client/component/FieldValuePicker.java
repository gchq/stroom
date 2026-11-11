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

package stroom.query.client.component;

import stroom.cell.tickbox.client.TickBoxState;
import stroom.query.shared.FieldValueCount;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.DataGridSelectionEventManager;
import stroom.widget.quickfilter.client.QuickFilter;
import stroom.widget.spinner.client.SmallBusySpinner;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.text.client.Label;
import stroom.widget.util.client.CssProperties;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/// The value picker's content (BEM block `field-value-picker`, `field-value-picker.css`): a filter,
/// shown only when the values can be filtered, what the values were counted across, then a field's
/// most common values ([FieldValueCell]), each with the share of records that have it. In the list
/// the arrows move, and Space, Enter or a press picks the value with the focus; Enter in the filter
/// picks the first value. When several values can be picked, each has a tick showing whether the
/// term holds it. Its controller is [FieldValuePickerController].
public class FieldValuePicker extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final NumberFormat COUNT_FORMAT = NumberFormat.getFormat("#,##0");
    private static final NumberFormat PERCENT_FORMAT = NumberFormat.getFormat("0.0");
    private static final String RESIZED = "field-value-picker--resized";
    private static final String HEIGHT_PROPERTY = "--field-value-picker__height";
    // The size of the corner the browser drags to resize the picker
    private static final int RESIZE_GRIP_SIZE = 16;

    @UiField
    FlowPanel filterBar;
    @UiField
    QuickFilter filter;
    @UiField
    Label scope;
    @UiField
    SmallBusySpinner spinner;
    @UiField
    DataGrid<FieldValueCount> list;
    @UiField
    Label message;

    private Consumer<String> onFilterChange = text -> {
    };
    private Consumer<FieldValueCount> onPick = value -> {
    };
    private Predicate<FieldValueCount> pickable = value -> true;
    // Whether the term holds a value, or null when values aren't ticked
    private Predicate<FieldValueCount> ticked;
    private List<FieldValueCount> values = Collections.emptyList();
    private long totalCount;

    /// Makes an empty picker.
    public FieldValuePicker() {
        initWidget(BINDER.createAndBindUi(this));
        list.setPageSize(DataGrid.MASSIVE_LIST_PAGE_SIZE);

        final DataGridSelectionEventManager<FieldValueCount> selectionEventManager =
                new DataGridSelectionEventManager<>(list, new MultiSelectionModelImpl<>(), true);
        selectionEventManager.setChooser(this::pick);
        // Up from the first value goes back to the filter, and typing on a value types in it
        selectionEventManager.setMoveUpFromFirst(() -> {
            if (filterBar.isVisible()) {
                filter.forceFocus();
            }
        });
        selectionEventManager.setTypedHandler(text -> {
            if (filterBar.isVisible()) {
                filter.type(text);
            }
        });
        // Picking is the rows' own action, not a selection
        list.setSelectionModel(null, selectionEventManager);
        list.addColumn(new Column<FieldValueCount, FieldValueCount>(
                new FieldValueCell(this::getTickState, value -> pickable.test(value), this::getPercent)) {
            @Override
            public FieldValueCount getValue(final FieldValueCount value) {
                return value;
            }
        }, "", 100);
        // Named by the value alone, its share read after it
        list.setRowName(FieldValueCount::getValue);
        list.setRowDescription(this::describe);

        filter.setOnChange(text -> onFilterChange.accept(text));
        filter.setMoveDown(this::focusFirstValue);
        filter.addKeyDownHandler(event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                pickFirst();
            }
        });
        addDomHandler(this::onMouseDown, MouseDownEvent.getType());
    }

    /// @param shown Whether the filter is shown, e.g. only for fields holding text.
    public void setFilterShown(final boolean shown) {
        filterBar.setVisible(shown);
    }

    /// @param help Gives the help the filter shows.
    public void setFilterHelp(final Supplier<SafeHtml> help) {
        filter.registerPopupTextProvider(help);
    }

    /// @param onFilterChange Given the filter's text when it changes.
    public void setOnFilterChange(final Consumer<String> onFilterChange) {
        this.onFilterChange = Objects.requireNonNull(onFilterChange, "onFilterChange");
    }

    /// @param onPick Given the value the user picks.
    public void setOnPick(final Consumer<FieldValueCount> onPick) {
        this.onPick = Objects.requireNonNull(onPick, "onPick");
    }

    /// @param pickable Whether a value can be picked; one that can't is faded and says why.
    public void setPickable(final Predicate<FieldValueCount> pickable) {
        this.pickable = Objects.requireNonNull(pickable, "pickable");
    }

    /// Ticks the values the term holds, when several can be picked, and says so to screen readers.
    ///
    /// @param ticked Whether the term holds a value, or null when values aren't ticked.
    public void setTicked(final Predicate<FieldValueCount> ticked) {
        this.ticked = ticked;
        list.setRowChecked(ticked == null
                ? null
                : this::getTickState);
    }

    /// Shows values, in place of those or the message shown.
    ///
    /// @param values      The values, most common first.
    /// @param totalCount  How many records they were counted across.
    /// @param description What they were counted across, e.g. the time range's name.
    public void setValues(final List<FieldValueCount> values,
                          final long totalCount,
                          final String description) {
        this.values = values;
        this.totalCount = totalCount;
        final String scopeText = description + " - " + COUNT_FORMAT.format(totalCount) + " records";
        scope.setText(scopeText);
        // What they were counted across, e.g. the query's time conditions, may be too long to show
        scope.setTitle(scopeText);
        showMessage(null);
        list.setRowData(0, values);
        list.setRowCount(values.size());
    }

    /// Shows a message in place of the values, e.g. that there are none.
    ///
    /// @param message The message.
    public void setMessage(final String message) {
        clear();
        showMessage(message);
    }

    /// Shows no values and no message.
    public void clear() {
        values = Collections.emptyList();
        totalCount = 0;
        scope.setText("");
        scope.setTitle("");
        showMessage(null);
        list.setRowData(0, values);
        list.setRowCount(0);
    }

    /// Sets the filter's text, without the filter change callback being told.
    ///
    /// @param text The text.
    public void setFilterText(final String text) {
        filter.setText(text, false);
    }

    /// Draws the values again, e.g. once their ticks have changed.
    public void redraw() {
        list.redraw();
    }

    /// @return The task monitor that spins while values are fetched.
    public TaskMonitorFactory getTaskMonitor() {
        return spinner;
    }

    /// Puts the focus in the filter, or on the values if there is no filter.
    public void focus() {
        if (filterBar.isVisible()) {
            filter.forceFocus();
        } else {
            list.setFocus(true);
        }
    }

    // Shows a message in place of the list, or the list again for none
    private void showMessage(final String text) {
        message.setText(text == null
                ? ""
                : text);
        message.setVisible(text != null);
        list.setVisible(text == null);
    }

    private void focusFirstValue() {
        if (list.getVisibleItemCount() > 0) {
            list.setKeyboardSelectedRow(0, true);
        }
    }

    private void pick(final FieldValueCount value) {
        if (value != null && pickable.test(value)) {
            onPick.accept(value);
        }
    }

    private void pickFirst() {
        values.stream()
                .filter(pickable)
                .findFirst()
                .ifPresent(onPick);
    }

    private TickBoxState getTickState(final FieldValueCount value) {
        return ticked == null
                ? null
                : TickBoxState.fromBoolean(value != null && ticked.test(value));
    }

    private double getPercent(final FieldValueCount value) {
        return totalCount > 0
                ? Math.min(100, value.getCount() * 100D / totalCount)
                : 0;
    }

    // Read after a value's name and state: its share, how many records have it, and why it can't
    // be picked
    private String describe(final FieldValueCount value) {
        final String records = PERCENT_FORMAT.format(getPercent(value))
                               + "%, "
                               + COUNT_FORMAT.format(value.getCount())
                               + " of "
                               + COUNT_FORMAT.format(totalCount)
                               + " records";
        return pickable.test(value)
                ? records
                : records + ". Values holding a comma can't be used with 'in'";
    }

    // The picker is resized by dragging its bottom right corner. Its height is capped so that a long
    // list scrolls, but the browser won't resize it past the cap, so the cap is lifted, keeping the
    // height it has, once a resize starts.
    private void onMouseDown(final MouseDownEvent event) {
        final Element element = getElement();
        final boolean onResizeGrip = event.getRelativeX(element) >= element.getClientWidth() - RESIZE_GRIP_SIZE
                                     && event.getRelativeY(element) >= element.getClientHeight() - RESIZE_GRIP_SIZE;
        if (onResizeGrip && !getStyleName().contains(RESIZED)) {
            CssProperties.set(element, HEIGHT_PROPERTY, element.getOffsetHeight() + "px");
            addStyleName(RESIZED);
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FieldValuePicker> {

    }
}
