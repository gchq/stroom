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
import stroom.security.api.UserTokenUtil;
import stroom.security.shared.UserToken;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.GenericServerTask;
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
    ExecutorProviderImpl(final TaskManager taskManager, final SecurityContext securityContext) {
        this.taskManager = taskManager;
        this.securityContext = securityContext;
    }

    @Override
    public Executor getExecutor() {
        final Task<?> parentTask = CurrentTaskState.currentTask();
        return new TaskExecutor(taskManager, null, parentTask, getUserToken(parentTask), getTaskName(parentTask, "Generic Task"));
    }

    @Override
    public Executor getExecutor(final ThreadPool threadPool) {
        final Task<?> parentTask = CurrentTaskState.currentTask();
        return new TaskExecutor(taskManager, threadPool, parentTask, getUserToken(parentTask), threadPool.getName());
    }

    private UserToken getUserToken(final Task<?> parentTask) {
        if (parentTask != null && parentTask.getUserToken() != null) {
            return parentTask.getUserToken();
        }

        try {
            final String userId = securityContext.getUserId();
            if (userId != null) {
                return UserTokenUtil.create(securityContext.getUserId(), null);
            }
        } catch (final RuntimeException e) {
            LOGGER.debug("Error getting user id", e);
        }

        LOGGER.debug("Using internal processing user");
        return UserTokenUtil.processingUser();
    }

    private String getTaskName(final Task<?> parentTask, final String defaultName) {
        if (parentTask != null && parentTask.getTaskName() != null) {
            return parentTask.getTaskName();
        }

        return defaultName;
    }

    private static class TaskExecutor implements Executor {
        private final TaskManager taskManager;
        private final ThreadPool threadPool;
        private final Task<?> parentTask;
        private final UserToken userToken;
        private final String taskName;

        TaskExecutor(final TaskManager taskManager, final ThreadPool threadPool, final Task<?> parentTask, final UserToken userToken, final String taskName) {
            this.taskManager = taskManager;
            this.threadPool = threadPool;
            this.parentTask = parentTask;
            this.userToken = userToken;
            this.taskName = taskName;
        }

        @Override
        public void execute(final Runnable command) {
            final GenericServerTask genericServerTask = GenericServerTask.create(parentTask, userToken, taskName, null);
            final Runnable runnable = () -> {
                try {
                    command.run();
                } finally {
                    genericServerTask.setRunnable(null);
                }
            };
            genericServerTask.setRunnable(runnable);

            if (threadPool == null) {
                taskManager.execAsync(genericServerTask);
            } else {
                taskManager.execAsync(genericServerTask, threadPool);
            }
        }
    }
}
