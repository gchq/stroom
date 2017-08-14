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

import org.springframework.stereotype.Component;
import stroom.security.SecurityContext;
import stroom.util.shared.Task;
import stroom.util.shared.ThreadPool;

import javax.inject.Inject;
import java.util.concurrent.Executor;

@Component
public class ExecutorProviderImpl implements ExecutorProvider {
    private final TaskManager taskManager;
    private final SecurityContext securityContext;

    @Inject
    public ExecutorProviderImpl(final TaskManager taskManager, final SecurityContext securityContext) {
        this.taskManager = taskManager;
        this.securityContext = securityContext;
    }

    @Override
    public Executor getExecutor() {
        return command -> {
            final String userId = securityContext.getUserId();
            final Task<?> parentTask = CurrentTaskState.currentTask();

            String taskName = "Generic Task";
            if (parentTask != null) {
                taskName = parentTask.getTaskName();
            }

            final GenericServerTask genericServerTask = GenericServerTask.create(parentTask, userId, taskName, null);
            genericServerTask.setRunnable(command);
            taskManager.execAsync(genericServerTask);
        };
    }

    @Override
    public Executor getExecutor(final ThreadPool threadPool) {
        return command -> {
            final String userId = securityContext.getUserId();
            final Task<?> parentTask = CurrentTaskState.currentTask();

            String taskName = threadPool.getName();
            if (parentTask != null) {
                taskName = parentTask.getTaskName();
            }

            final GenericServerTask genericServerTask = GenericServerTask.create(parentTask, userId, taskName, null);
            genericServerTask.setRunnable(command);
            taskManager.execAsync(genericServerTask, threadPool);
        };
    }
}
