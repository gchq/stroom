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

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.node.client.NodeClient;
import stroom.node.client.NodeGroupClient;
import stroom.node.shared.FetchNodeStatusResponse;
import stroom.node.shared.FindNodeStatusCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.NodeStatusResult;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;

import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class NodeGroupStateListPresenter extends MyPresenterWidget<PagerView> {

    private final MyDataGrid<NodeStatusResult> dataGrid;
    private final RestDataProvider<NodeStatusResult, FetchNodeStatusResponse> dataProvider;
    private final FindNodeStatusCriteria findNodeStatusCriteria = new FindNodeStatusCriteria();

    private boolean invertSelection;
    private Set<Integer> allNodes = new HashSet<>();
    private Set<Integer> selectedNodes = new HashSet<>();

    @Inject
    public NodeGroupStateListPresenter(final EventBus eventBus,
                                       final PagerView view,
                                       final NodeClient nodeClient,
                                       final NodeGroupClient nodeGroupClient) {
        super(eventBus, view);
        dataGrid = new MyDataGrid<>(this);
        view.setDataWidget(dataGrid);

        initTableColumns();
        dataProvider = new RestDataProvider<NodeStatusResult, FetchNodeStatusResponse>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<FetchNodeStatusResponse> dataConsumer,
                                final RestErrorHandler errorHandler) {
                CriteriaUtil.setSortList(findNodeStatusCriteria, dataGrid.getColumnSortList());
                nodeClient.fetchNodeStatus(dataConsumer, errorHandler, findNodeStatusCriteria,
                        NodeGroupStateListPresenter.this);
            }
        };
        dataProvider.addDataDisplay(dataGrid);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(dataGrid.addColumnSortHandler(event -> refresh()));
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Include/Exclude in group
        final Column<NodeStatusResult, TickBoxState> column = new Column<NodeStatusResult, TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue(final NodeStatusResult object) {
                return TickBoxState.fromBoolean(selectedNodes.contains(object.getNode().getId()));
            }
        };

        final Header<TickBoxState> header = new Header<TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue() {
                if (allNodes.size() == selectedNodes.size()) {
                    return TickBoxState.TICK;
                }
                if (!selectedNodes.isEmpty()) {
                    return TickBoxState.HALF_TICK;
                }
                return TickBoxState.UNTICK;
            }
        };
        header.setUpdater(value -> {
            if (value.equals(TickBoxState.UNTICK)) {
                selectedNodes.clear();
            } else if (value.equals(TickBoxState.TICK)) {
                selectedNodes.addAll(allNodes);
            }
            dataGrid.redrawHeaders();
            dataGrid.redraw();
        });
        dataGrid.addColumn(column, header, ColumnSizeConstants.CHECKBOX_COL);

        // Add Handlers
        column.setFieldUpdater((index, row, value) -> {
            if (value.toBoolean()) {
                selectedNodes.add(row.getNode().getId());

            } else {
                selectedNodes.remove(row.getNode().getId());
            }
            dataGrid.redrawHeaders();
            dataGrid.redraw();
        });

        // Status.
        dataGrid.addResizableColumn(
                DataGridUtil
                        .textColumnBuilder((final NodeStatusResult result) -> {
                            boolean selected = selectedNodes.contains(result.getNode().getId());
                            if (invertSelection) {
                                selected = !selected;
                            }
                            if (selected) {
                                return "Included";
                            } else {
                                return "Excluded";
                            }
                        })
                        .build(),
                DataGridUtil.headingBuilder("Status")
                        .withToolTip("The include status.")
                        .build(),
                80);

        // Name.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataGridUtil.toStringFunc(NodeStatusResult::getNode, Node::getName))
                        .withSorting(FindNodeStatusCriteria.FIELD_ID_NAME)
                        .build(),
                DataGridUtil.headingBuilder("Name")
                        .withToolTip("The name of the node as defined in configuration.")
                        .build(),
                300);

        // Host Name.
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((
                                NodeStatusResult result) -> NullSafe.get(
                                result, NodeStatusResult::getNode, Node::getUrl))
                        .withSorting(FindNodeStatusCriteria.FIELD_ID_URL)
                        .build(),
                DataGridUtil.headingBuilder("Cluster Base Endpoint")
                        .withToolTip("The base URL for the node.")
                        .build(),
                300);

        DataGridUtil.addEndColumn(dataGrid);
    }

    public void setAllNodes(final Set<Integer> allNodes) {
        this.allNodes = allNodes;
    }

    public void setSelectedNodes(final Set<Integer> selectedNodes) {
        this.selectedNodes = selectedNodes;
    }

    public void setInvertSelection(final boolean invertSelection) {
        this.invertSelection = invertSelection;
    }

    public void refresh() {
        dataProvider.refresh();
        dataGrid.redrawHeaders();
    }
}
