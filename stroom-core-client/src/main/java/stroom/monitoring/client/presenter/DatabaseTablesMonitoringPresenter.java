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
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.node.shared.FindSystemTableStatusAction;
import stroom.node.shared.SystemTableStatus;
import stroom.streamstore.client.presenter.ActionDataProvider;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.tab.client.presenter.Icon;

public class DatabaseTablesMonitoringPresenter extends ContentTabPresenter<DataGridView<SystemTableStatus>> {
    private final ActionDataProvider<SystemTableStatus> dataProvider;

    @Inject
    public DatabaseTablesMonitoringPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher) {
        super(eventBus, new DataGridViewImpl<SystemTableStatus>(false, 1000));

        getView().addResizableColumn(new Column<SystemTableStatus, String>(new TextCell()) {
            @Override
            public String getValue(final SystemTableStatus row) {
                return row.getTable();
            }
        }, "Table", 200);

        getView().addResizableColumn(new Column<SystemTableStatus, String>(new TextCell()) {
            @Override
            public String getValue(final SystemTableStatus row) {
                return ModelStringUtil.formatCsv(row.getCount());
            }
        }, "Count", 100);
        getView().addResizableColumn(new Column<SystemTableStatus, String>(new TextCell()) {
            @Override
            public String getValue(final SystemTableStatus row) {
                return ModelStringUtil.formatByteSizeString(row.getDataSize());
            }
        }, "Data Size", 100);
        getView().addResizableColumn(new Column<SystemTableStatus, String>(new TextCell()) {
            @Override
            public String getValue(final SystemTableStatus row) {
                return ModelStringUtil.formatByteSizeString(row.getIndexSize());
            }
        }, "Index Size", 100);
        getView().addEndColumn(new EndColumn<SystemTableStatus>());

        final FindSystemTableStatusAction action = new FindSystemTableStatusAction();

        dataProvider = new ActionDataProvider<SystemTableStatus>(dispatcher, action);
        dataProvider.addDataDisplay(getView().getDataDisplay());

        dataProvider.refresh();
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
