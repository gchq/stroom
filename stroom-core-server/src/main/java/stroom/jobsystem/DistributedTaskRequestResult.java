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

package stroom.jobsystem;

import stroom.jobsystem.shared.JobNode;
import stroom.docref.SharedObject;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class DistributedTaskRequestResult implements SharedObject {
    private static final long serialVersionUID = -6827229808827594370L;

    private final int totalTasks;
    private final Map<JobNode, List<DistributedTask<?>>> taskMap;

    public DistributedTaskRequestResult(final int totalTasks, final Map<JobNode, List<DistributedTask<?>>> taskMap) {
        this.totalTasks = totalTasks;
        this.taskMap = taskMap;
    }

    public int getTotalTasks() {
        return totalTasks;
    }

    public Map<JobNode, List<DistributedTask<?>>> getTaskMap() {
        return taskMap;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        if (taskMap != null) {
            for (final Entry<JobNode, List<DistributedTask<?>>> entry : taskMap.entrySet()) {
                sb.append('\t');
                sb.append(entry.getKey().toString());
                sb.append("newTasks=\"");
                if (entry.getValue() != null) {
                    sb.append(entry.getValue().size());
                } else {
                    sb.append('0');
                }
                sb.append("\" ");
                sb.append('\n');
            }
        }
        return sb.toString();
    }
}
