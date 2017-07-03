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
import stroom.entity.shared.Sort.Direction;
import stroom.entity.shared.Sort.Direction;
import stroom.node.shared.DBTableStatus;
import stroom.node.shared.FindDBTableCriteria;
import stroom.node.shared.FindSystemTableStatusAction;
import stroom.streamstore.client.presenter.ActionDataProvider;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.tab.client.presenter.Icon;

public class DatabaseTablesMonitoringPresenter extends ContentTabPresenter<DataGridView<DBTableStatus>> implements ColumnSortEvent.Handler {
    private final FindDBTableCriteria criteria;
    private final ActionDataProvider<DBTableStatus> dataProvider;

    @Inject
    public DatabaseTablesMonitoringPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher) {
        super(eventBus, new DataGridViewImpl<>(false, 1000));

        getView().addResizableColumn(new OrderByColumn<DBTableStatus, String>(new TextCell(), DBTableStatus.FIELD_DATABASE, true) {
            @Override
            public String getValue(final DBTableStatus row) {
                return row.getDb();
            }
        }, DBTableStatus.FIELD_DATABASE, 200);

        getView().addResizableColumn(new OrderByColumn<DBTableStatus, String>(new TextCell(), DBTableStatus.FIELD_TABLE, true) {
            @Override
            public String getValue(final DBTableStatus row) {
                return row.getTable();
            }
        }, DBTableStatus.FIELD_TABLE, 200);

        getView().addResizableColumn(new OrderByColumn<DBTableStatus, String>(new TextCell(), DBTableStatus.FIELD_ROW_COUNT, false) {
            @Override
            public String getValue(final DBTableStatus row) {
                return ModelStringUtil.formatCsv(row.getCount());
            }
        }, DBTableStatus.FIELD_ROW_COUNT, 100);

        getView().addResizableColumn(new OrderByColumn<DBTableStatus, String>(new TextCell(), DBTableStatus.FIELD_DATA_SIZE, false) {
            @Override
            public String getValue(final DBTableStatus row) {
                return ModelStringUtil.formatIECByteSizeString(row.getDataSize());
            }
        }, DBTableStatus.FIELD_DATA_SIZE, 100);

        getView().addResizableColumn(new OrderByColumn<DBTableStatus, String>(new TextCell(), DBTableStatus.FIELD_INDEX_SIZE, false) {
            @Override
            public String getValue(final DBTableStatus row) {
                return ModelStringUtil.formatIECByteSizeString(row.getIndexSize());
            }
        }, DBTableStatus.FIELD_INDEX_SIZE, 100);

        getView().addEndColumn(new EndColumn<>());

        getView().addColumnSortHandler(this);

        criteria = new FindDBTableCriteria();
        final FindSystemTableStatusAction action = new FindSystemTableStatusAction(criteria);
        dataProvider = new ActionDataProvider<>(dispatcher, action);
        dataProvider.addDataDisplay(getView().getDataDisplay());

        dataProvider.refresh();
    }

    @Override
    public void onColumnSort(final ColumnSortEvent event) {
        if (event.getColumn() instanceof OrderByColumn<?, ?>) {
            final OrderByColumn<?, ?> orderByColumn = (OrderByColumn<?, ?>) event.getColumn();
            if (criteria != null) {
                if (event.isSortAscending()) {
                    criteria.setSort(orderByColumn.getField(), Direction.ASCENDING, orderByColumn.isIgnoreCase());
                } else {
                    criteria.setSort(orderByColumn.getField(), Direction.DESCENDING, orderByColumn.isIgnoreCase());
                }
                dataProvider.refresh();
            }
        }
    }

    @Override
    public Icon getIcon() {
        return GlyphIcons.DATABASE;
    }

    @Override
    public String getLabel() {
        return "Database Tables";
    }
}
