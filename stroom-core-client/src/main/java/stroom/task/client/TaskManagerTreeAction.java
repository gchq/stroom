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

package stroom.task.client;

import stroom.task.shared.TaskProgress;
import stroom.util.shared.TreeAction;

import java.util.HashSet;
import java.util.Set;

public class TaskManagerTreeAction implements TreeAction<TaskProgress> {

    private Set<TaskProgress> expandedRows = new HashSet<>();
    private final Set<TaskProgress> collapsedRows = new HashSet<>();
    private boolean expandAllRequested = false;

    @Override
    public void setRowExpanded(final TaskProgress row, final boolean expanded) {
        if (expanded) {
            expandedRows.add(row);
            collapsedRows.remove(row);

        } else {
            expandedRows.remove(row);
            collapsedRows.add(row);
        }
    }

    @Override
    public boolean isRowExpanded(final TaskProgress row) {
        return expandedRows.contains(row);
    }

    public boolean isRowCollapsed(final TaskProgress row) {
        return collapsedRows.contains(row);
    }

    @Override
    public Set<TaskProgress> getExpandedRows() {
        return expandedRows;
    }

    void setExpandedRows(final Set<TaskProgress> expandedRows) {
        this.expandedRows = expandedRows;
    }

    /// Forgets which rows are expanded or collapsed.
    public void reset() {
        expandedRows.clear();
        collapsedRows.clear();
        expandAllRequested = false;
    }

    /// Asks for every row to be expanded.
    public void expandAll() {
        expandedRows.addAll(collapsedRows);
        collapsedRows.clear();
        expandAllRequested = true;

    }

    /// Forgets a request to expand every row, once it has been met.
    public void resetExpandAllRequestState() {
        expandAllRequested = false;
    }

    public boolean isExpandAllRequested() {
        return expandAllRequested;
    }

    /// Collapses every row.
    public void collapseAll() {
        collapsedRows.addAll(expandedRows);
        expandedRows.clear();
        expandAllRequested = false;
    }

    /// @return Whether any row is expanded.
    public boolean hasExpandedRows() {
        return !expandedRows.isEmpty();
    }

    /// @return Whether any row is collapsed.
    public boolean hasCollapsedRows() {
        return !collapsedRows.isEmpty();
    }

    public boolean hasExpandedState(final TaskProgress taskProgress) {
        return expandedRows.contains(taskProgress) || collapsedRows.contains(taskProgress);
    }
}
