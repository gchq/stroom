/*
 * Copyright 2016 Crown Copyright
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

package stroom.task.shared;

import stroom.dispatch.shared.TreeAction;
import stroom.entity.shared.FindAction;

import java.util.Set;

public class FindTaskProgressAction extends FindAction<FindTaskProgressCriteria, TaskProgress>
        implements TreeAction<TaskProgress> {
    public static final String MANAGE_TASKS_PERMISSION = "Manage Tasks";
    private static final long serialVersionUID = -5285569438944240375L;

    public FindTaskProgressAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public FindTaskProgressAction(final FindTaskProgressCriteria criteria) {
        super(criteria);
    }

    @Override
    public String getTaskName() {
        return "Find Task Progress";
    }

    @Override
    public Set<TaskProgress> getExpandedRows() {
        return null;
    }

    @Override
    public void setRowExpanded(final TaskProgress row, final boolean open) {
        getCriteria().setExpanded(row, open);
    }

    @Override
    public boolean isRowExpanded(final TaskProgress row) {
        return getCriteria().isExpanded(row);
    }
}
