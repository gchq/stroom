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

package stroom.node.client.presenter;

import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.node.client.NodeGroupClient;
import stroom.node.shared.FindNodeGroupRequest;
import stroom.node.shared.FindNodeStatusCriteria;
import stroom.node.shared.NodeGroup;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;

public class NodeGroupListPresenter extends MyPresenterWidget<PagerView> {

    private final NodeGroupClient nodeGroupClient;
    private final MyDataGrid<NodeGroup> dataGrid;
    private final MultiSelectionModelImpl<NodeGroup> selectionModel;
    private final RestDataProvider<NodeGroup, ResultPage<NodeGroup>> dataProvider;

    @Inject
    public NodeGroupListPresenter(final EventBus eventBus,
                                  final PagerView view,
                                  final NodeGroupClient nodeGroupClient) {
        super(eventBus, view);
        this.nodeGroupClient = nodeGroupClient;

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);
        getWidget().getElement().addClassName("default-min-sizes");

        initTableColumns();

        final FindNodeGroupRequest request = new FindNodeGroupRequest(
                PageRequest.createDefault(),
                FindNodeGroupRequest.DEFAULT_SORT_LIST,
                null);
        dataProvider = new RestDataProvider<NodeGroup, ResultPage<NodeGroup>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<NodeGroup>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                CriteriaUtil.setRange(request, range);
                nodeGroupClient.find(request, dataConsumer, errorHandler, view);
            }
        };
        dataProvider.addDataDisplay(dataGrid);
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Name.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataGridUtil.toStringFunc(NodeGroup::getName))
                        .enabledWhen(NodeGroup::isEnabled)
                        .withSorting(FindNodeStatusCriteria.FIELD_ID_NAME)
                        .build(),
                DataGridUtil.headingBuilder("Name")
                        .withToolTip("The name of the node group.")
                        .build(),
                300);

        // Enabled
        dataGrid.addColumn(
                DataGridUtil.updatableTickBoxColumnBuilder(TickBoxState.createTickBoxFunc(
                                (NodeGroup result) -> NullSafe.get(
                                        result,
                                        NodeGroup::isEnabled)))
                        .enabledWhen(NodeGroup::isEnabled)
                        .withSorting(FindNodeStatusCriteria.FIELD_ID_ENABLED)
                        .withFieldUpdater((index, row, value) -> {
                            if (row != null) {
                                setEnabled(
                                        row,
                                        value.toBoolean(),
                                        result -> {
                                            internalRefresh();
//                                            NodeChangeEvent.fire(NodeGroupListPresenter.this, nodeName);
                                        },
                                        this);
                            }
                        })
                        .centerAligned()
                        .build(),
                DataGridUtil.headingBuilder("Enabled")
                        .withToolTip("Whether the node group is enabled for processing and background jobs.")
                        .centerAligned()
                        .build(),
                ColumnSizeConstants.ENABLED_COL);

        dataGrid.addEndColumn(new EndColumn<>());
    }

    private void internalRefresh() {
        dataProvider.refresh();
    }

    public void setEnabled(final NodeGroup nodeGroup,
                           final boolean enabled,
                           final Consumer<NodeGroup> resultConsumer,
                           final TaskMonitorFactory taskMonitorFactory) {
        nodeGroupClient.update(nodeGroup.copy().enabled(enabled).build(), resultConsumer, taskMonitorFactory);
    }

    public MultiSelectionModel<NodeGroup> getSelectionModel() {
        return selectionModel;
    }

    public void refresh() {
        dataProvider.refresh();
    }
}
