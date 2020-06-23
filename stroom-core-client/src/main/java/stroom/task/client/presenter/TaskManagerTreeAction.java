package stroom.task.client.presenter;

import stroom.task.shared.TaskProgress;
import stroom.util.shared.TreeAction;
import stroom.widget.customdatebox.client.ClientDateUtil;

import com.google.gwt.core.client.GWT;

import java.util.HashSet;
import java.util.Set;

public class TaskManagerTreeAction implements TreeAction<TaskProgress> {
    private Set<TaskProgress> expandedRows = new HashSet<>();
    private Set<TaskProgress> collapsedRows = new HashSet<>();
    private boolean expandAllRequested = false;
//    private boolean collapseAllRequested = false;

    @Override
    public void setRowExpanded(final TaskProgress row, final boolean expanded) {
        GWT.log(ClientDateUtil.toISOString(System.currentTimeMillis()) + ": " + row.getId().getId() + " " + row.getTaskName() + " setting expanded to " + expanded);
        if (expanded) {
            expandedRows.add(row);
            collapsedRows.remove(row);
//            allCollapsed = false;

        } else {
            expandedRows.remove(row);
            collapsedRows.add(row);
//            allExpanded = false;
        }
    }

    @Override
    public boolean isRowExpanded(final TaskProgress row) {
//        return (allExpanded && !allCollapsed) || (!allExpanded && expandedRows.contains(row));
        return expandedRows.contains(row);
    }

    public boolean isRowCollapsed(final TaskProgress row) {
//        return (allCollapsed && !allExpanded) || (!allCollapsed && collapsedRows.contains(row));
        return collapsedRows.contains(row);
    }

    @Override
    public Set<TaskProgress> getExpandedRows() {
        return expandedRows;
    }

    void setExpandedRows(final Set<TaskProgress> expandedRows) {
        this.expandedRows = expandedRows;
    }

    void reset() {
        expandedRows.clear();
        collapsedRows.clear();
        expandAllRequested = false;
    }

    void expandAll() {
//        allExpanded = true;
//        allCollapsed = false;
        expandedRows.addAll(collapsedRows);
        collapsedRows.clear();
        expandAllRequested = true;

    }

    void resetExpandAllRequestState() {
        expandAllRequested = false;
    }

    public boolean isExpandAllRequested() {
        return expandAllRequested;
    }

    void collapseAll() {
//        allExpanded = false;
//        allCollapsed = true;
        collapsedRows.addAll(expandedRows);
        expandedRows.clear();
        expandAllRequested = false;
//        collapseAllRequested = true;
    }

    boolean hasExpandedRows() {
//        return allExpanded || !expandedRows.isEmpty();
        return !expandedRows.isEmpty();
    }

    boolean hasCollapsedRows() {
//        return allCollapsed || !collapsedRows.isEmpty();
        return !collapsedRows.isEmpty();
    }

    public boolean hasExpandedState(final TaskProgress taskProgress) {
        return expandedRows.contains(taskProgress) || collapsedRows.contains(taskProgress);
    }
}
