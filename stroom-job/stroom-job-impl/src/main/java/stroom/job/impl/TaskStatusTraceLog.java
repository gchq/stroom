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

package stroom.job.impl;

import stroom.job.api.DistributedTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

class TaskStatusTraceLog {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskStatusTraceLog.class);

    void sendToWorkerNode(final Class<?> clazz, final List<DistributedTask> tasks, final String nodeName,
                          final String jobName) {
        if (LOGGER.isTraceEnabled() && tasks.size() > 0) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Master sending ");
            sb.append(tasks.size());
            sb.append(" tasks to worker ");
            sb.append(nodeName);
            sb.append(" for job '");
            sb.append(jobName);
            sb.append("'");
            appendDistributedTaskList(sb, tasks);
            appendClass(sb, clazz);
            LOGGER.trace(sb.toString());
        }
    }

    void errorSendingToWorkerNode(final Class<?> clazz, final List<DistributedTask> tasks, final String nodeName,
                                  final String jobName) {
        if (LOGGER.isTraceEnabled() && tasks.size() > 0) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Master failed to send ");
            sb.append(tasks.size());
            sb.append(" tasks to worker ");
            sb.append(nodeName);
            sb.append(" for job '");
            sb.append(jobName);
            sb.append("'");
            appendDistributedTaskList(sb, tasks);
            appendClass(sb, clazz);
            LOGGER.trace(sb.toString());
        }
    }

    void receiveOnWorkerNode(final Class<?> clazz, final List<DistributedTask> tasks, final String jobName) {
        if (LOGGER.isTraceEnabled() && tasks.size() > 0) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Worker received ");
            sb.append(tasks.size());
            sb.append(" tasks");
            sb.append(" for job '");
            sb.append(jobName);
            sb.append("'");
            appendDistributedTaskList(sb, tasks);
            appendClass(sb, clazz);
            LOGGER.trace(sb.toString());
        }
    }

    private void appendDistributedTaskList(final StringBuilder sb,
                                           final List<DistributedTask> streamProcessorTasks) {
        sb.append(" ( ");
        for (final DistributedTask task : streamProcessorTasks) {
            sb.append(task.getTraceString());
            sb.append(" ");
        }
        sb.append(")");
    }

    private void appendClass(final StringBuilder sb, final Class<?> clazz) {
        sb.append(" [");
        sb.append(clazz.getSimpleName());
        sb.append("]");
    }
}
