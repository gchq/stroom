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
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.ColumnSortEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.OrderByColumn;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.node.shared.DBTableStatus;
import stroom.node.shared.DbStatusResource;
import stroom.node.shared.FindDBTableCriteria;
import stroom.svg.client.Icon;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Sort.Direction;

import java.util.function.Consumer;

public class DatabaseTablesMonitoringPresenter extends ContentTabPresenter<DataGridView<DBTableStatus>> implements ColumnSortEvent.Handler {
    private static final DbStatusResource DB_STATUS_RESOURCE = GWT.create(DbStatusResource.class);

    private final FindDBTableCriteria criteria;
    private final RestDataProvider<DBTableStatus, ResultPage<DBTableStatus>> dataProvider;

    @Inject
    public DatabaseTablesMonitoringPresenter(final EventBus eventBus, final RestFactory restFactory) {
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
        dataProvider = new RestDataProvider<DBTableStatus, ResultPage<DBTableStatus>>(eventBus, criteria.obtainPageRequest()) {
            @Override
            protected void exec(final Consumer<ResultPage<DBTableStatus>> dataConsumer, final Consumer<Throwable> throwableConsumer) {
                final Rest<ResultPage<DBTableStatus>> rest = restFactory.create();
                rest.onSuccess(dataConsumer).onFailure(throwableConsumer).call(DB_STATUS_RESOURCE).findSystemTableStatus(criteria);
            }
        };
        dataProvider.addDataDisplay(getView().getDataDisplay());
//        dataProvider.refresh();
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
        return SvgPresets.DATABASE;
    }

    @Override
    public String getLabel() {
        return "Database Tables";
    }
}
