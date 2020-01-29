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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskManager;
import stroom.task.shared.Task;
import stroom.task.shared.ThreadPool;

import javax.inject.Inject;
import java.util.concurrent.Executor;

class ExecutorProviderImpl implements ExecutorProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutorProviderImpl.class);

    private final TaskManager taskManager;
    private final SecurityContext securityContext;

    @Inject
    ExecutorProviderImpl(final TaskManager taskManager,
                         final SecurityContext securityContext) {
        this.taskManager = taskManager;
        this.securityContext = securityContext;
    }

    @Override
    public Executor getExecutor() {
        final UserIdentity userIdentity = securityContext.getUserIdentity();
        if (userIdentity == null) {
            throw new NullPointerException("Null user identity");
        }

        final Task<?> parentTask = CurrentTaskState.currentTask();
        return new ExecutorImpl(taskManager, null, securityContext, parentTask, getTaskName(parentTask, "Generic Task"), userIdentity);
    }

    @Override
    public Executor getExecutor(final ThreadPool threadPool) {
        final UserIdentity userIdentity = securityContext.getUserIdentity();
        if (userIdentity == null) {
            throw new NullPointerException("Null user identity");
        }

        final Task<?> parentTask = CurrentTaskState.currentTask();
        return new ExecutorImpl(taskManager, threadPool, securityContext, parentTask, threadPool.getName(), userIdentity);
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
        private final SecurityContext securityContext;
        private final Task<?> parentTask;
        private final String taskName;
        private final UserIdentity userIdentity;

        ExecutorImpl(final TaskManager taskManager, final ThreadPool threadPool, final SecurityContext securityContext, final Task<?> parentTask, final String taskName, final UserIdentity userIdentity) {
            this.taskManager = taskManager;
            this.threadPool = threadPool;
            this.securityContext = securityContext;
            this.parentTask = parentTask;
            this.taskName = taskName;
            this.userIdentity = userIdentity;
        }

        @Override
        public void execute(final Runnable command) {
            securityContext.asUser(userIdentity, () -> taskManager.execAsync(parentTask, taskName, command, threadPool));
        }
    }
}
