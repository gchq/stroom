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

package stroom.dashboard.client.table;

import stroom.dashboard.client.input.ColumnValueRowStyles;
import stroom.dashboard.client.table.ColumnValuesFilterPresenter.ColumnValuesFilterView;
import stroom.dashboard.shared.ColumnValue;
import stroom.dashboard.shared.ColumnValues;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.data.table.client.MyCellTable;
import stroom.dispatch.client.RestErrorHandler;
import stroom.preferences.client.UserPreferencesManager;
import stroom.query.api.ColumnValueSelection;
import stroom.query.api.ConditionalFormattingRule;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageResponse;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.CheckListSelectionEventManager;
import stroom.widget.util.client.MySingleSelectionModel;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.AbstractHasData;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.inject.Provider;

public class ColumnValuesFilterPresenter extends MyPresenterWidget<ColumnValuesFilterView>
        implements
        HasDataSelectionHandlers<ColumnValuesFilterPresenter>,
        ColumnValuesFilterUiHandlers,
        QuickFilterUiHandlers {

    private final PagerView pagerView;
    private final CellTable<ColumnValue> cellTable;
    private final ColumnValueSelectionEventManager typeFilterSelectionEventManager;
    private final ColumnValueRowStyles rowStyles;

    private Provider<Element> filterButtonProvider;
    private Provider<ColumnValuesDataSupplier> dataSupplierProvider;
    private Provider<Map<String, ColumnValueSelection>> selectionProvider;
    private final ColumnValueSelection.Builder selection = ColumnValueSelection.builder();
    private stroom.query.api.Column column;
    private RestDataProvider<ColumnValue, ColumnValues> dataProvider;
    private FilterCellManager filterCellManager;
    private String nameFilter;

    @Inject
    public ColumnValuesFilterPresenter(final EventBus eventBus,
                                       final ColumnValuesFilterView view,
                                       final PagerView pagerView,
                                       final UserPreferencesManager userPreferencesManager) {
        super(eventBus, view);
        view.setUiHandlers(this);
        this.pagerView = pagerView;

        rowStyles = new ColumnValueRowStyles(userPreferencesManager);
        cellTable = new MyCellTable<>(MyDataGrid.DEFAULT_LIST_PAGE_SIZE);
        cellTable.getElement().setClassName("menuCellTable");
        cellTable.setRowStyles(rowStyles);

        // Sink events.
        final int mouseMove = Event.getTypeInt(BrowserEvents.MOUSEMOVE);
        cellTable.sinkEvents(mouseMove);

        cellTable.addColumn(getTickBoxColumn());
        cellTable.setSkipRowHoverCheck(true);

        final MySingleSelectionModel<ColumnValue> selectionModel = new MySingleSelectionModel<>();
        typeFilterSelectionEventManager = new ColumnValueSelectionEventManager(cellTable);
        cellTable.setSelectionModel(selectionModel, typeFilterSelectionEventManager);

        pagerView.setDataWidget(cellTable);
        view.setDataView(pagerView);
    }

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

        if (currentSelection != null) {
            selection
                    .values(new HashSet<>(currentSelection.getValues()))
                    .invert(currentSelection.isInvert());
        } else {
            selection.clear().invert(true);
        }

        clear();
        refresh();
    }

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

        if (currentSelection != null) {
            selection
                    .values(new HashSet<>(currentSelection.getValues()))
                    .invert(currentSelection.isInvert());
        } else {
            selection.clear().invert(true);
        }

        clear();
        refresh();

        Rect relativeRect = new Rect(filterButtonProvider.get());
        relativeRect = relativeRect.grow(3);
        final PopupPosition popupPosition = new PopupPosition(relativeRect, PopupLocation.BELOW);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.POPUP)
                .popupPosition(popupPosition)
                .addAutoHidePartner(autoHidePartner)
                .onShow(e -> getView().focus())
                .onHide(handler)
                .fire();
    }

    public void hide() {
        HidePopupRequestEvent.builder(this).fire();
    }

    private void hideSelf() {
        HidePopupRequestEvent.builder(this)
                .fire();
    }

    public String getNameFilter() {
        return nameFilter;
    }

    public void setNameFilter(final String nameFilter) {
        this.nameFilter = nameFilter;
        getView().setText(nameFilter, false);
    }

    @Override
    public void onSelectAll() {
        if (NullSafe.isNonEmptyString(nameFilter)) {
            final ColumnValueSelection sel = selection.build();
            for (final ColumnValue value : cellTable.getVisibleItems()) {
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

    @Override
    public void onSelectNone() {
        if (NullSafe.isNonEmptyString(nameFilter)) {
            final ColumnValueSelection sel = selection.build();
            for (final ColumnValue value : cellTable.getVisibleItems()) {
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

    private void clear() {
        setData(Collections.emptyList());
    }

    private void setData(final List<ColumnValue> values) {
        cellTable.setRowData(0, values);
        cellTable.setRowCount(values.size());
    }

    public void focus() {
        typeFilterSelectionEventManager.selectFirstItem();
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(
            final DataSelectionHandler<ColumnValuesFilterPresenter> handler) {
        return getEventBus().addHandlerToSource(DataSelectionEvent.getType(), this, handler);
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
            cellTable.redraw();

            final Element filterButton = filterButtonProvider.get();
            if (filterButton != null) {
                if (columnValueSelection.isEnabled()) {
                    filterButton.addClassName("icon-colour__blue");
                } else {
                    filterButton.removeClassName("icon-colour__blue");
                }
            }
        }
    }

    private Column<ColumnValue, ColumnValue> getTickBoxColumn() {
        return new Column<ColumnValue, ColumnValue>(new ColumnValueCell(selection)) {
            @Override
            public ColumnValue getValue(final ColumnValue string) {
                return string;
            }
        };
    }

    @Override
    public void onFilterChange(final String text) {
        this.nameFilter = text;
        refresh();
    }

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
                        dataSupplier.setTaskMonitorFactory(pagerView);
                        dataSupplier.setNameFilter(nameFilter);
                        dataSupplier.exec(range, dataConsumer, errorHandler, selectionProvider.get());
                    } else {
                        dataConsumer.accept(new ColumnValues(Collections.emptyList(), PageResponse.empty()));
                    }
                }
            };
            dataProvider.addDataDisplay(cellTable);
        } else {
            dataProvider.refresh();
        }
    }

    public ColumnValueSelection getSelection() {
        return selection.build();
    }

    public interface ColumnValuesFilterView extends View, Focus, HasUiHandlers<ColumnValuesFilterUiHandlers> {

        void registerPopupTextProvider(Supplier<SafeHtml> popupTextSupplier);

        void setDataView(View view);

        void setText(String text, boolean fireEvents);
    }

    private class ColumnValueSelectionEventManager extends CheckListSelectionEventManager<ColumnValue> {

        public ColumnValueSelectionEventManager(final AbstractHasData<ColumnValue> cellTable) {
            super(cellTable);
        }

        @Override
        protected void onToggle(final ColumnValue item) {
            toggle(item);
        }

        @Override
        protected void onClose(final CellPreviewEvent<ColumnValue> e) {
            hideSelf();
        }

        @Override
        protected void onSelectAll(final CellPreviewEvent<ColumnValue> e) {
            ColumnValuesFilterPresenter.this.onSelectAll();
        }
    }
}
