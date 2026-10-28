/*
 * Copyright 2025 Crown Copyright
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

package stroom.dashboard.client.component;

import stroom.dashboard.client.input.ColumnValueRowStyles;
import stroom.dashboard.client.table.ColumnValuesDataSupplier;
import stroom.dashboard.client.table.FilterCellManager;
import stroom.dashboard.shared.ColumnValue;
import stroom.dashboard.shared.ColumnValues;
import stroom.data.client.presenter.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.preferences.client.UserPreferencesManager;
import stroom.query.api.ColumnValueSelection;
import stroom.query.api.ConditionalFormattingRule;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageResponse;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.PopupPosition.PopupLocation;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Element;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/// Filters a table by a column's values ([ColumnValuesFilter]): ticking a value shows the rows that
/// have it, and the table's filter button turns blue while any are unticked. Kept by the table and
/// shown again as a popup from the column's heading, or a part of a dashboard's table filter.
public class ColumnValuesFilterController extends Controller {

    private static final String FILTERED = "icon-colour__blue";

    private final ColumnValuesFilter view;
    private final ColumnValueRowStyles rowStyles;
    private final ColumnValueSelection.Builder selection = ColumnValueSelection.builder();

    private Provider<Element> filterButtonProvider;
    private Provider<ColumnValuesDataSupplier> dataSupplierProvider;
    private Provider<Map<String, ColumnValueSelection>> selectionProvider;
    private stroom.query.api.Column column;
    private RestDataProvider<ColumnValue, ColumnValues> dataProvider;
    private FilterCellManager filterCellManager;
    private String nameFilter;

    /// Made by GIN, with its list.
    ///
    /// @param eventBus               The application's event bus.
    /// @param view                   The list.
    /// @param userPreferencesManager Gives the theme the conditional formatting's row styles use.
    @Inject
    public ColumnValuesFilterController(final EventBus eventBus,
                                        final ColumnValuesFilter view,
                                        final UserPreferencesManager userPreferencesManager) {
        super(eventBus);
        this.view = view;
        initWidget(view);

        rowStyles = new ColumnValueRowStyles(userPreferencesManager);
        view.setRowStyles(rowStyles);
        view.setSelection(selection);
        view.setOnToggle(this::toggle);
        view.setOnSelectAll(this::onSelectAll);
        view.setOnSelectNone(this::onSelectNone);
        view.setOnFilterChange(text -> {
            nameFilter = text;
            refresh();
        });
    }

    /// Sets up the filter as a part of a dashboard's table filter.
    ///
    /// @param filterButtonProvider Gives the table's filter button for the column.
    /// @param column               The column.
    /// @param dataSupplierProvider Gives what fetches the column's values.
    /// @param selectionProvider    Gives every column's selection, which the values fetched match.
    /// @param currentSelection     The values ticked, or null for all.
    /// @param filterCellManager    Tells the table of the values ticked.
    /// @param rules                The column's conditional formatting, which styles the values.
    public void init(final Provider<Element> filterButtonProvider,
                     final stroom.query.api.Column column,
                     final Provider<ColumnValuesDataSupplier> dataSupplierProvider,
                     final Provider<Map<String, ColumnValueSelection>> selectionProvider,
                     final ColumnValueSelection currentSelection,
                     final FilterCellManager filterCellManager,
                     final List<ConditionalFormattingRule> rules) {
        this.filterButtonProvider = filterButtonProvider;
        this.column = column;
        this.dataSupplierProvider = dataSupplierProvider;
        this.selectionProvider = selectionProvider;
        this.filterCellManager = filterCellManager;
        rowStyles.setConditionalFormattingRules(rules);
        setSelection(currentSelection);
        view.clear();
        refresh();
    }

    /// Shows the filter as a popup below a column's filter button, owned by what owns this.
    ///
    /// @param filterButtonProvider Gives the table's filter button for the column.
    /// @param autoHidePartner      What a press on doesn't close the popup, e.g. the heading.
    /// @param column               The column.
    /// @param dataSupplierProvider Gives what fetches the column's values.
    /// @param handler              Told when the popup is hidden.
    /// @param currentSelection     The values ticked, or null for all.
    /// @param filterCellManager    Tells the table of the values ticked.
    public void show(final Provider<Element> filterButtonProvider,
                     final Element autoHidePartner,
                     final stroom.query.api.Column column,
                     final Provider<ColumnValuesDataSupplier> dataSupplierProvider,
                     final HidePopupEvent.Handler handler,
                     final ColumnValueSelection currentSelection,
                     final FilterCellManager filterCellManager) {
        this.filterButtonProvider = filterButtonProvider;
        this.column = column;
        this.dataSupplierProvider = dataSupplierProvider;
        this.selectionProvider = () -> null;
        this.filterCellManager = filterCellManager;
        setSelection(currentSelection);
        view.clear();
        refresh();

        final Rect relativeRect = new Rect(filterButtonProvider.get()).grow(3);
        ControllerDialog.show(this)
                .popupType(PopupType.POPUP)
                .caption("Filter Values")
                .popupPosition(new PopupPosition(relativeRect, PopupLocation.BELOW))
                .addAutoHidePartner(autoHidePartner)
                .onShow(e -> view.focus())
                .onHide(handler)
                .fire();
    }

    /// Asks the popup to close.
    public void hide() {
        ControllerDialog.pressAction(this, DialogAction.OK);
    }

    /// @return The filter's text.
    public String getNameFilter() {
        return nameFilter;
    }

    /// @param nameFilter The filter's text, set without fetching the values again.
    public void setNameFilter(final String nameFilter) {
        this.nameFilter = nameFilter;
        view.setFilterText(nameFilter);
    }

    /// Puts the focus on the first value, if there is one.
    public void focus() {
        view.focusFirstValue();
    }

    /// @return The values ticked.
    public ColumnValueSelection getSelection() {
        return selection.build();
    }

    /// Fetches the values again.
    public void refresh() {
        if (dataProvider == null) {
            //noinspection Convert2Diamond
            dataProvider = new RestDataProvider<ColumnValue, ColumnValues>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ColumnValues> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    final ColumnValuesDataSupplier dataSupplier = dataSupplierProvider.get();
                    if (dataSupplier != null) {
                        dataSupplier.setTaskMonitorFactory(view.getTaskMonitor());
                        dataSupplier.setNameFilter(nameFilter);
                        dataSupplier.exec(range, dataConsumer, errorHandler, selectionProvider.get());
                    } else {
                        dataConsumer.accept(new ColumnValues(Collections.emptyList(), PageResponse.empty()));
                    }
                }
            };
            view.setDataProvider(dataProvider);
        } else {
            dataProvider.refresh();
        }
    }

    private void setSelection(final ColumnValueSelection currentSelection) {
        if (currentSelection != null) {
            selection
                    .values(new HashSet<>(currentSelection.getValues()))
                    .invert(currentSelection.isInvert());
        } else {
            selection.clear().invert(true);
        }
    }

    private void onSelectAll() {
        if (NullSafe.isNonEmptyString(nameFilter)) {
            final ColumnValueSelection sel = selection.build();
            for (final ColumnValue value : view.getVisibleItems()) {
                if (sel.isInvert()) {
                    selection.remove(value.getValue());
                } else {
                    selection.add(value.getValue());
                }
            }
        } else {
            selection.clear().invert(true);
        }
        updateTable();
    }

    private void onSelectNone() {
        if (NullSafe.isNonEmptyString(nameFilter)) {
            final ColumnValueSelection sel = selection.build();
            for (final ColumnValue value : view.getVisibleItems()) {
                if (sel.isInvert()) {
                    selection.add(value.getValue());
                } else {
                    selection.remove(value.getValue());
                }
            }
        } else {
            selection.clear().invert(false);
        }
        updateTable();
    }

    private void toggle(final ColumnValue value) {
        if (value != null) {
            selection.toggle(value.getValue());
            updateTable();
        }
    }

    private void updateTable() {
        if (filterCellManager != null) {
            final ColumnValueSelection columnValueSelection = selection.build();
            filterCellManager.setValueSelection(column, columnValueSelection);
            view.redraw();

            final Element filterButton = filterButtonProvider.get();
            if (filterButton != null) {
                if (columnValueSelection.isEnabled()) {
                    filterButton.addClassName(FILTERED);
                } else {
                    filterButton.removeClassName(FILTERED);
                }
            }
        }
    }
}
