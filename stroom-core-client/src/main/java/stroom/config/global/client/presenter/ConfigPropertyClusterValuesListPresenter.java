package stroom.config.global.client.presenter;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.cell.expander.client.ExpanderCell;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.entity.client.presenter.TreeRowHandler;
import stroom.task.shared.TaskProgress;
import stroom.util.shared.Expander;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConfigPropertyClusterValuesListPresenter
        extends MyPresenterWidget<DataGridView<ClusterValuesRow>> {

    private Column<ClusterValuesRow, Expander> expanderColumn;
    private ListDataProvider<ClusterValuesRow> dataProvider;

    @Inject
    public ConfigPropertyClusterValuesListPresenter(final EventBus eventBus) {
        super(eventBus, new DataGridViewImpl<>(false, 1000));
        this.dataProvider = new ListDataProvider<>();
        initTableColumns();

        dataProvider.addDataDisplay(getView().getDataDisplay());

        // Handle use of the expander column.
//        dataProvider.setTreeRowHandler(new TreeRowHandler<TaskProgress>(request, getView(), expanderColumn));
    }

    public void setData(final Map<String, Set<String>> effectiveValueToNodesMap) {
        final List<ClusterValuesRow> rows = ClusterValuesRow.buildTree(effectiveValueToNodesMap);
        dataProvider.setList(rows);
        dataProvider.refresh();
    }

    private void initTableColumns() {
        // Expander column.
        expanderColumn = new Column<ClusterValuesRow, Expander>(new ExpanderCell()) {
            @Override
            public Expander getValue(final ClusterValuesRow row) {
                return buildExpander(row);
            }
        };
        getView().addColumn(expanderColumn, "");

        getView().addResizableColumn(new Column<ClusterValuesRow, String>(new TextCell()) {
            @Override
            public String getValue(final ClusterValuesRow row) {
                if (row == null) {
                    return null;
                }
                return row.getEffectiveValue();
            }
        }, "Effective Value", 300);

        getView().addResizableColumn(new Column<ClusterValuesRow, String>(new TextCell()) {
            @Override
            public String getValue(final ClusterValuesRow row) {
                if (row == null) {
                    return null;
                }
                return row.getNodeName();
            }
        }, "Node", 300);
    }

    private Expander buildExpander(final ClusterValuesRow row) {
        return row.getExpander();
    }
}
