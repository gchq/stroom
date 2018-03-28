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

import stroom.node.shared.Node;
import stroom.task.cluster.ClusterTask;
import stroom.util.shared.Task;

class DistributedTaskRequestClusterTask extends ClusterTask<DistributedTaskRequestResult> {
    private static final long serialVersionUID = 8371445065601694269L;

    private final Task<?> parentTask;
    private final Node node;
    private final DistributedRequiredTask[] requiredTasks;

    DistributedTaskRequestClusterTask(final Task<?> parentTask, final String taskName, final Node node, final DistributedRequiredTask[] requiredTasks) {
        super(parentTask.getUserToken(), taskName);
        this.parentTask = parentTask;
        this.node = node;
        this.requiredTasks = requiredTasks;
    }

    public Node getNode() {
        return node;
    }

    DistributedRequiredTask[] getRequiredTasks() {
        return requiredTasks;
    }

    @Override
    public Task<?> getParentTask() {
        return parentTask;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        if (requiredTasks != null) {
            for (final DistributedRequiredTask requiredTask : requiredTasks) {
                sb.append('\t');
                sb.append(requiredTask.getRequiredTaskCount());
                sb.append(" : ");
                sb.append(requiredTask.getJobNode().toString());
                sb.append('\n');
            }
        }
        return sb.toString();
    }
}
