package stroom.config.global.client.presenter;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.cell.expander.client.ExpanderCell;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.entity.client.presenter.TreeRowHandler;
import stroom.util.shared.Expander;
import stroom.util.shared.TreeAction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ConfigPropertyClusterValuesListPresenter
        extends MyPresenterWidget<DataGridView<ClusterValuesRow>> {

    private Column<ClusterValuesRow, Expander> expanderColumn;
    private ListDataProvider<ClusterValuesRow> dataProvider;
    private TreeAction<ClusterValuesRow> treeAction = new ClusterValuesTreeAction();
    private Map<String, Set<String>> effectiveValueToNodesMap;

    @Inject
    public ConfigPropertyClusterValuesListPresenter(final EventBus eventBus) {
        super(eventBus, new DataGridViewImpl<>(false, 1000));
        this.dataProvider = new ListDataProvider<>();
        initTableColumns();

        dataProvider.addDataDisplay(getView().getDataDisplay());

//         Handle use of the expander column.
        dataProvider.setTreeRowHandler(new TreeRowHandler<>(treeAction, getView(), expanderColumn));
    }

    private Map<String, Set<String>> makeDemoData() {
        Supplier<String> junkTextSupplier = () ->
            IntStream.rangeClosed(1,5)
                .boxed()
                .map(i -> this.getClass().getCanonicalName())
                .collect(Collectors.joining(" "));

        Map<String, Set<String>>  demoMap = new HashMap<>();
        IntStream.rangeClosed(1, 9)
            .forEach(i -> {
                demoMap.put("value " + i + junkTextSupplier.get(), IntStream.rangeClosed(i*10,(i*10)+9)
                    .boxed()
                    .map(j -> "node" + j)
                    .collect(Collectors.toSet()));
            });
        return demoMap;
    }

    public void setData(final Map<String, Set<String>> effectiveValueToNodesMap) {

        // TODO remove
        this.effectiveValueToNodesMap = makeDemoData();
//        this.effectiveValueToNodesMap = effectiveValueToNodesMap;

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

        getView().addColumn(expanderColumn, "");

        getView().addResizableColumn(new Column<ClusterValuesRow, String>(new TextCell()) {
            @Override
            public String getValue(final ClusterValuesRow row) {
                if (row == null) {
                    return null;
                }
                return row.getEffectiveValue();
            }
        }, "Effective Value", 550);

        getView().addResizableColumn(new Column<ClusterValuesRow, String>(new TextCell()) {
            @Override
            public String getValue(final ClusterValuesRow row) {
                if (row == null) {
                    return null;
                }
                return row.getNodeName();
            }
        }, "Node", 300);
        getView().addEndColumn(new EndColumn<>());
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
        final List<ClusterValuesRow> rows = ClusterValuesRow.buildTree(effectiveValueToNodesMap, treeAction);
        dataProvider.setList(rows);
        dataProvider.refresh();
    }
}
