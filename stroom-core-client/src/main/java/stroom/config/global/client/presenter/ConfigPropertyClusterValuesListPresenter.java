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

package stroom.config.global.client.presenter;

import stroom.cell.expander.client.ExpanderCell;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.entity.client.presenter.TreeRowHandler;
import stroom.util.shared.Expander;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ConfigPropertyClusterValuesListPresenter
        extends MyPresenterWidget<PagerView> {

    private Column<ClusterValuesRow, Expander> expanderColumn;
    private final ListDataProvider<ClusterValuesRow> dataProvider;
    private final ClusterValuesTreeAction treeAction = new ClusterValuesTreeAction();
    private final MyDataGrid<ClusterValuesRow> dataGrid;

    private Map<String, Set<NodeSource>> effectiveValueToNodeSourcesMap;

    @Inject
    public ConfigPropertyClusterValuesListPresenter(final EventBus eventBus,
                                                    final PagerView view) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>(this, 1000);
        dataGrid.setMultiLine(true);
        view.setDataWidget(dataGrid);

        dataProvider = new ListDataProvider<>();
        initTableColumns();

        dataProvider.addDataDisplay(dataGrid);

//         Handle use of the expander column.
        dataProvider.setTreeRowHandler(new TreeRowHandler<>(treeAction, dataGrid, expanderColumn));
    }

    // For DEV testing only, when you don't have two nodes
    private Map<String, Set<String>> makeDemoData() {
        final Supplier<String> junkTextSupplier = () ->
                IntStream.rangeClosed(1, 5)
                        .boxed()
                        .map(i -> this.getClass().getCanonicalName())
                        .collect(Collectors.joining(" "));

        final Map<String, Set<String>> demoMap = new HashMap<>();
        IntStream.rangeClosed(1, 9)
                .forEach(i -> {
                    demoMap.put("value " + i + junkTextSupplier.get(), IntStream.rangeClosed(i * 10, (i * 10) + 9)
                            .boxed()
                            .map(j -> "node" + j)
                            .collect(Collectors.toSet()));
                });
        return demoMap;
    }

    public void focus() {
        dataGrid.setFocus(true);
    }

    public void setData(final Map<String, Set<NodeSource>> effectiveValueToNodesMap) {

        // For DEV testing only, when you don't have two nodes
//        this.effectiveValueToNodesMap = makeDemoData();
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
        dataGrid.addEndColumn(new EndColumn<>());
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

    @Override
    protected void onReveal() {
        super.onReveal();
        refresh();
    }

    public void refresh() {
        final List<ClusterValuesRow> rows = ClusterValuesRow.buildTree(effectiveValueToNodeSourcesMap, treeAction);
        dataProvider.setCompleteList(rows);
        dataProvider.refresh(true);
    }
}
