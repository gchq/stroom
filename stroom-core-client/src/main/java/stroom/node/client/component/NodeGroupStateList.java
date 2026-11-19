/*
 * Copyright 2026 Crown Copyright
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

package stroom.node.client.component;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.client.TickBoxState;
import stroom.node.shared.FindNodeStatusCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.NodeStatusResult;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.ColumnSizeConstants;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.HashSet;
import java.util.Set;

/// The nodes, a row each with a tick box to include it in a node group (or, if the group is
/// inverted, to exclude it), whether it is included, its name and endpoint; a tick box in the header
/// ticks them all.
public class NodeGroupStateList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<NodeStatusResult> dataGrid;

    private boolean invertSelection;
    private Set<Integer> allNodes = new HashSet<>();
    private Set<Integer> selectedNodes = new HashSet<>();
    private Runnable onSortChange = () -> {
    };

    /// Makes an empty list.
    public NodeGroupStateList() {
        initWidget(BINDER.createAndBindUi(this));
        addColumns();
        dataGrid.addColumnSortHandler(event -> onSortChange.run());
    }

    /// @param dataProvider Gives the list its rows.
    public void setDataProvider(final AbstractDataProvider<NodeStatusResult> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return How the rows are sorted, for the request for them.
    public ColumnSortList getSortList() {
        return dataGrid.getColumnSortList();
    }

    /// Draws the header's tick box again.
    public void redrawHeaders() {
        dataGrid.redrawHeaders();
    }

    /// @param onSortChange Run when the user sorts by another column.
    public void setOnSortChange(final Runnable onSortChange) {
        this.onSortChange = onSortChange;
    }

    private void addColumns() {
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
    }

    /// @param allNodes The ids of every node, for the header's tick box.
    public void setAllNodes(final Set<Integer> allNodes) {
        this.allNodes = allNodes;
    }

    /// @param selectedNodes The ids of the nodes ticked, which ticking changes.
    public void setSelectedNodes(final Set<Integer> selectedNodes) {
        this.selectedNodes = selectedNodes;
    }

    /// @param invertSelection Whether the group is the nodes not ticked.
    public void setInvertSelection(final boolean invertSelection) {
        this.invertSelection = invertSelection;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, NodeGroupStateList> {

    }
}
