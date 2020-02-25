package stroom.config.global.client.presenter;

import stroom.util.shared.TreeAction;

import java.util.HashSet;
import java.util.Set;

public class ClusterValuesTreeAction implements TreeAction<ClusterValuesRow> {
    private Set<ClusterValuesRow> expandedRows = new HashSet<>();
    private Set<ClusterValuesRow> collapsedRows = new HashSet<>();

    @Override
    public void setRowExpanded(final ClusterValuesRow row, final boolean expanded) {
        if (expanded) {
            expandedRows.add(row);
            collapsedRows.remove(row);
        } else {
            expandedRows.remove(row);
            collapsedRows.add(row);
        }
    }

    @Override
    public boolean isRowExpanded(final ClusterValuesRow row) {
        return expandedRows.contains(row);
    }

    public boolean isRowCollapsed(final ClusterValuesRow row) {
        return collapsedRows.contains(row);
    }

    @Override
    public Set<ClusterValuesRow> getExpandedRows() {
        return expandedRows;
    }

    void setExpandedRows(final Set<ClusterValuesRow> expandedRows) {
        this.expandedRows = expandedRows;
    }
}
