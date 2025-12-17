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

import stroom.util.shared.TreeAction;

import java.util.HashSet;
import java.util.Set;

public class ClusterValuesTreeAction implements TreeAction<ClusterValuesRow> {

    private Set<ClusterValuesRow> expandedRows = new HashSet<>();
    private final Set<ClusterValuesRow> collapsedRows = new HashSet<>();

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
