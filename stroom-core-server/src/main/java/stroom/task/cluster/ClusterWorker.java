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

package stroom.task.cluster;

import stroom.node.shared.Node;
import stroom.util.shared.SharedObject;
import stroom.util.shared.TaskId;

public interface ClusterWorker {
    /**
     * When a node wants to execute a task on another node in the cluster they
     * dispatch the task with <code>ClusterDispatchAsync.execAsync()</code>.
     * <code>ClusterDispatchAsync.execAsync()</code> makes a cluster call which
     * executes this method on target worker nodes. This method then hands off
     * execution to the task manager so that each worker node will execute the
     * task asynchronously without the source node waiting for a response which
     * would result in the HTTP connection being held too long. Once
     * asynchronous execution has completed another cluster call is made to pass
     * the execution result back to the source node.
     *
     * @param task
     *            The task to execute on the target worker node.
     * @param sourceNode
     *            The node that this task originated from.
     * @param sourceTaskId
     *            The id of the parent task that owns this worker cluster task.
     * @param collectorId
     *            The id of the collector to send results back to.
     */
    <R extends SharedObject> void execAsync(ClusterTask<R> task, Node sourceNode, TaskId sourceTaskId,
            CollectorId collectorId);
}
