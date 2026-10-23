/*
 * Copyright 2026 Crown Copyright
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

package stroom.pathways.client.presenter;

import stroom.util.shared.TreeAction;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Which traces are open in the mutation list.
 *
 * <p>Held by trace id rather than by row, because the rows are rebuilt every time the list is drawn
 * and a trace has to stay open across that.
 */
class MutationTreeAction implements TreeAction<MutationRow> {

    private final Set<String> expanded = new HashSet<>();

    boolean isExpanded(final String group) {
        return expanded.contains(group);
    }

    void setExpanded(final String group, final boolean isExpanded) {
        if (isExpanded) {
            expanded.add(group);
        } else {
            expanded.remove(group);
        }
    }

    void expandAll(final Collection<String> groups) {
        expanded.addAll(groups);
    }

    void collapseAll() {
        expanded.clear();
    }

    @Override
    public void setRowExpanded(final MutationRow row, final boolean isExpanded) {
        setExpanded(row.getGroup(), isExpanded);
    }

    @Override
    public boolean isRowExpanded(final MutationRow row) {
        return isExpanded(row.getGroup());
    }

    /**
     * Unused: what is open is tracked by trace id, and nothing asks for the rows themselves.
     */
    @Override
    public Set<MutationRow> getExpandedRows() {
        return new HashSet<>();
    }
}
