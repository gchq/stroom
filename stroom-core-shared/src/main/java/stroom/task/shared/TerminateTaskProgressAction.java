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

import stroom.util.shared.VoidResult;

public class TerminateTaskProgressAction extends Action<VoidResult> {
    private static final long serialVersionUID = 2759048534848720682L;

    private String taskName;
    private FindTaskCriteria criteria;
    private boolean kill;

    public TerminateTaskProgressAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public TerminateTaskProgressAction(final String taskName, final FindTaskCriteria criteria, final boolean kill) {
        this.taskName = taskName;
        this.criteria = criteria;
        this.kill = kill;
    }

    public FindTaskCriteria getCriteria() {
        return criteria;
    }

    public boolean isKill() {
        return kill;
    }

    @Override
    public String getTaskName() {
        return taskName;
    }
}
