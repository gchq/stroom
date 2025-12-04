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

package stroom.processor.impl;

import stroom.processor.shared.ProcessorTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

class TaskStatusTraceLog {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskStatusTraceLog.class);

    void addUnownedTasks(final Class<?> clazz, final List<ProcessorTask> streamTasks) {
        if (LOGGER.isTraceEnabled() && streamTasks.size() > 0) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Master adding ");
            sb.append(streamTasks.size());
            sb.append(" unowned tasks");
            appendStreamTaskList(sb, streamTasks);
            appendClass(sb, clazz);
            LOGGER.trace(sb.toString());
        }
    }

    void assignTasks(final Class<?> clazz, final List<ProcessorTask> streamTasks, final String nodeName) {
        if (LOGGER.isTraceEnabled() && streamTasks.size() > 0) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Master assigned ");
            sb.append(streamTasks.size());
            sb.append(" stream tasks to worker ");
            sb.append(nodeName);
            appendStreamTaskList(sb, streamTasks);
            appendClass(sb, clazz);
            LOGGER.trace(sb.toString());
        }
    }

    void abandonTasks(final Class<?> clazz, final List<ProcessorTask> streamTasks, final String nodeName) {
        if (LOGGER.isTraceEnabled() && streamTasks.size() > 0) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Master abandoned ");
            sb.append(streamTasks.size());
            sb.append(" stream tasks for worker ");
            sb.append(nodeName);
            appendStreamTaskList(sb, streamTasks);
            appendClass(sb, clazz);
            LOGGER.trace(sb.toString());
        }
    }

    private void appendStreamTaskList(final StringBuilder sb, final List<ProcessorTask> streamTasks) {
        sb.append(" ( ");
        for (final ProcessorTask task : streamTasks) {
            sb.append(task.getId());
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
