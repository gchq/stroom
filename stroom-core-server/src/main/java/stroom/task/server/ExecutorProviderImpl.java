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

package stroom.task.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.security.SecurityContext;
import stroom.security.UserTokenUtil;
import stroom.util.shared.Task;
import stroom.util.shared.ThreadPool;

import javax.inject.Inject;
import java.util.concurrent.Executor;

@Component
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
        return new ExecutorImpl(taskManager, null, parentTask, getUserToken(parentTask), getTaskName(parentTask, "Generic Task"));
    }

    @Override
    public Executor getExecutor(final ThreadPool threadPool) {
        final Task<?> parentTask = CurrentTaskState.currentTask();
        return new ExecutorImpl(taskManager, threadPool, parentTask, getUserToken(parentTask), threadPool.getName());
    }

    private String getUserToken(final Task<?> parentTask) {
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
        return UserTokenUtil.INTERNAL_PROCESSING_USER_TOKEN;
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
        private final String userToken;
        private final String taskName;

        ExecutorImpl(final TaskManager taskManager, final ThreadPool threadPool, final Task<?> parentTask, final String userToken, final String taskName) {
            this.taskManager = taskManager;
            this.threadPool = threadPool;
            this.parentTask = parentTask;
            this.userToken = userToken;
            this.taskName = taskName;
        }

        @Override
        public void execute(final Runnable command) {
            taskManager.execAsync(parentTask, userToken, taskName, command, threadPool);
        }
    }
}
