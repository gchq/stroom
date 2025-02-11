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

import stroom.cell.tickbox.shared.TickBoxState;
import stroom.dashboard.client.main.SearchModel;
import stroom.dashboard.client.table.ColumnValuesFilterPresenter.ColumnValuesFilterView;
import stroom.dashboard.shared.ColumnValues;
import stroom.dashboard.shared.ColumnValuesRequest;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.DashboardResource;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.TableResultRequest;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.data.table.client.MyCellTable;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.expression.api.DateTimeSettings;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.ResultRequest.Fetch;
import stroom.query.api.v2.TableSettings;
import stroom.util.shared.PageRequest;
import stroom.widget.dropdowntree.client.view.QuickFilterDialogView;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.CheckListSelectionEventManager;
import stroom.widget.util.client.MySingleSelectionModel;
import stroom.widget.util.client.Rect;

import com.google.gwt.core.client.GWT;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class ColumnValuesFilterPresenter extends MyPresenterWidget<ColumnValuesFilterView>
        implements
        HasDataSelectionHandlers<ColumnValuesFilterPresenter>,
        ColumnValueSelectionModel,
        ColumnValuesFilterUiHandlers,
        QuickFilterUiHandlers {

    private final Set<String> selected = new HashSet<>();

    private static final DashboardResource DASHBOARD_RESOURCE = GWT.create(DashboardResource.class);

    private final PagerView pagerView;
    private final CellTable<String> cellTable;
    private final ColumnValueSelectionEventManager typeFilterSelectionEventManager;
    private final RestFactory restFactory;

    private SearchModel searchModel;
    private stroom.query.api.v2.Column column;
    private DashboardSearchRequest searchRequest;
    private boolean inverseSelection = true;
    private String nameFilter;
    private RestDataProvider<String, ColumnValues> dataProvider;

    @Inject
    public ColumnValuesFilterPresenter(final EventBus eventBus,
                                       final ColumnValuesFilterView view,
                                       final QuickFilterDialogView quickFilterPageView,
                                       final PagerView pagerView,
                                       final RestFactory restFactory) {
        super(eventBus, view);
        view.setUiHandlers(this);
        quickFilterPageView.setUiHandlers(this);
        this.restFactory = restFactory;
        this.pagerView = pagerView;

        cellTable = new MyCellTable<>(MyDataGrid.DEFAULT_LIST_PAGE_SIZE);
        cellTable.getElement().setClassName("menuCellTable");

        // Sink events.
        final int mouseMove = Event.getTypeInt(BrowserEvents.MOUSEMOVE);
        cellTable.sinkEvents(mouseMove);

//        cellTable.getElement().getStyle().setProperty("minWidth", 50 + "px");
//        cellTable.getElement().getStyle().setProperty("maxWidth", 600 + "px");

        cellTable.addColumn(getTickBoxColumn());
        cellTable.setSkipRowHoverCheck(true);

        MySingleSelectionModel<String> selectionModel = new MySingleSelectionModel<>();
        typeFilterSelectionEventManager = new ColumnValueSelectionEventManager(cellTable);
        cellTable.setSelectionModel(selectionModel, typeFilterSelectionEventManager);

        pagerView.setDataWidget(cellTable);
        quickFilterPageView.setDataView(pagerView);

        view.setList(quickFilterPageView);
    }

    public void show(final Element element,
                     final stroom.query.api.v2.Column column,
                     final SearchModel searchModel,
                     final TableSettings tableSettings,
                     final DateTimeSettings dateTimeSettings,
                     final String tableName) {
        this.searchModel = searchModel;
        this.column = column;

        final QueryKey queryKey = searchModel.getCurrentQueryKey();
        final Search currentSearch = searchModel.getCurrentSearch();
        final List<ComponentResultRequest> requests = new ArrayList<>();
        currentSearch.getComponentSettingsMap().entrySet()
                .stream()
                .filter(settings -> settings.getValue() instanceof TableComponentSettings)
                .forEach(componentSettings -> requests.add(TableResultRequest
                        .builder()
                        .componentId(componentSettings.getKey())
                        .requestedRange(OffsetRange.UNBOUNDED)
                        .tableName(tableName)
                        .tableSettings(tableSettings)
                        .fetch(Fetch.ALL)
                        .build()));

        final Search search = Search
                .builder()
                .dataSourceRef(currentSearch.getDataSourceRef())
                .expression(currentSearch.getExpression())
                .componentSettingsMap(currentSearch.getComponentSettingsMap())
                .params(currentSearch.getParams())
                .timeRange(currentSearch.getTimeRange())
                .incremental(true)
                .queryInfo(currentSearch.getQueryInfo())
                .build();

        searchRequest = DashboardSearchRequest
                .builder()
                .searchRequestSource(searchModel.getSearchRequestSource())
                .queryKey(queryKey)
                .search(search)
                .componentResultRequests(requests)
                .dateTimeSettings(dateTimeSettings)
                .build();

        clear();
        refresh();

//        this.filterStateConsumer = filterStateConsumer;
        Rect relativeRect = new Rect(element);
        relativeRect = relativeRect.grow(3);
        final PopupPosition popupPosition = new PopupPosition(relativeRect, PopupLocation.BELOW);

        //                    if (filterStateConsumer != null) {
        //                        filterStateConsumer.accept(hasActiveFilter());
        //                    }
        ShowPopupEvent.builder(this)
                .popupType(PopupType.POPUP)
                .popupPosition(popupPosition)
                .addAutoHidePartner(element)
//                .onShow(e -> {
//
//                }
//                .onHide(handler)
                .fire();
    }

    private void hideSelf() {
        HidePopupRequestEvent.builder(this)
                .fire();
    }

    @Override
    public void onSelectAll() {
        selected.clear();
        this.inverseSelection = true;
        refresh();
        updateTable();
    }

    @Override
    public void onClear() {
        selected.clear();
        inverseSelection = false;
        refresh();
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
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<ColumnValuesFilterPresenter> handler) {
        return getEventBus().addHandlerToSource(DataSelectionEvent.getType(), this, handler);
    }

    private void toggle(final String value) {
        if (value != null) {
            if (selected.contains(value)) {
                selected.remove(value);
            } else {
                selected.add(value);
            }

            refresh();
            updateTable();
        }
    }

    private void updateTable() {

    }

    @Override
    public TickBoxState getState(final String value) {
        if (inverseSelection) {
            return selected.contains(value)
                    ? TickBoxState.UNTICK
                    : TickBoxState.TICK;
        } else {
            return selected.contains(value)
                    ? TickBoxState.TICK
                    : TickBoxState.UNTICK;
        }
    }

    private Column<String, String> getTickBoxColumn() {
        return new Column<String, String>(new ColumnValueCell(this)) {
            @Override
            public String getValue(final String string) {
                return string;
            }
        };
    }

    @Override
    public void onFilterChange(final String text) {
        nameFilter = text;
        if (nameFilter != null) {
            nameFilter = nameFilter.trim();
            if (nameFilter.isEmpty()) {
                nameFilter = null;
            }
        }
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
                    final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
                    final ColumnValuesRequest columnValuesRequest = new ColumnValuesRequest(
                            searchRequest,
                            column,
                            nameFilter,
                            pageRequest);

                    restFactory
                            .create(DASHBOARD_RESOURCE)
                            .method(res -> res.getColumnValues(searchModel.getCurrentNode(),
                                    columnValuesRequest))
                            .onSuccess(dataConsumer)
                            .taskMonitorFactory(pagerView)
                            .exec();


//                    CacheNodeListPresenter.this.range = range;
//                    CacheNodeListPresenter.this.dataConsumer = dataConsumer;
//                    delayedUpdate.reset();
//                    nodeManager.listAllNodes(nodeNames ->
//                            fetchTasksForNodes(dataConsumer, errorHandler, nodeNames), errorHandler, getView());
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
