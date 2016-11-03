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

package stroom.pool.client.presenter;

import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.cell.client.NumberCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.presenter.HasRead;
import stroom.pool.shared.FetchPoolNodeRowAction;
import stroom.pool.shared.PoolClearAction;
import stroom.pool.shared.PoolNodeRow;
import stroom.pool.shared.PoolRow;
import stroom.streamstore.client.presenter.ActionDataProvider;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;

public class PoolNodeListPresenter extends MyPresenterWidget<DataGridView<PoolNodeRow>>implements HasRead<PoolRow> {
    private final FetchPoolNodeRowAction action = new FetchPoolNodeRowAction();
    private final ClientDispatchAsync dispatcher;
    private ActionDataProvider<PoolNodeRow> dataProvider;

    @Inject
    public PoolNodeListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher,
            final TooltipPresenter tooltipPresenter) {
        super(eventBus, new DataGridViewImpl<PoolNodeRow>(false));
        this.dispatcher = dispatcher;

        // Node
        final Column<PoolNodeRow, String> nodeColumn = new Column<PoolNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final PoolNodeRow row) {
                return row.getNode().getName();
            }
        };
        getView().addResizableColumn(nodeColumn, "Node", 200);

        // Last Access.
        final Column<PoolNodeRow, String> lastAccessColumn = new Column<PoolNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final PoolNodeRow row) {
                return ClientDateUtil.createDateTimeString(row.getPoolInfo().getLastAccessTime());
            }
        };
        getView().addResizableColumn(lastAccessColumn, "Last Access", 200);

        // Max Objects Per Key.
        final Column<PoolNodeRow, String> maxObjectsPerKeyColumn = new Column<PoolNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final PoolNodeRow row) {
                return row.getPoolInfo().getMaxObjectsPerKey();
            }
        };
        getView().addResizableColumn(maxObjectsPerKeyColumn, "Max Objects Per Key", 150);

        // In Use.
        final Column<PoolNodeRow, Number> inUseColumn = new Column<PoolNodeRow, Number>(new NumberCell()) {
            @Override
            public Number getValue(final PoolNodeRow row) {
                return row.getPoolInfo().getInUse();
            }
        };
        getView().addResizableColumn(inUseColumn, "In Use", 60);

        // In Pool.
        final Column<PoolNodeRow, Number> inPoolColumn = new Column<PoolNodeRow, Number>(new NumberCell()) {
            @Override
            public Number getValue(final PoolNodeRow row) {
                return row.getPoolInfo().getInPool();
            }
        };
        getView().addResizableColumn(inPoolColumn, "In Pool", 60);

        // Idle Time.
        final Column<PoolNodeRow, String> idleTimeColumn = new Column<PoolNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final PoolNodeRow row) {
                return ModelStringUtil.formatDurationString(row.getPoolInfo().getTimeToIdleMs());
            }
        };
        getView().addResizableColumn(idleTimeColumn, "Idle Time", 80);

        // Live Time.
        final Column<PoolNodeRow, String> liveTimeColumn = new Column<PoolNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final PoolNodeRow row) {
                return ModelStringUtil.formatDurationString(row.getPoolInfo().getTimeToLiveMs());
            }
        };
        getView().addResizableColumn(liveTimeColumn, "Live Time", 80);

        // Clear.
        final Column<PoolNodeRow, String> clearColumn = new Column<PoolNodeRow, String>(new ButtonCell()) {
            @Override
            public String getValue(final PoolNodeRow row) {
                return "Clear";
            }
        };
        clearColumn.setFieldUpdater(new FieldUpdater<PoolNodeRow, String>() {
            @Override
            public void update(final int index, final PoolNodeRow row, final String value) {
                dispatcher.execute(new PoolClearAction(row.getPoolInfo().getName(), row.getNode()), null);
            }
        });
        getView().addColumn(clearColumn, "</br>");

        getView().addEndColumn(new EndColumn<PoolNodeRow>());
    }

    @Override
    public void read(final PoolRow entity) {
        if (entity != null) {
            action.setPoolName(entity.getPoolName());

            if (dataProvider == null) {
                dataProvider = new ActionDataProvider<PoolNodeRow>(dispatcher, action);
                dataProvider.addDataDisplay(getView());
            }

            dataProvider.refresh();
        }
    }
}
