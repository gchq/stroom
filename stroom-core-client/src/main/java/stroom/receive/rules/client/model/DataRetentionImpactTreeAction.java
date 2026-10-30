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

package stroom.receive.rules.client.model;

import stroom.util.shared.TreeAction;

import java.util.HashSet;
import java.util.Set;

public class DataRetentionImpactTreeAction implements TreeAction<DataRetentionImpactRow> {

    private Set<DataRetentionImpactRow> expandedRows = new HashSet<>();
    private final Set<DataRetentionImpactRow> collapsedRows = new HashSet<>();

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

    public void setExpandedRows(final Set<DataRetentionImpactRow> expandedRows) {
        this.expandedRows = expandedRows;
    }

    public void reset() {
        expandedRows.clear();
        collapsedRows.clear();
    }

    public void expandAll() {
        expandedRows.addAll(collapsedRows);
        collapsedRows.clear();
    }

    public void collapseAll() {
        collapsedRows.addAll(expandedRows);
        expandedRows.clear();
    }

    public boolean hasExpandedRows() {
        return !expandedRows.isEmpty();
    }

    public boolean hasCollapsedRows() {
        return !collapsedRows.isEmpty();
    }
}
