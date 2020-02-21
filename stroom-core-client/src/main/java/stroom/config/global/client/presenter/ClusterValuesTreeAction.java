package stroom.config.global.client.presenter;

import stroom.util.shared.TreeAction;

import java.util.HashSet;
import java.util.Set;

public class ClusterValuesTreeAction implements TreeAction<ClusterValuesRow> {
    private Set<ClusterValuesRow> expandedRows = new HashSet<>();

    @Override
    public void setRowExpanded(final ClusterValuesRow row, final boolean expanded) {
        if (expanded) {
            expandedRows.add(row);
        } else {
            expandedRows.remove(row);
        }
    }

    @Override
    public boolean isRowExpanded(final ClusterValuesRow row) {
        return expandedRows.contains(row);
    }

    @Override
    public Set<ClusterValuesRow> getExpandedRows() {
        return expandedRows;
    }

    void setExpandedRows(final Set<ClusterValuesRow> expandedRows) {
        this.expandedRows = expandedRows;
    }
}
