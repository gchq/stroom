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

package stroom.job.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cluster.task.api.ClusterResult;
import stroom.cluster.task.api.ClusterTaskHandler;
import stroom.cluster.task.api.ClusterTaskRef;
import stroom.cluster.task.api.ClusterWorker;
import stroom.job.api.DistributedTask;
import stroom.job.api.DistributedTaskFactory;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


class DistributedTaskRequestClusterHandler
        implements ClusterTaskHandler<DistributedTaskRequestClusterTask, DistributedTaskRequestResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedTaskRequestClusterHandler.class);

    private final ClusterWorker clusterWorker;
    private final DistributedTaskFactoryBeanRegistry distributedTaskFactoryBeanRegistry;

    private final TaskStatusTraceLog taskStatusTraceLog = new TaskStatusTraceLog();

    @Inject
    DistributedTaskRequestClusterHandler(final ClusterWorker clusterWorker,
                                         final DistributedTaskFactoryBeanRegistry distributedTaskFactoryBeanRegistry) {
        this.clusterWorker = clusterWorker;
        this.distributedTaskFactoryBeanRegistry = distributedTaskFactoryBeanRegistry;
    }

    @Override
    public void exec(final DistributedTaskRequestClusterTask request,
                     final ClusterTaskRef<DistributedTaskRequestResult> clusterTaskRef) {
        final String nodeName = request.getNodeName();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Task request: node=\"" + nodeName + "\"");
            if (LOGGER.isTraceEnabled()) {
                final String trace = "\nTask request: node=\"" + nodeName + "\"\n" + request.toString();
                LOGGER.trace(trace);
            }
        }

        // Return the requested number of tasks from the cache.
        final Map<String, List<DistributedTask<?>>> tasksToReturn = new HashMap<>();
        int totalTasks = 0;

        try {
            final DistributedRequiredTask[] requiredTasks = request.getRequiredTasks();

            if (requiredTasks.length > 0) {
                // Loop over jobs with the same priority until we get no more
                // tasks for them or have reached the maximum number of tasks to
                // return.
                for (final DistributedRequiredTask requiredTask : requiredTasks) {
                    final String jobName = requiredTask.getJobName();

                    // Make sure we are still supposed to be getting tasks for
                    // this job.
                    final int requiredTaskCount = requiredTask.getRequiredTaskCount();

                    // Try and get the next task for this job.
                    if (requiredTaskCount > 0) {
                        LOGGER.trace("Getting tasks for {}", jobName);
                        final DistributedTaskFactory<DistributedTask<?>, ?> factory = getDistributedTaskFactory(
                                jobName);
                        final List<DistributedTask<?>> fetched = factory.fetch(nodeName, requiredTaskCount);
                        tasksToReturn.put(jobName, fetched);
                        totalTasks += fetched.size();

                        taskStatusTraceLog.sendToWorkerNode(DistributedTaskRequestClusterHandler.class, fetched, nodeName,
                                jobName);
                    }
                }
            }

            // Form the valid response
            final DistributedTaskRequestResult response = new DistributedTaskRequestResult(totalTasks, tasksToReturn);

            if (LOGGER.isTraceEnabled()) {
                final String trace = "Task response: node=\"" + nodeName + "\"\n" + response.toString();
                LOGGER.trace(trace);
            } else if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Task response: node=\"" + nodeName + "\"");
            }

            try {
                clusterWorker.sendResult(ClusterResult.success(clusterTaskRef, response));
            } catch (final RuntimeException e) {
                // If we couldn't return the tasks for any reason then abandon them.
                abandonTasks(nodeName, tasksToReturn);
            }
        } catch (final Throwable e) {
            LOGGER.error(e.getMessage(), e);
            try {
                clusterWorker.sendResult(ClusterResult.failure(clusterTaskRef, e));
            } catch (final Throwable e2) {
                abandonTasks(nodeName, tasksToReturn);
            }
        }
    }

    private void abandonTasks(final String nodeName, final Map<String, List<DistributedTask<?>>> tasksToReturn) {
        try {
            LOGGER.error("Abandoning tasks that we failed to call back with");
            // Failed to call back
            for (final Entry<String, List<DistributedTask<?>>> entry : tasksToReturn.entrySet()) {
                try {
                    final String jobName = entry.getKey();
                    final List<DistributedTask<?>> tasks = entry.getValue();

                    taskStatusTraceLog.errorSendingToWorkerNode(DistributedTaskRequestClusterHandler.class, tasks, nodeName,
                            jobName);

                    final DistributedTaskFactory<DistributedTask<?>, ?> factory = getDistributedTaskFactory(jobName);
                    factory.abandon(nodeName, tasks);
                } catch (final RuntimeException e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error("Error abandoning tasks that we failed to call back with", e);
        }
    }

    private DistributedTaskFactory<DistributedTask<?>, ?> getDistributedTaskFactory(final String jobName) {
        final DistributedTaskFactory<DistributedTask<?>, ?> factory = distributedTaskFactoryBeanRegistry
                .findFactory(jobName);
        if (factory == null) {
            throw new RuntimeException("Unable to find task factory for: " + jobName);
        }
        return factory;
    }
}
