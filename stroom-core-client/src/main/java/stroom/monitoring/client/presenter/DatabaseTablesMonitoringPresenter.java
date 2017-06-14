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

package stroom.monitoring.client.presenter;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.ColumnSortEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.OrderByColumn;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.BaseCriteria.OrderByDirection;
import stroom.entity.shared.OrderBy;
import stroom.node.shared.DBTableStatus;
import stroom.node.shared.FindSystemTableStatusAction;
import stroom.streamstore.client.presenter.ActionDataProvider;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.button.client.SvgIcons;
import stroom.widget.tab.client.presenter.Icon;

public class DatabaseTablesMonitoringPresenter extends ContentTabPresenter<DataGridView<DBTableStatus>> implements ColumnSortEvent.Handler {
    private final FindSystemTableStatusAction action;
    private final ActionDataProvider<DBTableStatus> dataProvider;

    @Inject
    public DatabaseTablesMonitoringPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher) {
        super(eventBus, new DataGridViewImpl<>(false, 1000));

        getView().addResizableColumn(new OrderByColumn<DBTableStatus, String>(new TextCell(), DBTableStatus.DATABASE) {
            @Override
            public String getValue(final DBTableStatus row) {
                return row.getDb();
            }
        }, DBTableStatus.DATABASE.getDisplayValue(), 200);
        getView().addResizableColumn(new OrderByColumn<DBTableStatus, String>(new TextCell(), DBTableStatus.TABLE) {
            @Override
            public String getValue(final DBTableStatus row) {
                return row.getTable();
            }
        }, DBTableStatus.TABLE.getDisplayValue(), 200);
        getView().addResizableColumn(new OrderByColumn<DBTableStatus, String>(new TextCell(), DBTableStatus.ROW_COUNT) {
            @Override
            public String getValue(final DBTableStatus row) {
                return ModelStringUtil.formatCsv(row.getCount());
            }
        }, DBTableStatus.ROW_COUNT.getDisplayValue(), 100);
        getView().addResizableColumn(new OrderByColumn<DBTableStatus, String>(new TextCell(), DBTableStatus.DATA_SIZE) {
            @Override
            public String getValue(final DBTableStatus row) {
                return ModelStringUtil.formatIECByteSizeString(row.getDataSize());
            }
        }, DBTableStatus.DATA_SIZE.getDisplayValue(), 100);
        getView().addResizableColumn(new OrderByColumn<DBTableStatus, String>(new TextCell(), DBTableStatus.INDEX_SIZE) {
            @Override
            public String getValue(final DBTableStatus row) {
                return ModelStringUtil.formatIECByteSizeString(row.getIndexSize());
            }
        }, DBTableStatus.INDEX_SIZE.getDisplayValue(), 100);
        getView().addEndColumn(new EndColumn<>());

        getView().addColumnSortHandler(this);

        action = new FindSystemTableStatusAction();
        dataProvider = new ActionDataProvider<>(dispatcher, action);
        dataProvider.addDataDisplay(getView().getDataDisplay());

        dataProvider.refresh();
    }

    @Override
    public void onColumnSort(final ColumnSortEvent event) {
        if (event.getColumn() instanceof OrderByColumn<?, ?>) {
            final OrderBy orderBy = ((OrderByColumn<?, ?>) event.getColumn()).getOrderBy();

            if (action != null) {
                action.setOrderBy(orderBy);
                if (event.isSortAscending()) {
                    action.setOrderByDirection(OrderByDirection.ASCENDING);
                } else {
                    action.setOrderByDirection(OrderByDirection.DESCENDING);
                }
                dataProvider.refresh();
            }
        }
    }

    @Override
    public Icon getIcon() {
        return SvgIcons.DATABASE;
    }

    @Override
    public String getLabel() {
        return "Database Tables";
    }
}
