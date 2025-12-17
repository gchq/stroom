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

package stroom.dashboard.client.input;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.OrderByColumn;
import stroom.data.grid.client.PagerView;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.RestErrorHandler;
import stroom.query.api.ColumnRef;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ColumnSelectionPresenter
        extends MyPresenterWidget<PagerView>
        implements Refreshable, HasValueChangeHandlers<Set<ColumnRef>> {

    private final MyDataGrid<ColumnRef> dataGrid;
    private RestDataProvider<ColumnRef, ResultPage<ColumnRef>> dataProvider;
    private final Set<ColumnRef> allColumns = new HashSet<>();
    private final Set<ColumnRef> selectedColumns = new HashSet<>();
    private final HandlerManager handlerManager = new HandlerManager(this);

    @Inject
    public ColumnSelectionPresenter(final EventBus eventBus,
                                    final PagerView view) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>(this);
        view.setDataWidget(dataGrid);

        addColumns();
    }

    public void setSelectedColumns(final Set<ColumnRef> selectedColumns) {
        this.selectedColumns.clear();
        this.selectedColumns.addAll(NullSafe.set(selectedColumns));
    }

    public void setAllColumns(final Set<ColumnRef> allColumns) {
        this.allColumns.clear();
        this.allColumns.addAll(NullSafe.set(allColumns));
    }

    public Set<ColumnRef> getSelectedColumns() {
        return selectedColumns;
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(dataGrid.addColumnSortHandler(event -> refresh()));
    }


    private void addColumns() {
        addSelectedColumn();
        addNameColumn();
        dataGrid.addEndColumn(new EndColumn<>());
    }

    private void addSelectedColumn() {
        // Select Column
        final Column<ColumnRef, TickBoxState> column = new Column<ColumnRef, TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue(final ColumnRef indexShard) {
                final boolean match = selectedColumns.contains(indexShard);
                return TickBoxState.fromBoolean(match);
            }

        };
        final Header<TickBoxState> header = new Header<TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue() {
                if (allColumns.size() == selectedColumns.size()) {
                    return TickBoxState.TICK;
                } else if (!selectedColumns.isEmpty()) {
                    return TickBoxState.HALF_TICK;
                }
                return TickBoxState.UNTICK;
            }
        };
        dataGrid.addColumn(column, header, ColumnSizeConstants.CHECKBOX_COL);

        // Add Handlers
        header.setUpdater(value -> {
            if (value.equals(TickBoxState.UNTICK)) {
                selectedColumns.clear();
            } else if (value.equals(TickBoxState.TICK)) {
                selectedColumns.clear();
                selectedColumns.addAll(allColumns);
            }
            ValueChangeEvent.fire(this, selectedColumns);
            refresh();
        });
        column.setFieldUpdater((index, row, value) -> {
            if (value.toBoolean()) {
                selectedColumns.add(row);
            } else {
                selectedColumns.remove(row);
            }
            ValueChangeEvent.fire(this, selectedColumns);
            dataGrid.redrawHeaders();
        });
    }

    private void addNameColumn() {
        dataGrid.addResizableColumn(new OrderByColumn<ColumnRef, String>(
                new TextCell(), "Column Name", true) {
            @Override
            public String getValue(final ColumnRef indexShard) {
                return indexShard.getName();
            }
        }, "Column Name", 300);
    }

    @Override
    public void refresh() {
        if (dataProvider == null) {
            dataProvider = new RestDataProvider<ColumnRef, ResultPage<ColumnRef>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<ColumnRef>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    dataConsumer.accept(ResultPage.createUnboundedList(allColumns
                            .stream()
                            .sorted(Comparator.comparing(ColumnRef::getName))
                            .collect(Collectors.toList())));
                }
            };
            dataProvider.addDataDisplay(dataGrid);
        } else {
            dataProvider.refresh();
        }
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<Set<ColumnRef>> handler) {
        return handlerManager.addHandler(ValueChangeEvent.getType(), handler);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        super.fireEvent(event);
        handlerManager.fireEvent(event);
    }
}
