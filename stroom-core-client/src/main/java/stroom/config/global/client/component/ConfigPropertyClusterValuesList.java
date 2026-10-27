/*
 * Copyright 2020 Crown Copyright
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

package stroom.config.global.client.component;

import stroom.cell.expander.client.ExpanderCell;
import stroom.config.global.client.presenter.ListDataProvider;
import stroom.config.global.client.presenter.NodeSource;
import stroom.entity.client.presenter.TreeRowHandler;
import stroom.util.shared.Expander;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/// A property's values across the cluster: a row for each value, which expands to the nodes that
/// have it, with where each node's value comes from.
public class ConfigPropertyClusterValuesList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DataGrid<ClusterValuesRow> dataGrid;

    private Column<ClusterValuesRow, Expander> expanderColumn;
    private final ListDataProvider<ClusterValuesRow> dataProvider;
    private final ClusterValuesTreeAction treeAction = new ClusterValuesTreeAction();

    private Map<String, Set<NodeSource>> effectiveValueToNodeSourcesMap;

    /// Makes an empty list.
    public ConfigPropertyClusterValuesList() {
        initWidget(BINDER.createAndBindUi(this));

        dataProvider = new ListDataProvider<>();
        initTableColumns();

        dataProvider.addDataDisplay(dataGrid);

        // Handle use of the expander column.
        dataProvider.setTreeRowHandler(new TreeRowHandler<>(treeAction, dataGrid, expanderColumn));
    }

    /// Puts the focus on the list.
    public void focus() {
        dataGrid.setFocus(true);
    }

    /// Shows a property's values.
    ///
    /// @param effectiveValueToNodesMap Each value, with the nodes that have it and where it comes from.
    public void setData(final Map<String, Set<NodeSource>> effectiveValueToNodesMap) {

        // For DEV testing only, when you don't have two nodes
        this.effectiveValueToNodeSourcesMap = effectiveValueToNodesMap;

        refresh();
    }

    private void initTableColumns() {
        // Expander column.
        expanderColumn = new Column<ClusterValuesRow, Expander>(new ExpanderCell()) {
            @Override
            public Expander getValue(final ClusterValuesRow row) {
                return buildExpander(row);
            }
        };
        expanderColumn.setFieldUpdater((index, row, value) -> {
            treeAction.setRowExpanded(row, !value.isExpanded());
            refresh();
        });

        dataGrid.addColumn(expanderColumn, "");
        dataGrid.addResizableColumn(buildEffectiveValueColumn(), "Effective Value", 475);
        dataGrid.addResizableColumn(buildNodeCountColumn(), "Count", 50);
        dataGrid.addResizableColumn(buildBasicColumn(ClusterValuesRow::getSource), "Source", 75);
        dataGrid.addResizableColumn(buildBasicColumn(ClusterValuesRow::getNodeName), "Node", 250);
    }

    private Column<ClusterValuesRow, String> buildNodeCountColumn() {
        return buildBasicColumn(row ->
                (row.getNodeCount() != null
                        ? row.getNodeCount().toString()
                        : ""));
    }

    private Column<ClusterValuesRow, String> buildBasicColumn(final Function<ClusterValuesRow, String> valueFunc) {
        return new Column<ClusterValuesRow, String>(new TextCell()) {
            @Override
            public String getValue(final ClusterValuesRow row) {
                if (row == null) {
                    return null;
                }
                return valueFunc.apply(row);
            }
        };
    }

    private Column<ClusterValuesRow, String> buildEffectiveValueColumn() {
        return new Column<ClusterValuesRow, String>(new TextCell()) {
            @Override
            public String getValue(final ClusterValuesRow row) {
                if (row == null) {
                    return null;
                }
                return row.getEffectiveValue();
            }
        };
    }

    private Expander buildExpander(final ClusterValuesRow row) {
        return row.getExpander();
    }

    /// Shows the values again, as expanded.
    public void refresh() {
        final List<ClusterValuesRow> rows = ClusterValuesRow.buildTree(effectiveValueToNodeSourcesMap, treeAction);
        dataProvider.setCompleteList(rows);
        dataProvider.refresh(true);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ConfigPropertyClusterValuesList> {

    }
}
