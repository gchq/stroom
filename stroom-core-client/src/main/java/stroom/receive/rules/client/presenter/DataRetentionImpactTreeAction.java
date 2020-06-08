package stroom.receive.rules.client.presenter;

import stroom.util.shared.TreeAction;

import java.util.HashSet;
import java.util.Set;

public class DataRetentionImpactTreeAction implements TreeAction<DataRetentionImpactRow> {
    private Set<DataRetentionImpactRow> expandedRows = new HashSet<>();
    private Set<DataRetentionImpactRow> collapsedRows = new HashSet<>();

    @Override
    public void setRowExpanded(final DataRetentionImpactRow row, final boolean expanded) {
        if (expanded) {
            expandedRows.add(row);
            collapsedRows.remove(row);
        } else {
            expandedRows.remove(row);
            collapsedRows.add(row);
        }
    }

    @Override
    public boolean isRowExpanded(final DataRetentionImpactRow row) {
        return expandedRows.contains(row);
    }

    public boolean isRowCollapsed(final DataRetentionImpactRow row) {
        return collapsedRows.contains(row);
    }

    @Override
    public Set<DataRetentionImpactRow> getExpandedRows() {
        return expandedRows;
    }

    void setExpandedRows(final Set<DataRetentionImpactRow> expandedRows) {
        this.expandedRows = expandedRows;
    }
}
