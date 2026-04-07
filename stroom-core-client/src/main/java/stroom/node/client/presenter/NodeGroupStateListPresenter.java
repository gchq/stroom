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
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.node.client.NodeGroupClient;
import stroom.node.shared.FindNodeCriteria;
import stroom.node.shared.FindNodeStatusCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.NodeGroup;
import stroom.node.shared.NodeGroupChange;
import stroom.node.shared.NodeGroupState;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;

public class NodeGroupStateListPresenter extends MyPresenterWidget<PagerView> {

    private final MyDataGrid<NodeGroupState> dataGrid;
    private final NodeGroupClient nodeGroupClient;
    private final RestDataProvider<NodeGroupState, ResultPage<NodeGroupState>> dataProvider;
    private final FindNodeCriteria findNodeStatusCriteria = new FindNodeCriteria();

    private NodeGroup nodeGroup;

    @Inject
    public NodeGroupStateListPresenter(final EventBus eventBus,
                                       final PagerView view,
                                       final NodeGroupClient nodeGroupClient) {
        super(eventBus, view);
        dataGrid = new MyDataGrid<>(this);
        view.setDataWidget(dataGrid);

        this.nodeGroupClient = nodeGroupClient;

        initTableColumns();
        dataProvider = new RestDataProvider<NodeGroupState, ResultPage<NodeGroupState>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<NodeGroupState>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                if (nodeGroup != null) {
                    CriteriaUtil.setSortList(findNodeStatusCriteria, dataGrid.getColumnSortList());
                    nodeGroupClient.getNodeGroupState(nodeGroup.getId(),
                            dataConsumer,
                            NodeGroupStateListPresenter.this);
                } else {
                    dataConsumer.accept(ResultPage.empty());
                }
            }
        };
        dataProvider.addDataDisplay(dataGrid);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(dataGrid.addColumnSortHandler(event -> internalRefresh()));
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Include in group
        dataGrid.addColumn(
                DataGridUtil.updatableTickBoxColumnBuilder(TickBoxState.createTickBoxFunc(
                                (NodeGroupState result) -> NullSafe.get(
                                        result,
                                        NodeGroupState::isIncluded)))
                        .withFieldUpdater((index, row, value) -> {
                            if (row != null) {
                                final NodeGroupChange change = new NodeGroupChange(
                                        row.getNode().getId(),
                                        nodeGroup.getId(),
                                        !row.isIncluded());
                                nodeGroupClient.updateNodeGroupState(change, ngs ->
                                        internalRefresh(), this);
                            }
                        })
                        .build(),
                DataGridUtil.headingBuilder("In Group")
                        .withToolTip("Whether the node is included in the node group.")
                        .build(),
                100);

        // Name.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataGridUtil.toStringFunc(NodeGroupState::getNode, Node::getName))
                        .withSorting(FindNodeStatusCriteria.FIELD_ID_NAME)
                        .build(),
                DataGridUtil.headingBuilder("Name")
                        .withToolTip("The name of the node as defined in configuration.")
                        .build(),
                300);

        // Host Name.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((NodeGroupState result) -> NullSafe.get(
                                result, NodeGroupState::getNode, Node::getUrl))
                        .withSorting(FindNodeStatusCriteria.FIELD_ID_URL)
                        .build(),
                DataGridUtil.headingBuilder("Cluster Base Endpoint")
                        .withToolTip("The base URL for the node.")
                        .build(),
                300);

        DataGridUtil.addEndColumn(dataGrid);
    }

    private void internalRefresh() {
        dataProvider.refresh();
    }

    public void setNodeGroup(final NodeGroup nodeGroup) {
        this.nodeGroup = nodeGroup;
        internalRefresh();
    }
}
