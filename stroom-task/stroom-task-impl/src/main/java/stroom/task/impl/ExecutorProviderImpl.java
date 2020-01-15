/*
 * Copyright 2017 Crown Copyright
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

package stroom.task.impl;

import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskManager;
import stroom.task.shared.Task;
import stroom.task.shared.ThreadPool;

import javax.inject.Inject;
import java.util.concurrent.Executor;

class ExecutorProviderImpl implements ExecutorProvider {
    private final TaskManager taskManager;

    @Inject
    ExecutorProviderImpl(final TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    public Executor getExecutor() {
        final Task<?> parentTask = CurrentTaskState.currentTask();
        return new ExecutorImpl(taskManager, null, parentTask, getTaskName(parentTask, "Generic Task"));
    }

    @Override
    public Executor getExecutor(final ThreadPool threadPool) {
        final Task<?> parentTask = CurrentTaskState.currentTask();
        return new ExecutorImpl(taskManager, threadPool, parentTask, threadPool.getName());
    }

    private String getTaskName(final Task<?> parentTask, final String defaultName) {
        if (parentTask != null && parentTask.getTaskName() != null) {
            return parentTask.getTaskName();
        }

        return defaultName;
    }

    private static class ExecutorImpl implements Executor {
        private final TaskManager taskManager;
        private final ThreadPool threadPool;
        private final Task<?> parentTask;
        private final String taskName;

        ExecutorImpl(final TaskManager taskManager, final ThreadPool threadPool, final Task<?> parentTask, final String taskName) {
            this.taskManager = taskManager;
            this.threadPool = threadPool;
            this.parentTask = parentTask;
            this.taskName = taskName;
        }

        @Override
        public void execute(final Runnable command) {
            taskManager.execAsync(parentTask, taskName, command, threadPool);
        }
    }
}
