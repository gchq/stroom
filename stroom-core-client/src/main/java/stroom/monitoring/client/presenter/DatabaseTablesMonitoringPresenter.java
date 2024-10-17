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

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.OrderByColumn;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.node.shared.DBTableStatus;
import stroom.node.shared.DbStatusResource;
import stroom.node.shared.FindDBTableCriteria;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResultPage;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

public class DatabaseTablesMonitoringPresenter
        extends ContentTabPresenter<PagerView> {

    public static final String TAB_TYPE = "DatabaseTables";

    private static final DbStatusResource DB_STATUS_RESOURCE = GWT.create(DbStatusResource.class);

    private final FindDBTableCriteria criteria;
    private final RestDataProvider<DBTableStatus, ResultPage<DBTableStatus>> dataProvider;

    @Inject
    public DatabaseTablesMonitoringPresenter(final EventBus eventBus,
                                             final PagerView view,
                                             final RestFactory restFactory) {
        super(eventBus, view);

        final MyDataGrid<DBTableStatus> dataGrid = new MyDataGrid<>(1000);
        view.setDataWidget(dataGrid);

        dataGrid.addResizableColumn(new OrderByColumn<DBTableStatus, String>(
                new TextCell(), DBTableStatus.FIELD_DATABASE, true) {
            @Override
            public String getValue(final DBTableStatus row) {
                return row.getDb();
            }
        }, DBTableStatus.FIELD_DATABASE, 200);

        dataGrid.addResizableColumn(new OrderByColumn<DBTableStatus, String>(
                new TextCell(), DBTableStatus.FIELD_TABLE, true) {
            @Override
            public String getValue(final DBTableStatus row) {
                return row.getTable();
            }
        }, DBTableStatus.FIELD_TABLE, 200);

        dataGrid.addResizableColumn(new OrderByColumn<DBTableStatus, String>(
                new TextCell(), DBTableStatus.FIELD_ROW_COUNT, false) {
            @Override
            public String getValue(final DBTableStatus row) {
                return ModelStringUtil.formatCsv(row.getCount());
            }
        }, DBTableStatus.FIELD_ROW_COUNT, 100);

        dataGrid.addResizableColumn(new OrderByColumn<DBTableStatus, String>(
                new TextCell(), DBTableStatus.FIELD_DATA_SIZE, false) {
            @Override
            public String getValue(final DBTableStatus row) {
                return ModelStringUtil.formatIECByteSizeString(row.getDataSize());
            }
        }, DBTableStatus.FIELD_DATA_SIZE, 100);

        dataGrid.addResizableColumn(new OrderByColumn<DBTableStatus, String>(
                new TextCell(), DBTableStatus.FIELD_INDEX_SIZE, false) {
            @Override
            public String getValue(final DBTableStatus row) {
                return ModelStringUtil.formatIECByteSizeString(row.getIndexSize());
            }
        }, DBTableStatus.FIELD_INDEX_SIZE, 100);

        dataGrid.addEndColumn(new EndColumn<>());

        criteria = new FindDBTableCriteria();
        dataProvider = new RestDataProvider<DBTableStatus, ResultPage<DBTableStatus>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<DBTableStatus>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                CriteriaUtil.setRange(criteria, range);
                restFactory
                        .create(DB_STATUS_RESOURCE)
                        .method(res -> res.findSystemTableStatus(criteria))
                        .onSuccess(dataConsumer)
                        .onFailure(errorHandler)
                        .taskMonitorFactory(view)
                        .exec();
            }
        };
        dataProvider.addDataDisplay(dataGrid);
//        dataProvider.refresh();


        dataGrid.addColumnSortHandler(event -> {
            if (event.getColumn() instanceof OrderByColumn<?, ?>) {
                final OrderByColumn<?, ?> orderByColumn = (OrderByColumn<?, ?>) event.getColumn();
                criteria.setSort(orderByColumn.getField(), !event.isSortAscending(), orderByColumn.isIgnoreCase());
                dataProvider.refresh();
            }
        });
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.DATABASE;
    }

    @Override
    public IconColour getIconColour() {
        return IconColour.GREY;
    }

    @Override
    public String getLabel() {
        return "Database Tables";
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
