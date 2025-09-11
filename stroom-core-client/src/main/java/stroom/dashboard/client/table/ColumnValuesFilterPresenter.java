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

package stroom.dashboard.client.table;

import stroom.dashboard.client.table.ColumnValuesFilterPresenter.ColumnValuesFilterView;
import stroom.dashboard.shared.ColumnValues;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.data.table.client.MyCellTable;
import stroom.dispatch.client.RestErrorHandler;
import stroom.query.api.ColumnValueSelection;
import stroom.util.shared.NullSafe;
import stroom.widget.dropdowntree.client.view.QuickFilterDialogView;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.CheckListSelectionEventManager;
import stroom.widget.util.client.MySingleSelectionModel;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.cellview.client.AbstractHasData;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.Event;
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
import java.util.function.Consumer;

public class ColumnValuesFilterPresenter extends MyPresenterWidget<ColumnValuesFilterView>
        implements
        HasDataSelectionHandlers<ColumnValuesFilterPresenter>,
        ColumnValuesFilterUiHandlers,
        QuickFilterUiHandlers {

    private final QuickFilterDialogView quickFilterPageView;
    private final PagerView pagerView;
    private final CellTable<String> cellTable;
    private final ColumnValueSelectionEventManager typeFilterSelectionEventManager;

    private Element filterButton;
    private ColumnValuesDataSupplier dataSupplier;
    private final ColumnValueSelection.Builder selection = ColumnValueSelection.builder();
    private RestDataProvider<String, ColumnValues> dataProvider;
    private FilterCellManager filterCellManager;
    private String nameFilter;

    @Inject
    public ColumnValuesFilterPresenter(final EventBus eventBus,
                                       final ColumnValuesFilterView view,
                                       final QuickFilterDialogView quickFilterPageView,
                                       final PagerView pagerView) {
        super(eventBus, view);
        view.setUiHandlers(this);
        quickFilterPageView.setUiHandlers(this);
        this.quickFilterPageView = quickFilterPageView;
        this.pagerView = pagerView;

        cellTable = new MyCellTable<>(MyDataGrid.DEFAULT_LIST_PAGE_SIZE);
        cellTable.getElement().setClassName("menuCellTable");

        // Sink events.
        final int mouseMove = Event.getTypeInt(BrowserEvents.MOUSEMOVE);
        cellTable.sinkEvents(mouseMove);

        cellTable.addColumn(getTickBoxColumn());
        cellTable.setSkipRowHoverCheck(true);

        final MySingleSelectionModel<String> selectionModel = new MySingleSelectionModel<>();
        typeFilterSelectionEventManager = new ColumnValueSelectionEventManager(cellTable);
        cellTable.setSelectionModel(selectionModel, typeFilterSelectionEventManager);

        pagerView.setDataWidget(cellTable);
        quickFilterPageView.setDataView(pagerView);

        view.setList(quickFilterPageView);
    }

    public void show(final Element filterButton,
                     final Element autoHidePartner,
                     final ColumnValuesDataSupplier dataSupplier,
                     final HidePopupEvent.Handler handler,
                     final ColumnValueSelection currentSelection,
                     final FilterCellManager filterCellManager) {
        this.filterButton = filterButton;
        this.dataSupplier = dataSupplier;
        dataSupplier.setNameFilter(nameFilter);
        this.filterCellManager = filterCellManager;
        dataSupplier.setTaskMonitorFactory(pagerView);

        if (currentSelection != null) {
            selection
                    .values(new HashSet<>(currentSelection.getValues()))
                    .invert(currentSelection.isInvert());
        } else {
            selection.clear().invert(true);
        }

        clear();
        refresh();

        Rect relativeRect = new Rect(filterButton);
        relativeRect = relativeRect.grow(3);
        final PopupPosition popupPosition = new PopupPosition(relativeRect, PopupLocation.BELOW);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.POPUP)
                .popupSize(PopupSize.resizable(400, 400))
                .popupPosition(popupPosition)
                .addAutoHidePartner(autoHidePartner)
                .onShow(e -> quickFilterPageView.focus())
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
        quickFilterPageView.setText(nameFilter, false);
    }

    @Override
    public void onSelectAll() {
        if (NullSafe.isNonEmptyString(nameFilter)) {
            final ColumnValueSelection sel = selection.build();
            for (final String value : cellTable.getVisibleItems()) {
                if (sel.isInvert()) {
                    selection.remove(value);
                } else {
                    selection.add(value);
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
            for (final String value : cellTable.getVisibleItems()) {
                if (sel.isInvert()) {
                    selection.add(value);
                } else {
                    selection.remove(value);
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

    private void setData(final List<String> values) {
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

    private void toggle(final String value) {
        if (value != null) {
            selection.toggle(value);
            updateTable();
        }
    }

    private void updateTable() {
        final ColumnValueSelection columnValueSelection = selection.build();
        filterCellManager.setValueSelection(dataSupplier.getColumn(), columnValueSelection);
        cellTable.redraw();

        if (columnValueSelection.isEnabled()) {
            filterButton.addClassName("icon-colour__blue");
        } else {
            filterButton.removeClassName("icon-colour__blue");
        }
    }

    private Column<String, String> getTickBoxColumn() {
        return new Column<String, String>(new ColumnValueCell(selection)) {
            @Override
            public String getValue(final String string) {
                return string;
            }
        };
    }

    @Override
    public void onFilterChange(final String text) {
        this.nameFilter = text;
        dataSupplier.setNameFilter(text);
        refresh();
    }

    private void refresh() {
        if (dataProvider == null) {
            //noinspection Convert2Diamond
            dataProvider = new RestDataProvider<String, ColumnValues>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ColumnValues> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    dataSupplier.exec(range, dataConsumer, errorHandler);
                }
            };
            dataProvider.addDataDisplay(cellTable);
        } else {
            dataProvider.refresh();
        }
    }

    public interface ColumnValuesFilterView extends View, HasUiHandlers<ColumnValuesFilterUiHandlers> {

        void setList(View view);
    }

    private class ColumnValueSelectionEventManager extends CheckListSelectionEventManager<String> {

        public ColumnValueSelectionEventManager(final AbstractHasData<String> cellTable) {
            super(cellTable);
        }

        @Override
        protected void onToggle(final String item) {
            toggle(item);
        }

        @Override
        protected void onClose(final CellPreviewEvent<String> e) {
            hideSelf();
        }

        @Override
        protected void onSelectAll(final CellPreviewEvent<String> e) {
            ColumnValuesFilterPresenter.this.onSelectAll();
        }
    }
}
